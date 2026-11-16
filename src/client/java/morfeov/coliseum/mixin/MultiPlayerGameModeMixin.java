package morfeov.coliseum.mixin;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.misc.FakePlayer;
import morfeov.coliseum.punchy.PunchyBridge;
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
    private void coliseum$rwPvpFriends(int containerId,int slot,int button,net.minecraft.world.inventory.ContainerInput action,Player player,CallbackInfo ci) {
        var helper=Coliseum.MODULES.get(morfeov.coliseum.module.impl.misc.RWHelper.class);
        if(helper!=null&&helper.blocksPvp(slot)){ci.cancel();return;}
        var guiMove=Coliseum.MODULES.get(morfeov.coliseum.module.impl.movement.GuiMove.class);
        if(guiMove!=null&&guiMove.enabled()&&guiMove.deferClick(containerId,slot,button,action,player))ci.cancel();
    }
    @Inject(method="attack",at=@At("HEAD"),cancellable=true)
    private void coliseum$protectFriend(Player player,Entity entity,CallbackInfo ci) {
        if (morfeov.coliseum.friend.Friends.contains(entity)) { morfeov.coliseum.module.impl.combat.rotation.AttackContext.cancelCurrentAttack(); ci.cancel(); }
    }
    @Inject(method="startDestroyBlock", at=@At("HEAD"))
    private void coliseum$chooseTool(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        var tool=Coliseum.MODULES.get(morfeov.coliseum.module.impl.player.AutoTool.class);
        if(tool!=null && tool.enabled()) tool.start(pos);
    }

    @Inject(method="attack",at=@At("RETURN"))
    private void coliseum$combatAttack(Player player,Entity entity,CallbackInfo ci) {
        morfeov.coliseum.port.CombatHooks.attacked(entity);
    }
    @Inject(method = "attack", at = @At("HEAD"))
    private void coliseum$onAttack(Player player, Entity entity, CallbackInfo ci) {
        FakePlayer module = Coliseum.MODULES.get(FakePlayer.class);
        if (module != null && module.enabled()) module.onAttacked(entity);
        PunchyBridge.onEntityAttack(player);
    }
}
