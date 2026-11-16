package im.yaHustler.util.math;
public final class RotationUtil implements im.yaHustler.port.IMinecraft {
    public static Angle calculate(net.minecraft.world.phys.Vec3 target) { var d=target.subtract(mc.player.getEyePosition());return new Angle((float)Math.toDegrees(Math.atan2(d.z,d.x))-90,(float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance()))); }
}
