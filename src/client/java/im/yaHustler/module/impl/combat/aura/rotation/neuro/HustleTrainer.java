package im.yaHustler.module.impl.combat.aura.rotation.neuro;

import im.yaHustler.util.chat.ChatUtil;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class HustleTrainer {
    private static final AtomicReference<String> job = new AtomicReference<>();
    private static final AtomicBoolean cancelled = new AtomicBoolean();
    private static volatile Process running;

    public static String runningStatus() {
        String current = job.get();
        return current == null ? null : (cancelled.get() ? "отмена · " : "") + current;
    }

    public static void message(String text) {
        Minecraft.getInstance().execute(() -> ChatUtil.sendMessage("[DataSet] " + text));
    }

    public static void train(String datasetName, int epochs) throws IOException {
        String name = datasetName.endsWith(".csv") ? datasetName.substring(0, datasetName.length() - 4) : datasetName;
        HustleRecorder.validName(name);
        if (NeuroRotation.getInstance().recorder().isRecording()) {
            throw new IOException("Сначала .dataset stop");
        }
        Path dataset = HustleModel.dataDirectory().resolve(name + ".csv").toAbsolutePath();
        if (!Files.isRegularFile(dataset)) {
            throw new IOException("Датасет не найден: " + dataset.getFileName());
        }
        start("обучается " + dataset.getFileName() + " (" + epochs + " эпох)", () -> {
            Runtime runtime = prepare();
            Path output = HustleModel.modelPath(name);
            Path temporary = output.resolveSibling(name + ".training.json");
            try {
                int code = run(runtime, List.of("-u", runtime.trainer.resolve("train_aura.py").toString(),
                        "--dataset", dataset.toString(), "--motion", MotionTrace.path(name).toAbsolutePath().toString(),
                        "--out", temporary.toString(), "--epochs", Integer.toString(epochs)));
                if (code != 0) throw new IOException("Тренер завершился с кодом " + code);
                HustleModel.validate(temporary);
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                Minecraft.getInstance().execute(() -> {
                    if (HustleModel.select(name)) {
                        NeuroRotation.getInstance().reset();
                        message("Загружена модель из датасета " + dataset.getFileName());
                    } else {
                        message("Не удалось загрузить " + name);
                    }
                });
            } finally {
                Files.deleteIfExists(temporary);
            }
        });
    }

    public static void setup() throws IOException {
        start("настройка Python и PyTorch", () -> {
            prepare();
            message("Python, NumPy и PyTorch готовы");
        });
    }

    public static void cancel() {
        cancelled.set(true);
        Process p = running;
        if (p != null) {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
        }
    }

    private interface Work {
        void run() throws Exception;
    }

    private static void start(String description, Work work) throws IOException {
        if (!job.compareAndSet(null, description)) {
            throw new IOException("Обучение или установка уже выполняется");
        }
        cancelled.set(false);
        Thread thread = new Thread(() -> {
            try {
                work.run();
            } catch (Exception e) {
                message("Ошибка: " + e.getMessage());
            } finally {
                running = null;
                job.set(null);
            }
        }, "Neuro-Trainer");
        thread.setDaemon(true);
        thread.start();
        message("Запущено в фоне");
    }

    private record Runtime(String python, Path dependencies, Path trainer) {}

    private static Runtime prepare() throws Exception {
        Path dir = HustleModel.directory();
        Files.createDirectories(dir);
        Properties config = new Properties();
        Path settings = dir.resolve("runtime.properties");
        if (Files.isRegularFile(settings)) {
            try (var reader = Files.newBufferedReader(settings)) {
                config.load(reader);
            }
        }
        String python = System.getenv("NEURO_PYTHON");
        if (python == null || python.isBlank()) python = System.getenv("NEUROTEST_PYTHON");
        if (python == null || python.isBlank()) python = config.getProperty("python");
        if (python == null || python.isBlank()) {
            Path bundled = Path.of(System.getProperty("user.home"), ".cache", "codex-runtimes", "codex-primary-runtime", "dependencies", "python", "python.exe");
            python = Files.isRegularFile(bundled) ? bundled.toString() : "python";
        }
        Path deps = Path.of(config.getProperty("dependencies", dir.resolve("python-deps").toString())).toAbsolutePath();
        Path trainer = dir.resolve("trainer");
        Files.createDirectories(trainer);
        for (String name : List.of("common.py", "train_aura.py")) {
            try (var in = HustleTrainer.class.getResourceAsStream("/assets/hustle263/neuro/trainer/" + name)) {
                if (in == null) throw new IOException("Нет ресурса " + name);
                Files.copy(in, trainer.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Runtime rt = new Runtime(python, deps, trainer);
        if (run(rt, List.of("-c", "import sys; assert sys.version_info >= (3,10); print(sys.version)")) != 0) {
            throw new IOException("Нужен Python 3.10+; укажите путь в " + settings);
        }
        if (run(rt, List.of("-c", "import numpy, torch; print('numpy',numpy.__version__,'torch',torch.__version__)")) != 0) {
            Files.createDirectories(deps);
            message("Устанавливаю NumPy и CPU PyTorch");
            if (run(rt, List.of("-m", "pip", "install", "--target", deps.toString(), "numpy")) != 0 ||
                    run(rt, List.of("-m", "pip", "install", "--target", deps.toString(), "torch", "--index-url", "https://download.pytorch.org/whl/cpu")) != 0 ||
                    run(rt, List.of("-c", "import numpy, torch; print(torch.__version__)")) != 0) {
                throw new IOException("Не удалось подготовить зависимости");
            }
        }
        return rt;
    }

    private static int run(Runtime rt, List<String> args) throws Exception {
        if (cancelled.get()) throw new IOException("Остановлено пользователем");
        String bootstrap = "import sys,runpy; sys.path.insert(0,sys.argv.pop(1)); sys.path.insert(0,sys.argv.pop(1)); " +
                "a=sys.argv[1:]; sys.argv=a[1:] if a[0]=='-m' else ['-c']+a[2:] if a[0]=='-c' else a; " +
                "runpy.run_module(a[1],run_name='__main__',alter_sys=True) if a[0]=='-m' else exec(a[1]) if a[0]=='-c' else runpy.run_path(a[0],run_name='__main__')";
        List<String> cleaned = new ArrayList<>(args);
        cleaned.remove("-u");
        List<String> command = new ArrayList<>(List.of(rt.python, "-u", "-c", bootstrap, rt.dependencies.toString(), rt.trainer.toString()));
        command.addAll(cleaned);
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONUTF8", "1");
        Process p = pb.start();
        running = p;
        try (var reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) message(line);
            }
        }
        int code = p.waitFor();
        if (cancelled.get()) throw new IOException("Остановлено пользователем");
        return code;
    }
}
