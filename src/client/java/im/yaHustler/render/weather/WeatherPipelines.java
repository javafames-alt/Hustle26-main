package im.yaHustler.render.weather;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.Optional;
import im.yaHustler.render.hands.HandsFx;
import net.minecraft.resources.Identifier;

/** Explicit 26.3 camera bindings and reversed depth for the donor weather materials. */
public final class WeatherPipelines {
    public static final RenderPipeline WET = RenderPipeline.builder()
        .withLocation(id("pipeline/weather/wet_surface"))
        .withVertexShader("core/screenquad").withFragmentShader(id("core/weather/wet_surface"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withBindGroupLayout(HandsFx.layout(b -> {
            HandsFx.sampler(b, "DepthSampler"); HandsFx.ubo(b, "WetSurfaceData");
        })).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(Optional.empty()).withCull(false).build();
    public static final RenderPipeline SNOW = geometry("hustle263_snow", DefaultVertexFormat.POSITION_COLOR,
        PrimitiveTopology.TRIANGLES, true, false, false, "SnowMatrices", "HustleData");
    public static final RenderPipeline FLAKE = geometry("weather_flake", DefaultVertexFormat.POSITION_TEX_COLOR,
        PrimitiveTopology.TRIANGLES, true, false, true, "SnowfallMatrices");
    public static final RenderPipeline HALLOWEEN = geometry("weather_halloween", DefaultVertexFormat.POSITION_TEX_COLOR,
        PrimitiveTopology.TRIANGLES, true, false, false, "SnowfallMatrices");
    public static final RenderPipeline PUMPKIN = geometry("weather_pumpkin", DefaultVertexFormat.POSITION_TEX_COLOR,
        PrimitiveTopology.TRIANGLES, true, true, false, "SnowfallMatrices");
    /** Procedural sky dome for Weather «Свой»; drawn in the vanilla sky pass, before terrain. */
    public static final RenderPipeline SKY = geometry("weather_sky", DefaultVertexFormat.POSITION_COLOR,
        PrimitiveTopology.TRIANGLES, false, false, false, "SnowfallMatrices", "SkyData");
    public static final RenderPipeline HAT = geometry("weather_hat", DefaultVertexFormat.POSITION_COLOR,
        PrimitiveTopology.QUADS, true, true, false, "EffectMatrices");
    public static final RenderPipeline WORLD_RIBBON = RenderPipeline.builder()
        .withLocation(id("pipeline/world_ribbon"))
        .withVertexShader(id("core/weather/weather_hat")).withFragmentShader(id("core/weather/weather_hat"))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withBindGroupLayout(HandsFx.layout(b -> HandsFx.ubo(b, "EffectMatrices")))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 0, 0)))
        .withCull(false).build();
    private WeatherPipelines() {}
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("hustle263", path); }
    private static RenderPipeline geometry(String name, VertexFormat format, PrimitiveTopology topology,
                                           boolean depth, boolean write, boolean texture, String... uniforms) {
        return RenderPipeline.builder().withLocation(id("pipeline/weather/" + name))
            .withVertexShader(id("core/weather/" + name)).withFragmentShader(id("core/weather/" + name))
            .withVertexBinding(0, format).withPrimitiveTopology(topology)
            .withBindGroupLayout(HandsFx.layout(b -> {
                for (String uniform : uniforms) HandsFx.ubo(b, uniform);
                if (texture) HandsFx.sampler(b, "FlakeSampler");
            })).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(depth ? Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, write, 0, 0)) : Optional.empty())
            .withCull(name.equals("weather_pumpkin")).build();
    }
}
