package im.yaHustler.mixin;

import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.misc.FakePlayer;
import im.yaHustler.punchy.PunchyBridge;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method="handleContainerInput",at=@At("HEAD"),cancellable=true)
    private void hustle263$rwPvpFriends(int containerId,int slot,int button,net.minecraft.world.inventory.ContainerInput action,Player player,CallbackInfo ci) {
        var helper=Hustle26.MODULES.get(im.yaHustler.module.impl.misc.RWHelper.class);
        if(helper!=null&&helper.blocksPvp(slot)){ci.cancel();return;}
        var guiMove=Hustle26.MODULES.get(im.yaHustler.module.impl.movement.GuiMove.class);
        if(guiMove!=null&&guiMove.enabled()&&guiMove.deferClick(containerId,slot,button,action,player))ci.cancel();
    }
    @Inject(method="attack",at=@At("HEAD"),cancellable=true)
    private void hustle263$protectFriend(Player player,Entity entity,CallbackInfo ci) {
        var mace = Hustle26.MODULES.get(im.yaHustler.module.impl.combat.AutoMace.class);
        var client = net.minecraft.client.Minecraft.getInstance();
        if (im.yaHustler.friend.Friends.contains(entity)
            || (player == client.player && mace != null && mace.enabled() && mace.cancelAttack(client, entity))) {
            im.yaHustler.module.impl.combat.rotation.AttackContext.cancelCurrentAttack();
            ci.cancel();
        }
    }
    @Inject(method="startDestroyBlock", at=@At("HEAD"))
    private void hustle263$chooseTool(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        var tool=Hustle26.MODULES.get(im.yaHustler.module.impl.player.AutoTool.class);
        if(tool!=null && tool.enabled()) tool.start(pos);
    }

    @Inject(method="attack",at=@At("RETURN"))
    private void hustle263$combatAttack(Player player,Entity entity,CallbackInfo ci) {
        im.yaHustler.port.CombatHooks.attacked(entity);
    }
    @Inject(method = "attack", at = @At("HEAD"))
    private void hustle263$onAttack(Player player, Entity entity, CallbackInfo ci) {
        FakePlayer module = Hustle26.MODULES.get(FakePlayer.class);
        if (module != null && module.enabled()) module.onAttacked(entity);
        PunchyBridge.onEntityAttack(player);
    }
}
