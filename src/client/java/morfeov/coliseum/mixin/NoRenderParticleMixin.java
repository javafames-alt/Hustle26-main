package morfeov.coliseum.mixin;
import morfeov.coliseum.module.impl.render.NoRender;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ParticleEngine.class) public abstract class NoRenderParticleMixin {
 @Inject(method="createParticle",at=@At("HEAD"),cancellable=true)
 private void coliseum$create(ParticleOptions options,double x,double y,double z,double dx,double dy,double dz,CallbackInfoReturnable<Particle> cir){if(NoRender.hideParticle(options))cir.setReturnValue(null);}
 @Inject(method="add(Lnet/minecraft/client/particle/Particle;)V",at=@At("HEAD"),cancellable=true)
 private void coliseum$add(Particle p,CallbackInfo ci){if(NoRender.noParticles()||NoRender.noBlockParticles()&&p instanceof TerrainParticle)ci.cancel();}
 @Inject(method="extract",at=@At("HEAD"),cancellable=true)
 private void coliseum$extract(CallbackInfo ci){if(NoRender.noParticles())ci.cancel();}
}
