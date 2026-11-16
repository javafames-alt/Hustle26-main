package morfeov.coliseum.mixin;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.Ambience;
import morfeov.coliseum.render.coliseimvisual.VisualColors;
import morfeov.coliseum.render.coliseimvisual.FogSettingsEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FogRenderer.class)
public abstract class AmbienceFogMixin {
    @Inject(method="setupFog", at=@At("RETURN"))
    private void coliseum$ambienceFog(Camera camera, int distance, DeltaTracker delta, float darken, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        var weather = Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.Weather.class);
        if (weather != null && weather.active() && weather.hasFog()) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null || camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE
                || player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)) return;
            weather.applyFog(cir.getReturnValue());
            return;
        }
        Ambience module = Coliseum.MODULES.get(Ambience.class);
        if (module == null || !module.enabled()) return;
        FogData fog = cir.getReturnValue();
        FogSettingsEvent event = new FogSettingsEvent(camera, Math.max(16, fog.renderDistanceEnd));
        module.modifyFog(event);
        if (event.hasColorOverride()) { fog.color.set(VisualColors.toRgbaVector(event.color())); if(fog.color.w == 0) fog.color.w = 1; }
        if (event.hasDistanceOverride()) {
            fog.environmentalStart = fog.environmentalEnd = 1e7f;
            fog.renderDistanceStart = event.startDistance(); fog.renderDistanceEnd = event.endDistance();
            fog.skyEnd = fog.cloudEnd = event.endDistance();
        }
    }
}
