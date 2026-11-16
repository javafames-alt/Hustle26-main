package im.yaHustler.mixin;
import im.yaHustler.module.impl.render.NoRender;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ParticleEngine.class) public abstract class NoRenderParticleMixin {
 @Inject(method="createParticle",at=@At("HEAD"),cancellable=true)
 private void hustle263$create(ParticleOptions options,double x,double y,double z,double dx,double dy,double dz,CallbackInfoReturnable<Particle> cir){if(NoRender.hideParticle(options))cir.setReturnValue(null);}
 @Inject(method="add(Lnet/minecraft/client/particle/Particle;)V",at=@At("HEAD"),cancellable=true)
 private void hustle263$add(Particle p,CallbackInfo ci){if(NoRender.noParticles()||NoRender.noBlockParticles()&&p instanceof TerrainParticle)ci.cancel();}
 @Inject(method="extract",at=@At("HEAD"),cancellable=true)
 private void hustle263$extract(CallbackInfo ci){if(NoRender.noParticles())ci.cancel();}
}
