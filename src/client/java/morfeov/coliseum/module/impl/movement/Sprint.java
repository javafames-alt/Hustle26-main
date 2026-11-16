package morfeov.coliseum.module.impl.movement;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.module.impl.combat.aura.AttackAura;
import morfeov.coliseum.module.impl.combat.aura.sprint.SprintResetHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;

@ModuleInfo(id = "sprint", name = "Sprint", category = Category.MOVEMENT, description = "Автоматический спринт")
public final class Sprint extends Module {
    private boolean canSprint = true;
    private static int holdTicks;

    public static Sprint get() {
        return Coliseum.MODULES.get(Sprint.class);
    }

    public static void setCanSprint(boolean canSprint) {
        Sprint sprint = get();
        if (sprint != null) {
            sprint.canSprint = canSprint;
        }
    }

    public boolean isCanSprint() {
        return this.canSprint;
    }

    public static void holdSprint(int ticks) {
        holdTicks = Math.max(holdTicks, Math.max(0, ticks));
    }

    public static boolean shouldBlockVanillaSprint() {
        AttackAura aura = Coliseum.MODULES.get(AttackAura.class);
        return holdTicks > 0 || (aura != null && aura.shouldPauseAutoSprint());
    }

    public static void tickSprintBlock() {
        if (holdTicks > 0) holdTicks--;
    }

    public static void resetSprintBlock() {
        holdTicks = 0;
    }

    public void onDisconnect() {
        this.canSprint = true;
        holdTicks = 0;
        SprintResetHelper.resetConnectionState();
    }

    @Override
    public void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.options == null) {
            return;
        }

        if (holdTicks > 0) {
            stopSprint(client, player);
            return;
        }

        AttackAura aura = Coliseum.MODULES.get(AttackAura.class);
        if (aura != null && aura.shouldPauseAutoSprint()) {
            client.options.keySprint.setDown(false);
            return;
        }

        if (!this.canSprint) {
            stopSprint(client, player);
            return;
        }

        boolean sneaking = player.isShiftKeyDown() && !player.isSwimming();
        if (sneaking) {
            client.options.keySprint.setDown(false);
            return;
        }

        client.options.keySprint.setDown(true);
    }

    private void stopSprint(Minecraft client, LocalPlayer player) {
        if (player != null && player.isSprinting()) {
            player.setSprinting(false);
        }
        if (client.options != null) {
            client.options.keySprint.setDown(false);
        }
    }

    public static boolean isNearPartialStep(LocalPlayer player) {
        if (player == null || player.level() == null) return false;

        BlockPos feet = player.blockPosition();
        double yaw = Math.toRadians(player.getYRot());
        double frontX = -Math.sin(yaw);
        double frontZ = Math.cos(yaw);
        BlockPos front = BlockPos.containing(player.getX() + frontX, player.getY(), player.getZ() + frontZ);

        return isSlabOrStairs(player, feet)
                || isSlabOrStairs(player, feet.below())
                || isSlabOrStairs(player, front)
                || isSlabOrStairs(player, front.below());
    }

    private static boolean isSlabOrStairs(LocalPlayer player, BlockPos pos) {
        var world = player.level();
        var state = world.getBlockState(pos);
        var block = state.getBlock();
        if (block instanceof SlabBlock || block instanceof StairBlock) return true;
        if (state.isAir() || state.canBeReplaced()) return false;
        var collisionShape = state.getCollisionShape(world, pos);
        return !collisionShape.isEmpty() && !state.isSolidRender();
    }

    @Override
    protected void onDisable() {
        this.canSprint = true;
        stopSprint(Minecraft.getInstance(), Minecraft.getInstance().player);
    }
}
