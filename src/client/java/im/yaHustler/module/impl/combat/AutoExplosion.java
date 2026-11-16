package im.yaHustler.module.impl.combat;

import im.yaHustler.Hustle26;
import im.yaHustler.module.*;
import im.yaHustler.module.Module;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.rotation.*;
import im.yaHustler.util.InventoryActions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Obsidian -> crystal -> explosion, with a bounded lifetime and guaranteed hotbar restoration. */
@ModuleInfo(id="auto_explosion",name="AutoExplosion",description="Ставит и взрывает кристалл после установки обсидиана",category=Category.COMBAT)
public final class AutoExplosion extends Module {
    public final BooleanSetting protectDrops=add(new BooleanSetting("Не взрывать ресурсы",false));
    public final BooleanSetting matrix=add(new BooleanSetting("Обход Matrix",false));
    private BlockPos target;
    private long started;
    private int previousSlot=-1,selectedTick;
    private boolean placed;
    private net.minecraft.world.entity.player.Inventory inventory;
    private net.minecraft.client.multiplayer.ClientLevel level;
    public static void blockPlaced(BlockPos pos){
        var m=Hustle26.MODULES.get(AutoExplosion.class);Minecraft mc=Minecraft.getInstance();
        if(m==null||!m.enabled()||mc.level==null||!mc.level.getBlockState(pos).is(Blocks.OBSIDIAN))return;
        if(m.target!=null)return;
        m.reset();m.target=pos.immutable();m.started=mc.level.getGameTime();m.level=mc.level;
    }
    @Override protected void onDisable(){reset();}
    private void reset(){
        if(previousSlot>=0 && inventory!=null && Minecraft.getInstance().player!=null
                && Minecraft.getInstance().player.getInventory()==inventory){
            inventory.setSelectedSlot(previousSlot);
            Minecraft.getInstance().player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(previousSlot));
        }
        target=null;previousSlot=-1;inventory=null;level=null;placed=false;
        im.yaHustler.port.InvActionUtil.get().release("AutoExplosion");
    }
    @Override public void tick(Minecraft mc){
        if(target==null)return;
        if(mc.level==null||mc.level!=level||!InventoryActions.ready(mc)||mc.level.getGameTime()-started>(matrix.enabled()?14:6)
                ||!mc.level.getBlockState(target).is(Blocks.OBSIDIAN)){reset();return;}
        if(!im.yaHustler.port.InvActionUtil.get().tryAcquire("AutoExplosion",50,250))return;
        Vec3 center=Vec3.atBottomCenterOf(target.above());
        if(protectDrops.enabled()&&!mc.level.getEntitiesOfClass(ItemEntity.class,new AABB(center,center).inflate(3)).isEmpty())return;
        var crystals=mc.level.getEntitiesOfClass(EndCrystal.class,new AABB(target.above()).inflate(.25));
        if(!crystals.isEmpty()){
            var crystal=crystals.getFirst();Vec3 aim=crystal.position().add(0,.5,0);
            rotate(mc,aim);
            var eye=mc.player.getEyePosition();
            if(eye.distanceTo(aim)<=3 && (!matrix.enabled() || mc.player.hasLineOfSight(crystal))
                    && (crystal.getBoundingBox().contains(eye) || crystal.getBoundingBox().clip(eye,eye.add(mc.player.getLookAngle().scale(3))).isPresent())){
                mc.gameMode.attack(mc.player,crystal);mc.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);reset();
            }
            return;
        }
        if(placed)return;
        Vec3 aim=Vec3.atCenterOf(target).add(0,.5,0);
        if(mc.player.getEyePosition().distanceTo(aim)>4.5){reset();return;}
        rotate(mc,aim);
        Vec3 eye=mc.player.getEyePosition(),end=eye.add(mc.player.getLookAngle().scale(4.5));
        BlockHitResult hit=mc.level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
        if(!hit.getBlockPos().equals(target))return;
        if(previousSlot<0){
            int crystal=-1;for(int i=0;i<9;i++)if(mc.player.getInventory().getItem(i).is(Items.END_CRYSTAL)){crystal=i;break;}
            if(crystal<0){reset();return;}
            inventory=mc.player.getInventory();previousSlot=inventory.getSelectedSlot();inventory.setSelectedSlot(crystal);
            selectedTick=mc.player.tickCount;return;
        }
        if(mc.player.tickCount<=selectedTick)return;
        var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);
        if(result.consumesAction()){placed=true;mc.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);}
    }
    private void rotate(Minecraft mc,Vec3 point){
        Vec3 delta=point.subtract(mc.player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90;
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
        Rotations.aim(new Rotation(yaw,pitch),matrix.enabled()?90:360,0,60);
    }
}
