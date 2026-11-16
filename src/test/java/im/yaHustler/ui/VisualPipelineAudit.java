package im.yaHustler.ui;
import java.lang.reflect.Modifier;
import java.util.*;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import im.yaHustler.module.impl.render.*;
import im.yaHustler.render.coliseimvisual.*;
/** Includes every original fullscreen/geometry pipeline, not just the default module modes. */
public final class VisualPipelineAudit {
    public static List<RenderPipeline> collect() throws Exception {
        Map<Object,RenderPipeline> result = new LinkedHashMap<>();
        for (Object instance : new Object[]{im.yaHustler.render.weather.WeatherPipelines.class,im.yaHustler.render.hands.HandFlameRenderer.class,im.yaHustler.render.hands.HandFireRenderer.class,FigmaBackdropBlur.class,new CustomCrystal(),new MotionBlur(),new BlockOverlay(),new Trails(),new Ambience(),new JumpCircles(),new VolumetricFogRenderer(),new AuroraRenderer(),EspEffects.class,EntityGlowRenderer.class}) {
            Class<?> type = instance instanceof Class<?> c ? c : instance.getClass();
            for (var field : type.getDeclaredFields()) if(field.getType() == RenderPipeline.class) {
                field.setAccessible(true);
                var pipeline = (RenderPipeline)field.get(Modifier.isStatic(field.getModifiers()) ? null : instance);
                result.put(pipeline.getLocation(),pipeline);
            }
        }
        return List.copyOf(result.values());
    }
}
