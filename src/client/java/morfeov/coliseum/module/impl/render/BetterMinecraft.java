package morfeov.coliseum.module.impl.render;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.setting.*;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
@ModuleInfo(id="better_minecraft",name="BetterMinecraft",category=Category.RENDER,description="Плавное приближение и улучшенная камера")
public final class BetterMinecraft extends Module {
 public final BooleanSetting zoom=add(new BooleanSetting("Zoom",true)),clip=add(new BooleanSetting("Camera Clip",true)),smooth=add(new BooleanSetting("Smooth Camera (F5)",true)),motion=add(new BooleanSetting("Motion Zoom Out",true));
 public final NumberSetting smoothSpeed=add(new NumberSetting("F5 Smooth Speed",12,2,30,1)),motionDistance=add(new NumberSetting("Motion Distance",1.5,.3,3.5,.1));
 private final ModeSetting key=add(new ModeSetting("Zoom Key","C","C","V","X","Z","Left Alt","Left Ctrl","Mouse 4","Mouse 5"));
 private final NumberSetting fov=add(new NumberSetting("Zoom Fov",30,5,70,1)),speed=add(new NumberSetting("Zoom Speed",22,5,40,1));
 public final BooleanSetting chatAnimation=add(new BooleanSetting("Chat Animation",true)),containerAnimation=add(new BooleanSetting("Container Animation",true));
 /** Мокрый пол переехал в Weather → «Дождь». */
 public void close(){}
 public final BooleanSetting chunkAnimator=add(new BooleanSetting("Chunk Animator",true));
 private final NumberSetting chunkSpeed=add(new NumberSetting("Chunk Speed",10,1,20,1));
 private final ModeSetting chunkMode=add(new ModeSetting("Chunk Animation","Below","Below","Above"));
 private final java.util.Map<Long,Long> chunks=new java.util.HashMap<>();
 private net.minecraft.client.multiplayer.ClientLevel chunkWorld;
 public net.minecraft.core.BlockPos chunkOrigin(net.minecraft.core.BlockPos origin){var mc=Minecraft.getInstance();if(!enabled()||!chunkAnimator.enabled())return origin;if(chunkWorld!=mc.level){chunks.clear();chunkWorld=mc.level;}long now=System.nanoTime(),key=origin.asLong();Long start=chunks.get(key);if(start==null){chunks.put(key,now);start=now;}if(start==0)return origin;float t=(now-start)/(1_000_000_000f*2/chunkSpeed.floatValue());if(t>=1){chunks.put(key,0L);return origin;}float offset=36*(float)Math.pow(1-t,3)*(chunkMode.is("Below")?-1:1);return origin.offset(0,Math.round(offset),0);}
 private float progress;private long last;
 public float fov(float vanilla){long now=System.nanoTime();float dt=last==0?0:Math.min(.1f,(now-last)/1e9f);last=now;Minecraft mc=Minecraft.getInstance();
 int code=switch(key.get()){case "V"->86;case "X"->88;case "Z"->90;case "Left Alt"->342;case "Left Ctrl"->341;default->67;};
 boolean down=enabled()&&zoom.enabled()&&mc.gui.screen()==null&&(key.get().startsWith("Mouse")?morfeov.coliseum.util.ActionMouseButtons.down(key.is("Mouse 4")?3:4):InputConstants.isKeyDown(code));
 progress+=( (down?1:0)-progress)*(1-(float)Math.exp(-speed.floatValue()*dt));return vanilla+(Math.min(vanilla,fov.floatValue())-vanilla)*progress;
 }
 @Override protected void onDisable(){progress=0;last=0;chunks.clear();chunkWorld=null;}
}
