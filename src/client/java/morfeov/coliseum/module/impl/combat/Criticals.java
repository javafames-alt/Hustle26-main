package morfeov.coliseum.module.impl.combat;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.module.impl.combat.aura.AttackAura;
import morfeov.coliseum.module.impl.combat.aura.CriticalUtils;
import morfeov.coliseum.port.PortModule;
import morfeov.coliseum.port.event.EventDisconnect;
import morfeov.coliseum.setting.ModeSetting;
import morfeov.coliseum.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;

@ModuleInfo(id="criticals",name="Criticals",category=Category.COMBAT,description="Reallyworld: поддержка критов с эффектом медленного падения")
public final class Criticals extends PortModule {
    public final ModeSetting mode=add(new ModeSetting("Режим","Reallyworld","Reallyworld"));
    private final BooleanSetting autoJump=add(new BooleanSetting("Автопрыжок",true));
    private boolean spoofSentThisJump, criticalJumpActive;
    private long lastAttackMillis;
    public static Criticals getEnabled(){var module=Coliseum.MODULES.get(Criticals.class);return module!=null&&module.enabled()?module:null;}
    public static boolean isActive(){return getEnabled()!=null&&mc.player!=null&&mc.player.hasEffect(MobEffects.SLOW_FALLING);}
    public static boolean readyForNaturalCritical(LocalPlayer player){return player!=null&&!player.onGround()&&player.fallDistance>0&&player.getDeltaMovement().y<0&&player.hasEffect(MobEffects.SLOW_FALLING);}
    public static boolean allowsAuraAttack(LocalPlayer player){return readyForNaturalCritical(player)&&canUseCriticalEnvironment(player)&&!player.hasEffect(MobEffects.BLINDNESS);}
    private static boolean canUseCriticalEnvironment(LocalPlayer player){return !player.isInWater()&&!player.isInLava()&&!player.isFallFlying()&&!player.getAbilities().flying&&!player.onClimbable()&&!player.isPassenger()&&!player.hasEffect(MobEffects.LEVITATION)&&!CriticalUtils.isInCobweb(player);}
    public static boolean cooldownReady(LocalPlayer player,long last){return System.currentTimeMillis()-last>=450&&player.getAttackStrengthScale(.5f)>.9f;}
    public static boolean allowsAttack(LocalPlayer player,boolean onlyCrits,boolean smart){
        if(!onlyCrits)return true;
        boolean environment=!player.hasEffect(MobEffects.LEVITATION)&&!player.hasEffect(MobEffects.BLINDNESS)&&!player.isEyeInFluid(FluidTags.WATER)&&!player.isEyeInFluid(FluidTags.LAVA)&&!player.getAbilities().flying&&!player.isFallFlying()&&!player.onClimbable()&&!player.isPassenger();
        return environment&&!player.onGround()?player.fallDistance>0&&player.getDeltaMovement().y<0:smart&&player.onGround()&&!mc.options.keyJump.isDown();
    }
    public static boolean startJumpIfNeeded(LocalPlayer player,AttackAura aura){
        var module=getEnabled();
        if(module==null||!module.autoJump.enabled()||!isActive()||player==null||aura==null||!aura.enabled()||!aura.isOnlyCrits()||aura.getCurrentTarget()==null||!player.onGround()||!cooldownReady(player,module.lastAttackMillis)||player.distanceToSqr(aura.getCurrentTarget())>11.2225||!canUseCriticalEnvironment(player)||!player.level().noCollision(player,player.getBoundingBox().expandTowards(0,1.25,0)))return false;
        player.jumpFromGround();module.criticalJumpActive=true;return true;
    }
    public void beforeMotion(){
        var player=mc.player;
        if(player==null||mc.level==null||!player.hasEffect(MobEffects.SLOW_FALLING)||player.onGround()){spoofSentThisJump=false;criticalJumpActive=false;return;}
        var aura=Coliseum.MODULES.get(AttackAura.class);
        if(!spoofSentThisJump&&aura!=null&&aura.enabled()&&aura.getCurrentTarget()!=null&&readyForNaturalCritical(player)&&player.getAttackStrengthScale(.5f)>.9f&&player.distanceToSqr(aura.getCurrentTarget())<=11.2225&&canUseCriticalEnvironment(player)){
            player.connection.send(new ServerboundMovePlayerPacket.PosRot(player.getX(),player.getY()-1e-10,player.getZ(),player.getYRot(),player.getXRot(),false,player.horizontalCollision));spoofSentThisJump=true;
        }
    }
    @Override public void tick(Minecraft client){if(client.player==null||!client.player.hasEffect(MobEffects.SLOW_FALLING)||client.player.onGround()){spoofSentThisJump=false;criticalJumpActive=false;}}
    @Override public void onAttackSubmitted(morfeov.coliseum.port.event.EventAttackSubmitted event){lastAttackMillis=System.currentTimeMillis();}
    public static boolean suppressViewBobbing(){var module=getEnabled();return module!=null&&module.criticalJumpActive&&isActive();}
    @Override protected void onDisable(){spoofSentThisJump=false;criticalJumpActive=false;lastAttackMillis=0;}
    @Override public void onDisconnect(EventDisconnect event){onDisable();}
}
