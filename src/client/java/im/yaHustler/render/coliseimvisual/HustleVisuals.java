package im.yaHustler.render.coliseimvisual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;

/** World visual passes, placed before 26.3 clears depth for the 3D HUD/hands. */
public final class HustleVisuals {
    private static boolean worldFrame;
    private static long lastFailure;
    private HustleVisuals() {}
    public static boolean worldFrame() { return worldFrame; }
    public static void begin() {
        var optimization = Hustle26.MODULES.get(Optimization.class);
        if (optimization != null) optimization.recordWorldFrame();
        worldFrame = true;
        EspEffects.begin();
        var crystal = Hustle26.MODULES.get(CustomCrystal.class);
        if (crystal != null) crystal.beginFrame();
    }
    public static void close() {
        im.yaHustler.render.chams.ChamsModels.close();
        worldFrame = false;
        for (var module : Hustle26.MODULES.all()) if (module instanceof PortedModule imported) run(imported::onVisualDisable);
        var blur=Hustle26.MODULES.get(MotionBlur.class);
        if(blur!=null) run(blur::close);
        var overlay=Hustle26.MODULES.get(BlockOverlay.class);
        if(overlay!=null) run(overlay::close);
        var crystal=Hustle26.MODULES.get(CustomCrystal.class);
        if(crystal!=null) run(crystal::close);
        var hat=Hustle26.MODULES.get(ChinaHat.class);if(hat!=null)run(hat::close);
        var better=Hustle26.MODULES.get(BetterMinecraft.class);if(better!=null)run(better::close);
        run(EntityGlowRenderer::closeShared);
        var circles=Hustle26.MODULES.get(JumpCircles.class);
        if(circles!=null) run(circles::close);
        run(im.yaHustler.render.circles.CircleGeometry::close);
        var weather=Hustle26.MODULES.get(Weather.class);
        if(weather!=null) run(weather::close);
    }
    public static void matrices(Matrix4f projection) {
        var camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        WorldRenderMatrices.captureMatrices(camera.viewRotationMatrix, projection);
    }
    public static void render() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { worldFrame = false; return; }
        CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        Matrix4f projection = WorldRenderMatrices.projectionMatrix(), view = WorldRenderMatrices.viewMatrix();
        try {
                run(im.yaHustler.render.chams.ChamsModels::render);
                var blocks=Hustle26.MODULES.get(BlockESP.class);
                if(blocks!=null && blocks.enabled())run(()->blocks.render(view));
                Weather weather=Hustle26.MODULES.get(Weather.class);
                Ambience ambience = Hustle26.MODULES.get(Ambience.class);
                if (ambience != null && ambience.enabled() && (weather == null || !weather.enabled())) run(() -> {
                ambience.apply531(projection, view, mc.getDeltaTracker());
                ambience.apply550(projection, view, RenderSystem.getShaderFog());
                ambience.apply538(projection, view);
                });
                run(()->EspEffects.render(projection,view));
                var hat=Hustle26.MODULES.get(ChinaHat.class);if(hat!=null&&hat.enabled())run(()->hat.render(projection,view));
                CustomCrystal crystal = Hustle26.MODULES.get(CustomCrystal.class);
                if (crystal != null) run(crystal::render);
                Trails trails = Hustle26.MODULES.get(Trails.class);
                if (trails != null && trails.enabled()) run(() -> {
                PoseStack pose = new PoseStack(); pose.mulPose(view);
                trails.render(new WorldRenderFrame(mc.getDeltaTracker(), pose, null, camera.pos,
                    mc.gameRenderer.gameRenderState().levelRenderState.worldPartialTicks, projection, view));
                });
                if (ambience != null && ambience.enabled() && (weather == null || !weather.enabled())) run(() -> ambience.apply548(projection, view, mc.getDeltaTracker()));
                BlockOverlay overlay=Hustle26.MODULES.get(BlockOverlay.class);
                if(overlay!=null) run(() -> overlay.render(view));
                JumpCircles circles=Hustle26.MODULES.get(JumpCircles.class);
                if(circles!=null && circles.enabled()) run(()->{
                PoseStack pose=new PoseStack();pose.mulPose(view);
                circles.render(new im.yaHustler.render.circles.CircleFrame(mc.getDeltaTracker(),pose,camera.pos,projection,view));
                });
                if(weather!=null && weather.enabled()) run(() -> weather.render(projection,view));
                MotionBlur blur=Hustle26.MODULES.get(MotionBlur.class);
                if(blur!=null) run(() -> blur.render(projection,view));
        } finally { worldFrame = false; }
    }
    private static void run(Runnable effect) {
        try { effect.run(); }
        catch (RuntimeException e) {
                long now = System.currentTimeMillis();
                if (now - lastFailure > 5000) { lastFailure = now; Hustle26.LOGGER.error("Imported visual effect failed", e); }
        }
    }
}

