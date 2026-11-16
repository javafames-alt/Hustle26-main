package morfeov.coliseum.module.impl.movement;



import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.port.event.EventInput;
import morfeov.coliseum.port.event.EventDisconnect;
import morfeov.coliseum.port.event.EventOnMovePost;
import morfeov.coliseum.port.event.EventPacketReceive;
import morfeov.coliseum.port.event.EventPacketSend;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.port.PortModule;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.port.setting.BooleanSetting;
import morfeov.coliseum.port.setting.SelectSetting;
import morfeov.coliseum.port.setting.SliderSetting;
import morfeov.coliseum.port.MoveUtil;
import morfeov.coliseum.port.TimerSpeed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


@ModuleInfo(id = "speed", name = "Speed",
        description = "Ускоряет перемещение игрока различными режимами",
        category = Category.MOVEMENT
)
public final class Speed extends PortModule {

    private final SelectSetting mode = add(new SelectSetting(
            "Режим", "SvinNew",
            List.of("EntityBoost", "SvinNew", "Timer", "Elytra")
    ));
    private final BooleanSetting dive = add(new BooleanSetting(
            "Пикирование", false
    ).visibleWhen(() -> mode.getValue().equals("Elytra")));
    private final SliderSetting groundSpeed = add(new SliderSetting(
            "Скорость на земле", 0.84f, 0.1f, 5.0f, 0.02f
    ).visibleWhen(() -> mode.getValue().equals("SvinNew")));
    private final SliderSetting airSpeed = add(new SliderSetting(
            "Скорость в воздухе", 1.05f, 0.1f, 5.0f, 0.02f
    ).visibleWhen(() -> mode.getValue().equals("SvinNew")));
    private final SliderSetting speed3Boost = add(new SliderSetting(
            "Буст при Скорости 3", 1.45f, 1.0f, 2.5f, 0.05f, "x"
    ).visibleWhen(() -> mode.getValue().equals("SvinNew")));
    private final SliderSetting airSpeed3Boost = add(new SliderSetting(
            "Буст в воздухе (Скорость 3)", 1.25f, 1.0f, 2.5f, 0.05f, "x"
    ).visibleWhen(() -> mode.getValue().equals("SvinNew")));

    private int elytraLastSlot = -1;

    private static final float SVIN_NEW_BASE_SPEED = 0.84F;
    private static final float CHARGE_TIMER = 0.05F;
    private static final float BOOST_TIMER = 1.7F;
    private static final long CHARGE_DURATION_NANOS = 1_250_000_000L;
    private static final long MAX_BOOST_DURATION_NANOS = 2_400_000_000L;
    private static final int FULL_BOOST_JUMPS = 4;

    private final Map<Entity, Vec3> previousPositions = new HashMap<>();

    private enum TimerPhase { CHARGING, BOOSTING }
    private TimerPhase timerPhase = TimerPhase.CHARGING;
    private boolean timerCycleActive = false;
    private boolean timerAirborne = false;
    private int timerCompletedJumps = 0;
    private int timerGroundTicks = 0;
    private long timerPhaseStartedAt = 0L;
    private final Queue<ServerboundPongPacket> delayedTransactions = new ConcurrentLinkedQueue<>();

    public Speed() {
    }

