package im.yaHustler.module.impl.render;

import im.yaHustler.module.*;
import im.yaHustler.friend.Friends;
import im.yaHustler.setting.*;
import im.yaHustler.render.weather.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@ModuleInfo(id="china_hat",name="ChinaHat",category=Category.RENDER,description="Китайская шляпа и новогодняя шапка Деда Мороза")
public final class ChinaHat extends im.yaHustler.module.Module implements AutoCloseable {
    public final ModeSetting style=add(new ModeSetting("Модель","Китайская шляпа","Китайская шляпа","Дед Мороз"));
    public final BooleanSetting theme=add(new BooleanSetting("Цвет темы",false).visibleWhen(()->style.is("Китайская шляпа")));
    private final BooleanSetting showOnFriends = add(new BooleanSetting("Отображать на друзьях", true));
    private final WeatherPass pass=new WeatherPass();
    public void render(Matrix4f projection,Matrix4f view) {
        var mc=Minecraft.getInstance();
        if (!enabled() || mc.player == null || mc.level == null) return;
        float partial=mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var camera=mc.gameRenderer.mainCamera().position();
        var builder=pass.begin(false);
        for (Player player : mc.level.players()) {
            if (!player.isAlive() || player.isInvisible() || player.isSpectator()) continue;
            if (player == mc.player ? mc.options.getCameraType().isFirstPerson()
                : !showOnFriends.enabled() || !Friends.contains(player)) continue;
            addHat(builder, player, partial, camera);
        }
        pass.draw(builder,WeatherPipelines.HAT,new Matrix4f(projection).mul(view),null);
    }
    private void addHat(com.mojang.blaze3d.vertex.BufferBuilder builder, Player player, float partial, net.minecraft.world.phys.Vec3 camera) {
        var eye=player.getEyePosition(partial);
        float yaw=Mth.rotLerp(partial,player.yHeadRotO,player.yHeadRot);
        float pitch=Mth.lerp(partial,player.xRotO,player.getXRot());
        var matrix=new Matrix4f().translation((float)(eye.x-camera.x),(float)(eye.y-camera.y),(float)(eye.z-camera.z))
            .rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(pitch)).translate(0,.19f,0);
        boolean santa=style.is("Дед Мороз");
        var mesh=santa?HatGeometry.SANTA:HatGeometry.CHINA;
        int accent=theme.enabled()?im.yaHustler.module.impl.themes.Theme.accent():0xffd5a34e;
        var vertex = new Vector3f();
        for(int i=0;i<mesh.length;i+=4) {
            int color;
            float material=mesh[i+3];
            if(santa)color=material<0?0xfffff1df:shade(0xffc51f36,material);
            else color=shade(accent,material);
            matrix.transformPosition(mesh[i], mesh[i+1], mesh[i+2], vertex);
            builder.addVertex(vertex.x, vertex.y, vertex.z).setColor(color);
        }
    }
    private static int shade(int color,float brightness){return 0xff000000|Math.round((color>>16&255)*brightness)<<16|Math.round((color>>8&255)*brightness)<<8|Math.round((color&255)*brightness);}
    @Override protected void onDisable(){close();}
    @Override public void close(){pass.close();}
}
