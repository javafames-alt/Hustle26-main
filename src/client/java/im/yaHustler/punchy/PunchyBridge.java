package im.yaHustler.punchy;

import com.mojang.blaze3d.vertex.PoseStack;
import im.yaHustler.module.impl.render.SwingAnimations;
import im.yaHustler.module.impl.render.ViewModel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class PunchyBridge {
    private static final System.Logger LOGGER = System.getLogger("hustle263-punchy");
    private static final int[] HAND_FRAME_DEPTH = new int[2];
    private static final int[] ARM_FRAME_DEPTH = new int[2];
    private static final Matrix4f ROOT = new Matrix4f(), ROOT_INVERSE = new Matrix4f();
    private static final Matrix3f ROOT_NORMAL = new Matrix3f(), ROOT_NORMAL_INVERSE = new Matrix3f();
    private static boolean rootCaptured;

    private static boolean initialized;
    private static boolean available;
    private static boolean clientStarted;
    private static volatile boolean syncRequested = true;
    private static final int CHECK_INTERVAL = 20;
    private static int checkTimer;
    private static boolean lastDesired;

    private static Method snapshot;
    private static Method applyAndSave;
    private static Method applyRuntime;
    private static Method isModEnabled;
    private static Field enableMod;
    private static Method recordAttack;
    private static boolean recordAttackResolved;

    private PunchyBridge() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        available = FabricLoader.getInstance().isModLoaded("punchy");
        if (!available) {
            LOGGER.log(System.Logger.Level.WARNING, "Bundled Punchy was not loaded");
            return;
        }
        resolveDonorHooks();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            clientStarted = true;
            requestSync();
            tick(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> clientStarted = false);
        ClientTickEvents.END_CLIENT_TICK.register(PunchyBridge::tick);
    }

    public static void requestSync() { syncRequested = true; }

    public static boolean isActive() {
        return available && clientStarted && SwingAnimations.wantsPunchy();
    }

    public static void onEntityAttack(Player player) {
        if (!isActive() || player == null || player != Minecraft.getInstance().player) return;
        if (!recordAttackResolved) {
            recordAttackResolved = true;
            try {
                recordAttack = Class.forName("punchy.client.state.AttackActionTracker", false,
                    PunchyBridge.class.getClassLoader()).getMethod("recordAttack", InteractionHand.class);
            } catch (ReflectiveOperationException | LinkageError exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Punchy recordAttack is unavailable", exception);
            }
        }
        if (recordAttack == null) return;
        try {
            recordAttack.invoke(null, InteractionHand.MAIN_HAND);
        } catch (ReflectiveOperationException | LinkageError exception) {
            recordAttack = null;
        }
    }

    public static void captureRoot(PoseStack poseStack) {
        if (poseStack == null) return;
        PoseStack.Pose last = poseStack.last();
        ROOT.set(last.pose());
        ROOT.invert(ROOT_INVERSE);
        ROOT_NORMAL.set(last.normal());
        ROOT_NORMAL.invert(ROOT_NORMAL_INVERSE);
        rootCaptured = true;
    }

    public static void releaseRoot() { rootCaptured = false; }

    private static void applyViewModel(HumanoidArm arm, PoseStack poseStack) {
        if (rootCaptured) ViewModel.applyView(arm, poseStack, ROOT, ROOT_INVERSE, ROOT_NORMAL, ROOT_NORMAL_INVERSE);
    }

    public static void itemFramePush(LocalPlayer player, HumanoidArm arm, PoseStack poseStack) {
        if (!isActive() || player == null || arm == null || poseStack == null) return;
        poseStack.pushPose();
        HAND_FRAME_DEPTH[arm.ordinal()]++;
        applyViewModel(arm, poseStack);
        InteractionHand hand = player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        SwingAnimations config = SwingAnimations.getInstance();
        if (config != null) config.applyPunchyItemTranslate(poseStack, player.getItemInHand(hand));
    }

    public static void armFramePush(HumanoidArm arm, PoseStack poseStack) {
        if (!isActive() || arm == null || poseStack == null) return;
        poseStack.pushPose();
        ARM_FRAME_DEPTH[arm.ordinal()]++;
        applyViewModel(arm, poseStack);
    }

    public static void armFramePop(HumanoidArm arm, PoseStack poseStack) {
        if (arm == null || poseStack == null) return;
        int index = arm.ordinal();
        if (ARM_FRAME_DEPTH[index] <= 0) return;
        ARM_FRAME_DEPTH[index]--;
        poseStack.popPose();
    }

    public static void handFramePop(LocalPlayer player, HumanoidArm arm, PoseStack poseStack) {
        if (arm == null || poseStack == null) return;
        int index = arm.ordinal();
        if (HAND_FRAME_DEPTH[index] <= 0) return;
        HAND_FRAME_DEPTH[index]--;
        poseStack.popPose();
    }

    public static void applyItemScale(LocalPlayer player, HumanoidArm arm, PoseStack poseStack) {
        if (!isActive() || player == null || arm == null || poseStack == null) return;
        SwingAnimations config = SwingAnimations.getInstance();
        InteractionHand hand = player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (config != null) config.applyPunchyItemScale(poseStack, player.getItemInHand(hand));
    }

    private static void tick(Minecraft client) {
        if (!clientStarted || !available) return;
        boolean desired = SwingAnimations.wantsPunchy();
        if (!syncRequested && desired == lastDesired && --checkTimer > 0) return;
        lastDesired = desired;
        checkTimer = CHECK_INTERVAL;
        Boolean current = readEnabled();
        if (current != null && current.booleanValue() == desired) {
            syncRequested = false;
            return;
        }
        if (current != null) {
            apply(desired);
            return;
        }
        if (syncRequested) {
            syncRequested = false;
            apply(desired);
        }
    }

    private static void resolveDonorHooks() {
        ClassLoader loader = PunchyBridge.class.getClassLoader();
        try {
            Class<?> config = Class.forName("punchy.config.PunchyConfig", false, loader);
            Class<?> data = Class.forName("punchy.config.PunchyConfig$Data", false, loader);
            snapshot = config.getMethod("snapshot");
            isModEnabled = config.getMethod("isModEnabled");
            enableMod = data.getField("enableMod");
            try {
                applyAndSave = config.getMethod("applyAndSave", data);
            } catch (NoSuchMethodException exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Punchy applyAndSave is missing, falling back to applyRuntime");
            }
            try {
                applyRuntime = config.getMethod("applyRuntime", data);
            } catch (NoSuchMethodException ignored) {}
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Punchy config hooks are unavailable", exception);
        }
    }

    private static Boolean readEnabled() {
        Method reader = isModEnabled;
        if (reader == null) return null;
        try {
            return (Boolean) reader.invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            isModEnabled = null;
            LOGGER.log(System.Logger.Level.WARNING, "Punchy isModEnabled could not be read", exception);
            return null;
        }
    }

    private static void apply(boolean enabled) {
        Method snap = snapshot;
        Field flag = enableMod;
        if (snap == null || flag == null) return;
        try {
            Object data = snap.invoke(null);
            flag.setBoolean(data, enabled);
            Method writer = applyAndSave != null ? applyAndSave : applyRuntime;
            if (writer != null) writer.invoke(null, data);
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Punchy could not switch its enableMod flag", exception);
        }
    }
}
