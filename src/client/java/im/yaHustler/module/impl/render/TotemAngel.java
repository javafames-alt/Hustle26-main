package im.yaHustler.module.impl.render;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.Category;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.module.impl.themes.Theme;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;
@ModuleInfo(id="totem_angel",name="TotemAngel",category=Category.RENDER,description="Душа игрока поднимается после срабатывания тотема")
public final class TotemAngel extends Module {
 private final net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getInstance();
 private final NumberSetting rise=add(new NumberSetting("Скорость подъёма",1.4,.2,4,.1)),life=add(new NumberSetting("Длительность",3.8,.5,8,.1)),scale=add(new NumberSetting("Размер души",1.05,.5,2.5,.05));
 private record Soul(AvatarRenderer renderer,AvatarRenderState state,Vec3 position,float yaw,long born) {}
 private final List<Soul> souls=new ArrayList<>();
 private net.minecraft.client.multiplayer.ClientLevel world;
 @Override public void tick(net.minecraft.client.Minecraft client){if(world!=client.level){souls.clear();world=client.level;}}
 @SuppressWarnings({"rawtypes","unchecked"}) public void pop(Entity entity){
  if(!enabled()||!(entity instanceof Player player)||mc.player==null||player.distanceToSqr(mc.player)>14400)return;
  var renderer=mc.getEntityRenderDispatcher().getRenderer(player);
  if(!(renderer instanceof AvatarRenderer avatar))return;
  AvatarRenderState state=avatar.createRenderState();avatar.extractRenderState(player,state,0);
  if(souls.size()>=16)souls.removeFirst();souls.add(new Soul(avatar,state,player.position(),player.getYRot(),System.nanoTime()));
 }
 @Override protected void onDisable(){souls.clear();}
 @SuppressWarnings({"rawtypes","unchecked"}) public void submit(PoseStack pose,SubmitNodeCollector collector){
  if(mc.level==null){souls.clear();return;}long now=System.nanoTime();souls.removeIf(s->(now-s.born)/1e9>life.floatValue());
  Vec3 camera=mc.gameRenderer.mainCamera().position();
  for(Soul s:souls){float p=(now-s.born)/1e9f/life.floatValue(),fade=1-p;double y=(1-Math.pow(1-p,1.85))*(6+8*rise.floatValue());
   Vec3 pos=s.position.subtract(camera);pose.pushPose();pose.translate(pos.x+Math.sin(p*Math.PI*2.5)*.22*p,pos.y+y,pos.z);pose.rotateDegrees(Axis.YP,180-s.yaw);
   float size=scale.floatValue()*(1+p*.22f);pose.scale(-size,-size,size);pose.translate(0,-1.501,0);
   int tint=((int)(255*Math.pow(fade,1.15)*.45)<<24)|(Theme.accent()&0xffffff);
   collector.submitModel(s.renderer.getModel(),s.state,pose,RenderTypes.entityTranslucent(s.state.skin.body().texturePath()),0xf000f0,OverlayTexture.NO_OVERLAY,tint);pose.popPose();
  }
 }
}
