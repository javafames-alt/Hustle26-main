package morfeov.coliseum.module.impl.combat.aura.rotation.neuro;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.combat.aura.AttackAura;
import morfeov.coliseum.module.impl.combat.rotation.AttackContext;
import morfeov.coliseum.module.impl.combat.rotation.RotationComponent;
import morfeov.coliseum.port.SilentComponent;
import morfeov.coliseum.port.event.EventAttackSubmitted;
import morfeov.coliseum.util.chat.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public final class ColiseumRecorder {
    private static final String HEADER = "t,gcd,clean,yaw,pitch,dyaw,dpitch,has,tid,rx,ry,rz,bw,bh,dist,vis,on,atk,hp,ground,sprint\n";
    private final Minecraft mc = Minecraft.getInstance();
    private BufferedWriter writer;
    private long tick, rows, pitchChanges;
    private int targetId = -1, tail, flush;
    private long attackTick = -1;
    private boolean previous;
    private AttackSample attackSample;
    private float lastYaw, lastPitch;
    private ClientLevel world;

    private record AttackSample(LivingEntity target, AABB box, Vec3 eye, Vec3 view, float yaw, float pitch) {}

    public boolean isRecording() {
        return writer != null;
    }

    public static String validName(String name) {
        if (name == null || !name.matches("[\\p{L}\\p{N}_-]{1,64}")) {
            throw new IllegalArgumentException("Имя: 1–64 буквы, цифры, _ или -");
        }
        return name;
    }

    public void start(String name) throws IOException {
        validName(name);
        if (mc.player == null || mc.level == null) throw new IOException("Войдите в мир");
        if (isRecording()) throw new IOException("Запись уже идёт");
        Files.createDirectories(ColiseumModel.dataDirectory());
        Path file = ColiseumModel.dataDirectory().resolve(name + ".csv");
        boolean empty = !Files.exists(file) || Files.size(file) == 0;
        if (!empty) {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                long size = channel.size();
                ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(size, 8192));
                channel.read(buffer, size - buffer.capacity());
                byte[] bytes = buffer.array();
                boolean found = false;
                for (int i = bytes.length - 1; i >= 0; i--) {
                    if (bytes[i] == 10) {
                        channel.truncate(size - bytes.length + i + 1);
                        found = true;
                        break;
                    }
                }
                if (!found) throw new IOException("CSV без завершённой строки; выберите новое имя");
            }
            try (var lines = Files.lines(file)) {
                if (!lines.findFirst().orElse("").equals(HEADER.strip())) {
                    throw new IOException("Несовместимый CSV; выберите новое имя");
                }
            }
        }
        writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        if (empty) writer.write(HEADER);
        writer.flush();
        try {
            MotionTrace.record(name);
        } catch (IOException e) {
            stop();
            throw e;
        }
        tick = rows = pitchChanges = 0;
        tail = flush = 0;
        targetId = -1;
        attackTick = -1;
        previous = false;
        attackSample = null;
        world = mc.level;

        var aura = Coliseum.MODULES.get(AttackAura.class);
        if (aura != null && aura.enabled()) aura.toggle();
        SilentComponent.forceClear();
    }

    public String stop() {
        if (writer == null) return "Запись не идёт";
        String result = "Сохранено тиков: " + rows + " · с изменением pitch: " + pitchChanges;
        try {
            writer.close();
        } catch (IOException e) {
            result = "Ошибка сохранения CSV: " + e.getMessage();
        }
        writer = null;
        try {
            MotionTrace.stop();
        } catch (IOException e) {
            result += ". Ошибка сохранения движений: " + e.getMessage();
        }
        return result;
    }

    public void onAttackSubmitted(EventAttackSubmitted event) {
        if (isRecording() && mc.player != null && mc.level == world && !SilentComponent.isActive() && !AttackContext.isAuraAttack()
                && event.target() instanceof LivingEntity target && target != mc.player) {
            targetId = target.getId();
            attackTick = tick;
            attackSample = new AttackSample(target, target.getBoundingBox(), mc.player.getEyePosition(),
                    mc.player.getViewVector(1), mc.player.getYRot(), mc.player.getXRot());
        }
    }

    public void tick() {
        if (!isRecording()) return;
        if (mc.player == null || mc.level != world) {
            stop();
            return;
        }
        tick++;
        AttackSample hit = attackSample;
        attackSample = null;
        float yaw = hit != null ? hit.yaw() : mc.player.getYRot();
        float pitch = hit != null ? hit.pitch() : mc.player.getXRot();
        float dy = previous ? Mth.wrapDegrees(yaw - lastYaw) : 0;
        float dp = previous ? pitch - lastPitch : 0;
        previous = true;
        lastYaw = yaw;
        lastPitch = pitch;
        LivingEntity target = hit != null ? hit.target() : findTarget();
        if (target != null) {
            tail = 40;
        } else if (tail-- <= 0) {
            return;
        }
        AABB box = hit != null ? hit.box() : target == null ? null : target.getBoundingBox();
        Vec3 eye = hit != null ? hit.eye() : mc.player.getEyePosition();
        Vec3 view = hit != null ? hit.view() : mc.player.getViewVector(1);
        Vec3 delta = box == null ? Vec3.ZERO : box.getCenter().subtract(eye);
        boolean on = box != null && (box.contains(eye) || box.clip(eye, eye.add(view.scale(5))).isPresent());
        try {
            writer.write(String.format(Locale.ROOT, "%d,%.6f,%d,%.4f,%.4f,%.4f,%.4f,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%.1f,%d,%d%n",
                    tick, RotationComponent.getGCD(), hit != null || (!SilentComponent.isActive() && !AttackContext.isAuraAttack()) ? 1 : 0, yaw, pitch, dy, dp,
                    target == null ? 0 : 1, target == null ? -1 : target.getId(), delta.x, delta.y, delta.z,
                    box == null ? 0 : box.getXsize(), box == null ? 0 : box.getYsize(), box == null ? -1 : NeuroRotation.distanceToBox(eye, box),
                    target != null && visible(target) ? 1 : 0, on ? 1 : 0, hit != null ? 1 : 0,
                    target == null ? -1 : target.getHealth(),
                    mc.player.onGround() ? 1 : 0, mc.player.isSprinting() ? 1 : 0));
            rows++;
            if (Math.abs(dp) >= .0001f) pitchChanges++;
            if (++flush >= 256) {
                writer.flush();
                flush = 0;
            }
        } catch (IOException e) {
            stop();
            ChatUtil.sendMessage("[DataSet] Ошибка записи: " + e.getMessage());
        }
    }

    private LivingEntity findTarget() {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 view = mc.player.getViewVector(1);
        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity target && target != mc.player && target.isAlive()) {
            return target;
        }
        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (LivingEntity t : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(8),
                entity -> entity != mc.player && entity.isAlive())) {
                double d = NeuroRotation.distanceToBox(eye, t.getBoundingBox());
                if (d > 8) continue;
                double alignment = view.dot(t.getBoundingBox().getCenter().subtract(eye).normalize());
                double recentAttack = t.getId() == targetId && tick - attackTick <= 20 ? .04 : 0;
                double score = alignment - d * .025 + recentAttack;
                if (alignment > 0 && score > bestScore && visible(t)) {
                    best = t;
                    bestScore = score;
                }
        }
        return best;
    }

    private boolean visible(LivingEntity target) {
        AABB b = target.getBoundingBox();
        for (double x : new double[]{0.2, 0.5, 0.8}) {
            for (double y : new double[]{0.15, 0.4, 0.65, 0.9}) {
                for (double z : new double[]{0.2, 0.5, 0.8}) {
                    Vec3 point = new Vec3(Mth.lerp(x, b.minX, b.maxX), Mth.lerp(y, b.minY, b.maxY), Mth.lerp(z, b.minZ, b.maxZ));
                    if (mc.level.clip(new ClipContext(mc.player.getEyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static long totalTicks() throws IOException {
        if (!Files.isDirectory(ColiseumModel.dataDirectory())) return 0;
        long total = 0;
        try (var files = Files.list(ColiseumModel.dataDirectory())) {
            for (Path p : files.filter(p -> p.toString().endsWith(".csv")).toList()) {
                try (var lines = Files.lines(p)) {
                    total += lines.skip(1).filter(s -> s.split(",").length == 21).count();
                }
            }
        }
        return total;
    }
}
