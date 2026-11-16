package morfeov.coliseum.module.impl.combat;

import morfeov.coliseum.port.InvActionUtil;
import morfeov.coliseum.rotation.Rotation;
import morfeov.coliseum.rotation.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Shared visible-face placement and short inventory leases for the imported combat modules. */
final class CombatPlacement {
    private CombatPlacement() {}
    static boolean use(Minecraft mc, String owner, Item item, BlockHitResult hit, double range) {
        Vec3 eye = mc.player.getEyePosition();
        if (eye.distanceTo(hit.getLocation()) > range) return false;
        BlockHitResult visible = mc.level.clip(new ClipContext(eye, hit.getLocation(),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (visible.getType() != HitResult.Type.MISS && !visible.getBlockPos().equals(hit.getBlockPos())) return false;
        int slot = -1;
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getItem(i).is(item)) { slot = i; break; }
        if (slot < 0) return false;
        if (!InvActionUtil.get().tryAcquire(owner, 50, 100)) return false;
        int previous = mc.player.getInventory().getSelectedSlot();
        try {
            if (!owner.equals("AutoWebTrap")) aim(mc, hit.getLocation());
            mc.player.getInventory().setSelectedSlot(slot);
            ((morfeov.coliseum.mixin.CombatGameModeAccessor) mc.gameMode).coliseum$syncSelectedSlot();
            var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
            return result.consumesAction();
        } finally {
            mc.player.getInventory().setSelectedSlot(previous);
            ((morfeov.coliseum.mixin.CombatGameModeAccessor) mc.gameMode).coliseum$syncSelectedSlot();
            InvActionUtil.get().release(owner);
        }
    }
    static BlockHitResult support(Minecraft mc, BlockPos position) {
        BlockHitResult best=null;
        double distance=Double.MAX_VALUE;
        for (Direction face : Direction.values()) {
            BlockPos neighbor = position.relative(face.getOpposite());
            var state = mc.level.getBlockState(neighbor);
            if (!state.isAir() && !state.getCollisionShape(mc.level, neighbor).isEmpty()) {
                Vec3 point = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(.5));
                var trace=mc.level.clip(new ClipContext(mc.player.getEyePosition(),point.subtract(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(.002)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
                double next=mc.player.getEyePosition().distanceToSqr(point);
                if(trace.getType()==HitResult.Type.BLOCK && trace.getBlockPos().equals(neighbor) && trace.getDirection()==face && next<distance) {best=new BlockHitResult(point,face,neighbor,false);distance=next;}
            }
        }
        return best;
    }
    static void aim(Minecraft mc, Vec3 point) {
        Vec3 delta = point.subtract(mc.player.getEyePosition());
        Rotations.aim(new Rotation((float)Math.toDegrees(Math.atan2(delta.z, delta.x))-90,
            (float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)))), 180, 0, 50);
    }
}
