package im.yaHustler.module.impl.combat.aura.rotation.neuro;

import im.yaHustler.module.impl.combat.rotation.RotationComponent;
import im.yaHustler.rotation.Look;
import im.yaHustler.util.chat.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class MotionTrace {
    private static final String HEADER = "time_ns,yaw,pitch";
    private static BufferedWriter writer;
    private static ClientLevel world;
    private static long origin, lastTime, points;
    private static double yaw, pitch;
    static final Point ZERO = new Point(0, 0, 0);

    private MotionTrace() {}

    public record Point(long time, float yaw, float pitch) {}

    static Path path(String name) {
        return HustleModel.directory().resolve("motion").resolve(HustleRecorder.validName(name) + ".csv");
    }

    static void record(String name) throws IOException {
        if (writer != null) throw new IOException("Сначала остановите запись движения");
        var mc = Minecraft.getInstance();
        if (mc.level == null) throw new IOException("Войдите в мир");
        Path file = path(name);
        Files.createDirectories(file.getParent());
        boolean empty = !Files.exists(file) || Files.size(file) == 0;
        var output = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        try {
            if (empty) output.write(HEADER + "\n");
            output.write("0,0,0\n");
            output.flush();
        } catch (IOException e) {
            try { output.close(); } catch (IOException closeError) { e.addSuppressed(closeError); }
            throw e;
        }
        writer = output;
        world = mc.level;
        origin = lastTime = points = 0;
        yaw = pitch = 0;
    }

    public static void capture(double dx, double dy) {
        if (writer == null || (dx == 0 && dy == 0)) return;
        var mc = Minecraft.getInstance();
        if (mc.level != world || mc.player == null || mc.gui.screen() != null || Look.active()
                || mc.options.smoothCamera || mc.player.isScoping()) return;
        long now = System.nanoTime();
        if (points == 0) origin = now;
        long time = Math.max(now - origin, lastTime + 1);
        float scale = RotationComponent.getGCD();
        yaw += dx * scale * (mc.options.invertMouseX().get() ? -1 : 1);
        pitch += dy * scale * (mc.options.invertMouseY().get() ? -1 : 1);
        try {
            writer.write(time + "," + yaw + "," + pitch + "\n");
            lastTime = time;
            if (++points % 128 == 0) writer.flush();
        } catch (IOException e) {
            try { stop(); } catch (IOException closeError) { e.addSuppressed(closeError); }
            ChatUtil.sendMessage("[DataSet] Ошибка записи движения: " + e.getMessage());
        }
    }

    static void stop() throws IOException {
        if (writer == null) return;
        BufferedWriter current = writer;
        writer = null;
        world = null;
        current.close();
    }

    public static Point cameraOffset() {
        return NeuroRotation.getInstance().cameraMotion();
    }

    static Point sample(Point[] points, long elapsed) {
        if (points.length < 2) return ZERO;
        long phase = elapsed % points[points.length - 1].time();
        int low = 0, high = points.length - 1;
        while (high - low > 1) {
            int mid = (low + high) >>> 1;
            if (points[mid].time() <= phase) low = mid;
            else high = mid;
        }
        Point a = points[low], b = points[high];
        float fraction = (float) ((double) (phase - a.time()) / (b.time() - a.time()));
        return new Point(phase, a.yaw() + fraction * (b.yaw() - a.yaw()),
                a.pitch() + fraction * (b.pitch() - a.pitch()));
    }

}
