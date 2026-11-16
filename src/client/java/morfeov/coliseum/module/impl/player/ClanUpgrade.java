package morfeov.coliseum.module.impl.player;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(id="clan_upgrade", name="ClanUpgrade", category=Category.PLAYER,
    description="Устанавливает и ломает редстоун под игроком")
public final class ClanUpgrade extends Module {
    private float pitch, yaw;
    private boolean rotated, warned;

    @Override public void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!mc.player.getMainHandItem().is(Items.REDSTONE)) {
            if (!warned) mc.gui.chatListener().handleSystemMessage(Component.literal("Возьмите редстоун в руку!"), false);
            warned = true;
            return;
        }
        warned = false;
        if (!rotated) { pitch = mc.player.getXRot(); yaw = mc.player.getYRot(); rotated = true; }
        mc.player.setXRot(90.0f);
        mc.player.setYRot(0.0f);
        var pos = mc.player.blockPosition();
        var state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            var below = pos.below();
            if (mc.level.getBlockState(below).isSolid()) {
                var hit = new BlockHitResult(new Vec3(below.getX()+0.5, below.getY()+1.0, below.getZ()+0.5), Direction.UP, below, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
            }
        } else if (state.is(Blocks.REDSTONE_WIRE)) {
            mc.gameMode.startDestroyBlock(pos, Direction.UP);
            mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        }
    }

    @Override protected void onEnable() { rotated = false; warned = false; }
    @Override protected void onDisable() {
        var player = Minecraft.getInstance().player;
        if (rotated && player != null) { player.setXRot(pitch); player.setYRot(yaw); }
        rotated = false;
    }
}
