package morfeov.coliseum.module.impl.render;

import morfeov.coliseum.module.*;
import morfeov.coliseum.setting.*;
import morfeov.coliseum.render.weather.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

@ModuleInfo(id="china_hat",name="ChinaHat",category=Category.RENDER,description="Китайская шляпа и новогодняя шапка Деда Мороза")
public final class ChinaHat extends morfeov.coliseum.module.Module implements AutoCloseable {
    public final ModeSetting style=add(new ModeSetting("Модель","Китайская шляпа","Китайская шляпа","Дед Мороз"));
    public final BooleanSetting theme=add(new BooleanSetting("Цвет темы",false).visibleWhen(()->style.is("Китайская шляпа")));
    private final WeatherPass pass=new WeatherPass();
    public void render(Matrix4f projection,Matrix4f view) {
        var mc=Minecraft.getInstance();var player=mc.player;
        if(!enabled()||player==null||player.isInvisible()||player.isSpectator()||mc.options.getCameraType().isFirstPerson())return;
        float partial=mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var camera=mc.gameRenderer.mainCamera().position();var eye=player.getEyePosition(partial);
        float yaw=Mth.rotLerp(partial,player.yHeadRotO,player.yHeadRot);
        float pitch=Mth.lerp(partial,player.xRotO,player.getXRot());
        var matrix=new Matrix4f(projection).mul(view).translate((float)(eye.x-camera.x),(float)(eye.y-camera.y),(float)(eye.z-camera.z))
            .rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(pitch)).translate(0,.19f,0);
        var builder=pass.begin(false);
        boolean santa=style.is("Дед Мороз");
        var mesh=santa?HatGeometry.SANTA:HatGeometry.CHINA;
        int accent=theme.enabled()?morfeov.coliseum.module.impl.themes.Theme.accent():0xffd5a34e;
        for(int i=0;i<mesh.length;i+=4) {
            int color;
            float material=mesh[i+3];
            if(santa)color=material<0?0xfffff1df:shade(0xffc51f36,material);
            else color=shade(accent,material);
            builder.addVertex(mesh[i],mesh[i+1],mesh[i+2]).setColor(color);
        }
        pass.draw(builder,WeatherPipelines.HAT,matrix,null);
    }
    private static int shade(int color,float brightness){return 0xff000000|Math.round((color>>16&255)*brightness)<<16|Math.round((color>>8&255)*brightness)<<8|Math.round((color&255)*brightness);}
    @Override protected void onDisable(){close();}
    @Override public void close(){pass.close();}
}