    @Override
    public void onEnable() {
        previousPositions.clear();
        stopTimerCycle();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        previousPositions.clear();
        stopTimerCycle();
        TimerSpeed.reset();
        if (elytraLastSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(elytraLastSlot);
        }
        elytraLastSlot = -1;
        super.onDisable();
    }

    
    @Override
    public void tick(net.minecraft.client.Minecraft ignored) {
        if (mc.player == null || mc.level == null) return;

        String m = mode.getValue();
        if (timerCycleActive && !m.equals("Timer")) {
            stopTimerCycle();
        }
        if (m.equals("EntityBoost") || m.equals("Collision")) {
            tickEntityBoost();
        } else if (m.equals("SvinNew")) {
            tickSvinNew();
        } else if (m.equals("Timer")) {
            tickTimerMode();
        } else if (m.equals("Elytra")) {
            tickElytraSpeed();
        }
    }

    
    @Override
    public void onDisconnect(EventDisconnect event) {
        delayedTransactions.clear();
        elytraLastSlot = -1;
        stopTimerCycle();
        previousPositions.clear();
    }

    
    @Override
    public void onMovePost(EventOnMovePost event) {
        if (!enabled() || mc.player == null || mc.level == null) return;

        String m = mode.getValue();
        if (m.equals("SvinNew")) {
            tickSvinNew();
        } else if (m.equals("Elytra")) {
            if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                mc.player.stopFallFlying();
            }
        }
    }

    
    @Override
    public void onInput(EventInput event) {
        if (!enabled() || mc.player == null) return;

        String m = mode.getValue();
        if (m.equals("Timer")) {
            if (!isMoving()) {
                timerGroundTicks = 0;
                stopTimerCycle();
                return;
            }

            if (timerPhase == TimerPhase.BOOSTING) {
                timerGroundTicks = mc.player.verticalCollision ? timerGroundTicks + 1 : 0;
                event.setSprint(true);
                if (timerGroundTicks > 0) {
                    event.setJump(true);
                }
            }
        }
    }

    
    @Override
    public void onPacketReceive(EventPacketReceive event) {
        if (!enabled()) return;

        if (mode.getValue().equals("Timer")) {
            if (event.getPacket() instanceof ClientboundPlayerPositionPacket && timerCycleActive) {
                beginCharging();
            }
        }
    }

    
    @Override
    public void onPacketSend(EventPacketSend event) {
        if (!enabled()) return;

        if (mode.getValue().equals("Timer")) {
            if (morfeov.coliseum.port.PacketBufferManager.sendingSilent()) return;

            if (timerCycleActive && event.getPacket() instanceof ServerboundPongPacket packet) {
                delayedTransactions.add(packet);
                event.cancel();
            }
        }
    }

    private void tickTimerMode() {
        if (!isMoving()) {
            stopTimerCycle();
            return;
        }

        long now = System.nanoTime();

        if (!timerCycleActive) {
            timerCycleActive = true;
            beginCharging();
            return;
        }

        if (timerPhase == TimerPhase.BOOSTING) {
            if (mc.player.verticalCollision) {
                if (timerAirborne) {
                    timerAirborne = false;
                    timerCompletedJumps++;
                }
            } else {
                timerAirborne = true;
                if (timerCompletedJumps >= FULL_BOOST_JUMPS && mc.player.getDeltaMovement().y <= 0.0D) {
                    beginCharging();
                    return;
                }
            }
        }

        if (timerPhase == TimerPhase.CHARGING && now - timerPhaseStartedAt >= CHARGE_DURATION_NANOS) {
            beginBoosting();
        } else if (timerPhase == TimerPhase.BOOSTING && now - timerPhaseStartedAt >= MAX_BOOST_DURATION_NANOS) {
            beginCharging();
        }
    }

    private void beginCharging() {
        flushTransactions();
        timerPhase = TimerPhase.CHARGING;
        timerAirborne = false;
        timerCompletedJumps = 0;
        timerGroundTicks = 0;
        timerPhaseStartedAt = System.nanoTime();
        TimerSpeed.setSpeed(CHARGE_TIMER);
    }

    private void beginBoosting() {
        timerPhase = TimerPhase.BOOSTING;
        timerAirborne = false;
        timerCompletedJumps = 0;
        timerGroundTicks = 0;
        timerPhaseStartedAt = System.nanoTime();
        TimerSpeed.setSpeed(BOOST_TIMER);
    }

    private void stopTimerCycle() {
        flushTransactions();
        timerCycleActive = false;
        timerPhase = TimerPhase.CHARGING;
        timerAirborne = false;
        timerCompletedJumps = 0;
        timerGroundTicks = 0;
        timerPhaseStartedAt = 0L;
        TimerSpeed.reset();
    }

    private void flushTransactions() {
        ServerboundPongPacket packet;
        while ((packet = delayedTransactions.poll()) != null) {
            morfeov.coliseum.port.PacketBufferManager.sendSilentPacket(packet);
        }
    }



    private void tickSvinNew() {
        if (mc.player == null || !MoveUtil.isMoving()) return;

        MobEffectInstance speedEffect = mc.player.getEffect(MobEffects.SPEED);
        MobEffectInstance slowEffect = mc.player.getEffect(MobEffects.SLOWNESS);

        float appliedSpeed = groundSpeed.getValue();
        if (speedEffect != null) {
            int amplifier = speedEffect.getAmplifier();
            if (amplifier >= 2) {
                appliedSpeed *= Math.max(1.25F, speed3Boost.getValue());
            } else if (amplifier >= 1) {
                appliedSpeed *= 1.14F;
            } else {
                appliedSpeed *= 1.06F;
            }
        }

        if (slowEffect != null) {
            appliedSpeed *= 0.85F;
        }

        if (!mc.player.onGround()) {
            appliedSpeed = airSpeed.getValue();
            if (mc.player.getDeltaMovement().y > 0.0) {
                appliedSpeed *= 1.08F;
            } else {
                appliedSpeed *= 1.02F;
            }
            if (speedEffect != null) {
                int amplifier = speedEffect.getAmplifier();
                if (amplifier >= 2) {
                    appliedSpeed *= airSpeed3Boost.getValue();
                } else if (amplifier >= 1) {
                    appliedSpeed *= 1.10F;
                } else {
                    appliedSpeed *= 1.05F;
                }
            }
        }

        if (isOnSlabOrStairs()) {
            appliedSpeed *= 0.72F;
        }

        if (isOnAnvil()) {
            appliedSpeed *= 0.78F;
        }

        MoveUtil.setMotion(appliedSpeed);
    }

    private boolean isOnSlabOrStairs() {
        if (mc.player == null || mc.level == null) return false;
        BlockPos playerPos = mc.player.blockPosition();
        BlockState blockState = mc.level.getBlockState(playerPos);
        BlockState belowState = mc.level.getBlockState(playerPos.below());
        return blockState.getBlock() instanceof SlabBlock || blockState.getBlock() instanceof StairBlock
                || belowState.getBlock() instanceof SlabBlock || belowState.getBlock() instanceof StairBlock;
    }

    private boolean isOnAnvil() {
        if (mc.player == null || mc.level == null) return false;
        BlockPos playerPos = mc.player.blockPosition();
        BlockState blockState = mc.level.getBlockState(playerPos);
        BlockState belowState = mc.level.getBlockState(playerPos.below());
        return blockState.getBlock() instanceof AnvilBlock || belowState.getBlock() instanceof AnvilBlock;
    }

    private void tickEntityBoost() {
        previousPositions.keySet().removeIf(entity -> entity.isRemoved() || entity.level() != mc.level);
        double maxHorizontalOffset = 0.0;
        double maxVerticalOffset = 0.0;
        boolean collidedHorizontally = false;
        boolean collidedVertically = false;

        AABB playerBox = mc.player.getBoundingBox();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player && entity != mc.player) {
                AABB entityBox = entity.getBoundingBox();
                if (playerBox.intersects(entityBox)) {
                    Vec3 entityPos = new Vec3(entity.getX(), entity.getY(), entity.getZ());
                    Vec3 prevPos = previousPositions.getOrDefault(entity, entityPos);
                    Vec3 movement = entityPos.subtract(prevPos);

                    double horizontalOffset = Math.hypot(movement.x, movement.z);
                    double verticalOffset = movement.y;

                    if (horizontalOffset > maxHorizontalOffset) {
                        maxHorizontalOffset = horizontalOffset;
                    }
                    if (verticalOffset > maxVerticalOffset) {
                        maxVerticalOffset = verticalOffset;
                    }

                    collidedHorizontally = true;
                    if (verticalOffset > 0) {
                        collidedVertically = true;
                    }
                }
            }
            previousPositions.put(entity, new Vec3(entity.getX(), entity.getY(), entity.getZ()));
        }

        if (collidedHorizontally) {
            double currentSpeed = Math.hypot(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().z);
            double newSpeed = currentSpeed + maxHorizontalOffset * 1.5;
            MoveUtil.setMotion(newSpeed);
        }

        if (collidedVertically) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, maxVerticalOffset * 1.2, mc.player.getDeltaMovement().z);
        }
    }

    private void tickElytraSpeed() {
        if (mc.player == null || mc.level == null) return;

        int elytraSlot = findItemInHotbar(Items.ELYTRA);
        if (elytraSlot != -1) {
            if (dive.isValue()) {
                mc.player.setXRot(55.0f);
            }
            if (elytraLastSlot == -1) {
                elytraLastSlot = mc.player.getInventory().getSelectedSlot();
            }
            if (mc.player.getInventory().getSelectedSlot() != elytraSlot) {
                mc.player.getInventory().setSelectedSlot(elytraSlot);
                return;
            }
            if (mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;

            if (mc.getConnection() != null && mc.gameMode != null) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));

                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);

                mc.getConnection().send(new ServerboundPlayerCommandPacket(
                        mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                mc.player.startFallFlying();

                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            }
        }
    }

    private int findItemInHotbar(Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isMoving() {
        if (mc.player == null || mc.player.input == null) return false;
        Input input = mc.player.input.keyPresses;
        return input.forward() || input.backward() || input.left() || input.right();
    }
}
