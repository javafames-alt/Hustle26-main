package im.yaHustler.module.impl.combat;

import java.util.Comparator;
import java.util.ArrayList;
import im.yaHustler.module.Category;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.port.PortModule;
import im.yaHustler.port.CombatHooks;
import im.yaHustler.port.event.EventDisconnect;
import im.yaHustler.rotation.Rotation;
import im.yaHustler.rotation.Rotations;
import im.yaHustler.setting.*;
import im.yaHustler.util.InventoryActions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(id="target_pearl",name="TargetPearl",category=Category.COMBAT,
    description="Бросает жемчуг к рассчитанному месту приземления чужого жемчуга")
public final class TargetPearl extends PortModule {
    public final ModeSetting mode=add(new ModeSetting("Режим","По кнопке","По кнопке","Всегда"));
    public final KeySetting throwKey=add(new KeySetting("Бросить").visibleWhen(()->mode.is("По кнопке")));
    public final ModeSetting targets=add(new ModeSetting("Цель","Цель Aura","Цель Aura","Все"));
    public final NumberSetting distance=add(new NumberSetting("Минимальная дистанция",6,3,15,1));
    public final ModeSetting version=add(new ModeSetting("Версия броска","1.17.1","1.17.1","1.21.4"));
    private long lastThrow;
    private Solution pending;
    private int aimingTicks;

    @Override public void tick(Minecraft client) {
        if (client.player==null || client.level==null) { reset(); return; }
        if (client.gui.screen()!=null || client.player.isUsingItem() || System.nanoTime()-lastThrow<1_000_000_000L
            || (mode.is("По кнопке") && !throwKey.down()) || !InventoryActions.ready(client)
            || client.player.getCooldowns().isOnCooldown(Items.ENDER_PEARL.getDefaultInstance())) { reset(); return; }
        if (!client.player.getOffhandItem().is(Items.ENDER_PEARL) && InventoryActions.find(client,Items.ENDER_PEARL)<0) { reset(); return; }
        var pearls=new ArrayList<ThrownEnderpearl>();
        for (var entity:client.level.entitiesForRendering()) if (entity instanceof ThrownEnderpearl pearl) {
            if (pearl.getOwner()==null) client.level.players().stream()
                .filter(p->p!=client.player && p.distanceTo(pearl)<=3)
                .min(Comparator.comparingDouble(p->p.distanceToSqr(pearl))).ifPresent(pearl::setOwner);
            if (pearl.getOwner()==client.player) { reset(); return; }
            if (pearl.getOwner()==null || client.player.isAlliedTo(pearl.getOwner())) continue;
            var target=CombatHooks.target();
            if (targets.is("Цель Aura") && target!=null && target!=pearl.getOwner()) continue;
            pearls.add(pearl);
        }
        if (version.is("1.17.1") && pending!=null) {
            if (++aimingTicks>8) { reset(); lastThrow=System.nanoTime(); return; }
            Vec3 landing=trace(origin(),launch(client.player.getYRot(),client.player.getXRot()));
            if (landing!=null && landing.distanceTo(pending.landing())<=2) { fire(client); return; }
        }
        Solution best=null;
        double bestAngle=Double.MAX_VALUE;
        for (var pearl:pearls) {
            Vec3 landing=trace(pearl.position(),pearl.getDeltaMovement(),pearl);
            if (landing==null || landing.distanceTo(client.player.position())<distance.get()) continue;
            Solution candidate=solve(landing);
            if (candidate==null) continue;
            double angle=Math.hypot(Mth.wrapDegrees(candidate.yaw()-client.player.getYRot()),candidate.pitch()-client.player.getXRot());
            if (angle<bestAngle) { best=candidate; bestAngle=angle; }
        }
        if (best==null) { reset(); return; }
        pending=best;
        Rotations.aim(new Rotation(best.yaw(),best.pitch()),360,0,100);
        if (version.is("1.21.4")) {
            Vec3 hit=trace(origin(),launch(client.player.getYRot(),client.player.getXRot()));
            if(hit!=null&&hit.distanceTo(best.landing())<=2)fire(client);
        }
    }
    private void fire(Minecraft client) {
        if (InventoryActions.use(client,Items.ENDER_PEARL,false)) lastThrow=System.nanoTime();
        reset();
    }
    private Vec3 origin() { return new Vec3(mc.player.getX(),mc.player.getEyeY()-0.1,mc.player.getZ()); }
    private Vec3 launch(float yaw,float pitch) {
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        Vec3 movement=mc.player.getDeltaMovement();
        return new Vec3(-Math.sin(y)*Math.cos(p)*1.5,-Math.sin(p)*1.5,Math.cos(y)*Math.cos(p)*1.5)
            .add(movement.x,mc.player.onGround()?0:movement.y,movement.z);
    }
    private Vec3 trace(Vec3 position,Vec3 velocity) {
        return trace(position,velocity,null);
    }
    private Vec3 trace(Vec3 position,Vec3 velocity,ThrownEnderpearl pearl) {
        for (int tick=0;tick<(pearl==null?200:300);tick++) {
            Vec3 next=position.add(velocity);
            var hit=mc.level.clip(new ClipContext(position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            if (hit.getType()!=HitResult.Type.MISS) return hit.getLocation();
            if(pearl!=null&&!mc.level.getEntities(pearl,new net.minecraft.world.phys.AABB(position,next).inflate(0.3),
                entity->entity!=pearl.getOwner()&&entity!=mc.player&&entity instanceof net.minecraft.world.entity.LivingEntity&&entity.isAlive()).isEmpty())return next;
            double drag=mc.level.getFluidState(BlockPos.containing(position)).is(FluidTags.WATER)?0.8:0.99;
            velocity=velocity.scale(drag).add(0,-0.03,0);
            position=next;
            if (position.y<mc.level.getMinY()-64) return null;
        }
        return null;
    }
    private double error(float yaw,float pitch,Vec3 target) {
        Vec3 hit=trace(origin(),launch(yaw,pitch));
        return hit==null?Double.MAX_VALUE:hit.distanceTo(target);
    }
    private Solution solve(Vec3 target) {
        Vec3 delta=target.subtract(origin());
        float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90;
        float coarse=Float.NaN;
        double previous=Double.MAX_VALUE,current=error(yaw,89,target);
        for (int i=0;i<179;i++) {
            double next=i==178?Double.MAX_VALUE:error(yaw,88-i,target);
            if (current<8 && current<=previous && current<=next) { coarse=89-i; break; }
            previous=current; current=next;
        }
        if (Float.isNaN(coarse)) return null;
        Solution best=null;
        double bestError=Double.MAX_VALUE;
        for (int pi=-10;pi<=10;pi++) for (int yi=-3;yi<=3;yi++) {
            float pitch=Mth.clamp(coarse+pi*0.1f,-90,90),angle=yaw+yi*0.5f;
            double value=error(angle,pitch,target);
            if (value<bestError) { bestError=value; best=new Solution(target,angle,pitch); }
        }
        return bestError<=2?best:null;
    }
    private void reset() { pending=null; aimingTicks=0; }
    @Override protected void onDisable() { if (pending!=null) Rotations.release(); reset(); }
    @Override public void onDisconnect(EventDisconnect event) { reset(); lastThrow=0; }
    private record Solution(Vec3 landing,float yaw,float pitch) {}
}
