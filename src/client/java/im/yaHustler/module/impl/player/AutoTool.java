package im.yaHustler.module.impl.player;
import im.yaHustler.module.*;
import im.yaHustler.port.PortModule;
import im.yaHustler.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

@ModuleInfo(id="auto_tool", name="AutoTool", category=Category.PLAYER, description="Выбирает подходящий инструмент для добычи")
public final class AutoTool extends PortModule {
    private final ModeSetting mode=add(new ModeSetting("Режим", "Пакетный", "Легитный", "Пакетный"));
    private int previous=-1;
    public void start(BlockPos pos) {
        if (mc.player==null || mc.level==null || mc.gameMode==null) return;
        BlockState state=mc.level.getBlockState(pos); int best=-1; float speed=1;
        for(int i=0;i<9;i++) { var stack=mc.player.getInventory().getItem(i); float candidate=stack.getDestroySpeed(state); if(candidate>speed) { speed=candidate; best=i; } }
        if (best<0 || best==mc.player.getInventory().getSelectedSlot()) return;
        if(previous<0) previous=mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(best);
        if(mode.is("Пакетный")) ((im.yaHustler.mixin.CombatGameModeAccessor)mc.gameMode).hustle263$syncSelectedSlot();
    }
    public void restore() { if(previous>=0 && mc.player!=null && mc.gameMode!=null) { mc.player.getInventory().setSelectedSlot(previous); if(mode.is("Пакетный")) ((im.yaHustler.mixin.CombatGameModeAccessor)mc.gameMode).hustle263$syncSelectedSlot(); } previous=-1; }
    @Override public void tick(Minecraft client) { if(previous>=0 && !im.yaHustler.util.ActionMouseButtons.down(0)) restore(); }
    @Override protected void onDisable() { restore(); }
    @Override public void onDisconnect(im.yaHustler.port.event.EventDisconnect e) { previous=-1; }
}
