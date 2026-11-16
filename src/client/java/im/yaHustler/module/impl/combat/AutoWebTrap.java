package im.yaHustler.module.impl.combat;

import java.util.*;

import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.util.InventoryActions;
import im.yaHustler.module.impl.combat.crystal.CrystalDamageCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Hustle 26.3 web interception adapted to the local inventory and collision APIs. */
@ModuleInfo(id="auto_web_trap", name="AutoWebTrap", category=Category.COMBAT,
    description="Ставит паутину на пути цели с предсказанием движения")
public final class AutoWebTrap extends im.yaHustler.port.PortModule {
    private final NumberSetting range = add(new NumberSetting("Радиус цели", 6, 2, 8, .5));
    private final NumberSetting prediction = add(new NumberSetting("Предикт тиков", 2, 0, 4, 1));
    private final NumberSetting delay = add(new NumberSetting("Задержка тиков", 2, 1, 10, 1));
    private final Map<BlockPos, Integer> retryAfter = new HashMap<>();
    private Player target;
    private Object level;
    private int nextPlacement;
    private BlockPos pending;
    private net.minecraft.world.phys.BlockHitResult pendingHit;
    private int aimedAt;
    public Player target() { return enabled() ? target : null; }
    private void releaseAim() {pending=null;pendingHit=null;if(im.yaHustler.rotation.Rotations.owns(this))im.yaHustler.rotation.Rotations.release();}
    @Override protected void onDisable() {releaseAim();target=null; level=null; retryAfter.clear(); nextPlacement=0; }
    @Override public void tick(Minecraft mc) {
        if (mc.level != level) { onDisable(); level=mc.level; }
        if (mc.level==null || !InventoryActions.ready(mc) || mc.player.isSpectator() || mc.gui.screen()!=null || mc.player.isUsingItem()) {releaseAim();target=null; return; }
        if(pending!=null) {
            if(mc.player.tickCount-aimedAt>12 || !mc.level.getBlockState(pending).canBeReplaced())releaseAim();
            else {rotateToHit();return;}
        }
        target=mc.level.players().stream().filter(p -> p != mc.player && !AntiBot.rejects(p) && !im.yaHustler.friend.Friends.contains(p) && !mc.player.isAlliedTo(p) && p.isAlive() && !p.isSpectator() && !p.isCreative() && mc.player.distanceToSqr(p) <= range.floatValue()*range.floatValue())
            .min(Comparator.comparingDouble(mc.player::distanceToSqr)).orElse(null);
        if(target==null || mc.player.tickCount<nextPlacement) return;
        retryAfter.entrySet().removeIf(e -> e.getValue()<=mc.player.tickCount);
        var box=CrystalDamageCalculator.predictBox(target,prediction.intValue());
        Set<BlockPos> footprint=new LinkedHashSet<>();
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),
                BlockPos.containing(box.maxX,box.minY+.5,box.maxZ))) footprint.add(pos.immutable());
        Set<BlockPos> expanded=new LinkedHashSet<>(footprint);
        for(BlockPos pos:footprint) { expanded.add(pos.north()); expanded.add(pos.south()); expanded.add(pos.east()); expanded.add(pos.west()); }
        List<BlockPos> candidates=new ArrayList<>(expanded);
        candidates.sort(Comparator.<BlockPos>comparingInt(pos -> footprint.contains(pos)?0:1).thenComparingDouble(pos -> mc.player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))));
        for(BlockPos pos:candidates) {
            if(retryAfter.containsKey(pos) || mc.level.getBlockState(pos).is(Blocks.COBWEB)
                || !mc.level.getBlockState(pos).canBeReplaced() || mc.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos))) continue;
            var hit=CombatPlacement.support(mc,pos);
            if(hit==null) continue;
            if(mc.player.getEyePosition().distanceToSqr(hit.getLocation())>Math.pow(Math.min(4.5,mc.player.blockInteractionRange()),2))continue;
            boolean hasWeb=false;for(int i=0;i<9;i++)if(mc.player.getInventory().getItem(i).is(Items.COBWEB))hasWeb=true;
            if(!hasWeb)return;
            pending=pos;pendingHit=hit;aimedAt=mc.player.tickCount;rotateToHit();break;
        }
    }
    private void rotateToHit() {
        var delta=pendingHit.getLocation().subtract(mc.player.getEyePosition());
        im.yaHustler.rotation.Rotations.aim(this,new im.yaHustler.rotation.Rotation((float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90,(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)))),35,0,90);
    }
    @Override public void onPostMotion(im.yaHustler.port.event.EventPostMotion event) {
        if(pending==null || mc.player==null || mc.level==null || !im.yaHustler.rotation.Rotations.owns(this) || mc.player.tickCount<=aimedAt)return;
        var eye=mc.player.getEyePosition();
        var trace=mc.level.clip(new net.minecraft.world.level.ClipContext(eye,eye.add(mc.player.getLookAngle().scale(Math.min(4.5,mc.player.blockInteractionRange()))),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        if(trace.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK || !trace.getBlockPos().equals(pendingHit.getBlockPos()) || trace.getDirection()!=pendingHit.getDirection())return;
        if(target==null || !target.isAlive() || !mc.level.getBlockState(pending).canBeReplaced() || mc.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pending))) {releaseAim();return;}
        boolean sent=CombatPlacement.use(mc,"AutoWebTrap",Items.COBWEB,trace,Math.min(4.5,mc.player.blockInteractionRange()));
        retryAfter.put(pending,mc.player.tickCount+(sent?12:20));nextPlacement=mc.player.tickCount+Math.max(3,delay.intValue());releaseAim();
    }
}
