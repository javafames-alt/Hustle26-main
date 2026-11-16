package morfeov.coliseum.util.player;
import net.minecraft.core.BlockPos; import net.minecraft.core.Direction; import net.minecraft.world.phys.*;
public final class RaycastUtil implements morfeov.coliseum.port.IMinecraft {
    public static boolean isLookingAtEntity(net.minecraft.world.entity.Entity entity,double reach) { var eye=mc.player.getEyePosition();return entity.getBoundingBox().contains(eye)||entity.getBoundingBox().clip(eye,eye.add(mc.player.getLookAngle().scale(reach))).isPresent(); }
    public static BlockHitResult raycastBlock(BlockPos pos,double reach) { var eye=mc.player.getEyePosition();var end=eye.add(mc.player.getLookAngle().scale(reach));return AABB.clip(java.util.List.of(new AABB(0,0,0,1,1,1)),eye,end,pos); }
}
