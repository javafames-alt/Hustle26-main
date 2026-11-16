package morfeov.coliseum.module.impl.combat.aura.rotation.neuro;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ColiseumModel {
    public static final int FEATURES = 17;
    public static final String DEFAULT_NAME = "default";
    private static ColiseumModel activeModel;
    private static boolean loaded;
    private static String selectedName;
    private final float[][] inputWeights;
    private final float[][] hiddenWeights;
    private final float[][] outputWeights;
    private final float[] inputBias;
    private final float[] hiddenBias;
    private final float[] outputBias;
    private final float[] errorQuantiles;
    private final float[] aim;
    private final MotionTrace.Point[] motion;
    private final int hiddenSize;
    private final int mixtures;
    private final int freezeLimit;
    private final float yawLimit;
    private final float pitchLimit;

    private ColiseumModel(JsonObject jsonObject) {
        this.hiddenSize = jsonObject.get("hidden").getAsInt();
        this.mixtures = jsonObject.get("mix").getAsInt();
        this.freezeLimit = jsonObject.has("freezeCut") ? jsonObject.get("freezeCut").getAsInt() : 12;
        JsonArray jsonArray = jsonObject.getAsJsonArray("limits");
        this.yawLimit = jsonArray.get(0).getAsFloat();
        this.pitchLimit = jsonArray.get(1).getAsFloat();
        this.errorQuantiles = jsonObject.has("error") ? vector(jsonObject.getAsJsonArray("error")) : new float[]{};
        this.aim = jsonObject.has("aim") ? vector(jsonObject.getAsJsonArray("aim")) : null;
        JsonArray motionPoints = jsonObject.getAsJsonArray("motion");
        this.motion = new MotionTrace.Point[motionPoints == null ? 0 : motionPoints.size()];
        for (int i = 0; i < motion.length; i++) {
            JsonArray point = motionPoints.get(i).getAsJsonArray();
            motion[i] = new MotionTrace.Point(point.get(0).getAsLong(), point.get(1).getAsFloat(), point.get(2).getAsFloat());
        }
        JsonObject gru = jsonObject.getAsJsonObject("gru");
        this.inputWeights = matrix(gru.getAsJsonArray("wi"));
        this.hiddenWeights = matrix(gru.getAsJsonArray("wh"));
        this.inputBias = vector(gru.getAsJsonArray("bi"));
        this.hiddenBias = vector(gru.getAsJsonArray("bh"));
        JsonObject out = jsonObject.getAsJsonObject("out");
        this.outputWeights = matrix(out.getAsJsonArray("w"));
        this.outputBias = vector(out.getAsJsonArray("b"));
    }

    private static float[][] matrix(JsonArray jsonArray) {
        float[][] result = new float[jsonArray.size()][];
        for (int i = 0; i < jsonArray.size(); ++i) {
            result[i] = vector(jsonArray.get(i).getAsJsonArray());
        }
        return result;
    }

    private static float[] vector(JsonArray jsonArray) {
        float[] result = new float[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); ++i) {
            result[i] = jsonArray.get(i).getAsFloat();
        }
        return result;
    }

    public static synchronized Path directory() {
        Path parent = Minecraft.getInstance().gameDirectory.toPath().resolve("Coliseum");
        Path current = parent.resolve("neuro");
        Path previous = parent.resolve("neurotest");
        if (!Files.exists(current) && Files.isDirectory(previous)) {
            try {
                Files.move(previous, current);
            } catch (IOException e) {
                // Keep recordings available if the old directory cannot be renamed.
                if (!Files.isDirectory(current)) return previous;
            }
        }
        return current;
    }

    public static Path dataDirectory() {
        return directory().resolve("data");
    }

    public static Path modelPath(String name) {
        return directory().resolve(name + ".json");
    }

    public static String activeName() {
        if (selectedName == null) {
            try {
                Path path = directory().resolve("active.txt");
                selectedName = Files.isRegularFile(path) ? Files.readString(path).trim() : DEFAULT_NAME;
            } catch (Exception exception) {
                selectedName = DEFAULT_NAME;
            }
            if (selectedName.isEmpty()) {
                selectedName = DEFAULT_NAME;
            }
        }
        return selectedName;
    }

    public static List<String> listModels() {
        List<String> list = new ArrayList<>();
        list.add(DEFAULT_NAME);
        try (Stream<Path> stream = Files.list(directory())) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .filter(name -> !list.contains(name))
                    .sorted()
                    .forEach(list::add);
        } catch (Exception ignored) {}
        return list;
    }

    public static boolean exists(String name) {
        return DEFAULT_NAME.equals(name) || Files.isRegularFile(modelPath(name));
    }

    public static boolean select(String name) {
        ColiseumModel candidate = readModel(name);
        if (candidate == null) return false;
        selectedName = name;
        try {
            Files.createDirectories(directory());
            Files.writeString(directory().resolve("active.txt"), name);
        } catch (Exception ignored) {}
        activeModel = candidate;
        loaded = true;
        return true;
    }

    public static void validate(Path path) throws IOException {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (json.get("features").getAsInt() != 17 || !"deg".equals(json.get("units").getAsString())) {
                throw new IllegalArgumentException("Формат модели");
            }
            new ColiseumModel(json).checkDimensions();
        } catch (RuntimeException e) {
            throw new IOException("Некорректная модель", e);
        }
    }

    private void checkDimensions() {
        if (hiddenSize < 1 || hiddenSize > 4096 || mixtures < 1 || mixtures > 128 || freezeLimit < 1 || yawLimit <= 0 || pitchLimit <= 0) {
            throw new IllegalArgumentException("Размеры модели");
        }
        checkMatrix(inputWeights, 3 * hiddenSize, 17);
        checkMatrix(hiddenWeights, 3 * hiddenSize, hiddenSize);
        checkMatrix(outputWeights, 1 + 6 * mixtures, hiddenSize);
        checkVector(inputBias, 3 * hiddenSize);
        checkVector(hiddenBias, 3 * hiddenSize);
        checkVector(outputBias, 1 + 6 * mixtures);
        checkVector(errorQuantiles, errorQuantiles.length);
        long lastTime = -1;
        for (MotionTrace.Point point : motion) {
            if (point.time() <= lastTime || !Float.isFinite(point.yaw()) || !Float.isFinite(point.pitch())
                    || Math.abs(point.yaw()) > 0.35f || Math.abs(point.pitch()) > 0.35f) {
                throw new IllegalArgumentException("Некорректная траектория движения");
            }
            lastTime = point.time();
        }
        if (motion.length > 0 && motion[0].time() != 0) throw new IllegalArgumentException("Некорректное начало траектории");
        if (aim != null) {
            checkVector(aim, 2);
            if (Math.abs(aim[0]) > 0.9f || Math.abs(aim[1]) > 0.9f) {
                throw new IllegalArgumentException("Точка вне хитбокса");
            }
        }
    }

    private static void checkMatrix(float[][] matrix, int rows, int cols) {
        if (matrix.length != rows) throw new IllegalArgumentException("Строки весов");
        for (float[] row : matrix) checkVector(row, cols);
    }

    private static void checkVector(float[] vector, int length) {
        if (vector.length != length) throw new IllegalArgumentException("Размер весов");
        for (float v : vector) {
            if (!Float.isFinite(v)) throw new IllegalArgumentException("Нечисловой вес");
        }
    }

    public static ColiseumModel active() {
        if (!loaded) {
            loaded = true;
            activeModel = readModel(activeName());
        }
        return activeModel;
    }

    public static void reload() {
        loaded = false;
        activeModel = null;
    }

    private static ColiseumModel readModel(String name) {
        try {
            JsonObject jsonObject;
            Path path = modelPath(name);
            if (Files.isRegularFile(path)) {
                jsonObject = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            } else {
                if (!DEFAULT_NAME.equals(name)) return null;
                try (InputStream inputStream = ColiseumModel.class.getClassLoader().getResourceAsStream("assets/coliseum/neuro/default.json")) {
                    if (inputStream == null) return null;
                    jsonObject = JsonParser.parseReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
            if (jsonObject.get("features").getAsInt() != 17 || !jsonObject.has("mix") || !jsonObject.has("units")) return null;
            if ("deg".equals(jsonObject.get("units").getAsString())) {
                ColiseumModel model = new ColiseumModel(jsonObject);
                model.checkDimensions();
                return model;
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public int freezeCut() {
        return this.freezeLimit;
    }

    public boolean hasAim() { return aim != null; }
    MotionTrace.Point motionAt(long elapsed) { return MotionTrace.sample(motion, elapsed); }
    public float aimSide() { return aim[0]; }
    public float aimHeight() { return aim[1]; }

    public float[] newState() {
        return new float[this.hiddenSize];
    }

    public float[] forward(float[] input, float[] state) {
        float[] inBuf = new float[3 * this.hiddenSize];
        float[] hidBuf = new float[3 * this.hiddenSize];
        for (int n = 0; n < 3 * this.hiddenSize; ++n) {
            float f = this.inputBias[n];
            float[] inW = this.inputWeights[n];
            for (int n2 = 0; n2 < inW.length; ++n2) f += inW[n2] * input[n2];
            inBuf[n] = f;
            f = this.hiddenBias[n];
            float[] hidW = this.hiddenWeights[n];
            for (int n2 = 0; n2 < hidW.length; ++n2) f += hidW[n2] * state[n2];
            hidBuf[n] = f;
        }
        for (int n = 0; n < this.hiddenSize; ++n) {
            float r = sigmoid(inBuf[n] + hidBuf[n]);
            float z = sigmoid(inBuf[this.hiddenSize + n] + hidBuf[this.hiddenSize + n]);
            float h = (float) Math.tanh(inBuf[2 * this.hiddenSize + n] + r * hidBuf[2 * this.hiddenSize + n]);
            state[n] = (1.0f - z) * h + z * state[n];
        }
        float[] output = new float[this.outputWeights.length];
        for (int i = 0; i < this.outputWeights.length; ++i) {
            float f4 = this.outputBias[i];
            float[] outW = this.outputWeights[i];
            for (int j = 0; j < outW.length; ++j) f4 += outW[j] * state[j];
            output[i] = f4;
        }
        return output;
    }

    public int sampleMixture(float[] mixture, float rand) {
        float maxVal = mixture[1];
        for (int i = 1; i < this.mixtures; ++i) maxVal = Math.max(maxVal, mixture[1 + 6 * i]);
        float sum = 0.0f;
        for (int i = 0; i < this.mixtures; ++i) sum += (float) Math.exp(mixture[1 + 6 * i] - maxVal);
        float threshold = rand * sum;
        for (int i = 0; i < this.mixtures; ++i) {
            threshold -= (float) Math.exp(mixture[1 + 6 * i] - maxVal);
            if (threshold <= 0.0f) return i;
        }
        return this.mixtures - 1;
    }

    public float sampleError(float quantile) {
        if (this.errorQuantiles.length == 0) return 0.0f;
        float idx = Math.max(0.0f, Math.min(1.0f, quantile)) * (float) (this.errorQuantiles.length - 1);
        int n = (int) idx;
        return n >= this.errorQuantiles.length - 1 ? this.errorQuantiles[this.errorQuantiles.length - 1]
                : this.errorQuantiles[n] + (this.errorQuantiles[n + 1] - this.errorQuantiles[n]) * (idx - (float) n);
    }

    public float sampleDelta(float mu, float logSigma, boolean isYaw, float standardGaussian, float temperature) {
        float sigma = (float) Math.exp(Math.max(-4.0f, Math.min(1.5f, logSigma)));
        float val = Math.max(-8.0f, Math.min(8.0f, mu + temperature * sigma * standardGaussian));
        float limit = isYaw ? this.yawLimit : this.pitchLimit;
        return Math.max(-limit, Math.min(limit, (float) Math.sinh(val)));
    }

    public static float freezeProbability(float f) {
        return sigmoid(f);
    }

    public static float correlation(float f) {
        return (float) Math.tanh(f) * 0.95f;
    }

    private static float sigmoid(float f) {
        return 1.0f / (1.0f + (float) Math.exp(-f));
    }
}
