package im.yaHustler.font;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class MsdfPipeline {
    private static RenderPipeline pipeline, head, crisp, alphaMask, text, cover, slotOutline;
    private MsdfPipeline() {}
    private static RenderPipeline searchSurface, searchIcon;
    private static RenderPipeline colorField, hueStrip;
    private static RenderPipeline fovRing, smoothShape;
    public static RenderPipeline smoothShape() {
        if (smoothShape == null) smoothShape = build("pipeline/gui_smooth_shape", "core/gui_smooth_shape");
        return smoothShape;
    }
    public static RenderPipeline fovRing() {
        if (fovRing == null) fovRing = build("pipeline/gui_fov_ring", "core/fov_ring");
        return fovRing;
    }
    public static RenderPipeline colorField() {
        if (colorField == null) colorField = build("pipeline/gui_color_field", "core/color_field");
        return colorField;
    }
    public static RenderPipeline hueStrip() {
        if (hueStrip == null) hueStrip = build("pipeline/gui_hue_strip", "core/hue_strip");
        return hueStrip;
    }
    public static RenderPipeline searchSurface(){
        if(searchSurface==null)searchSurface=build("pipeline/gui_search_surface","core/search_surface");
        return searchSurface;
    }
    public static RenderPipeline searchIcon(){
        if(searchIcon==null)searchIcon=build("pipeline/gui_search_icon","core/search_icon");
        return searchIcon;
    }
    public static RenderPipeline get() {
        if (pipeline == null) pipeline = build("pipeline/gui_msdf", "core/msdf");
        return pipeline;
    }
    public static RenderPipeline head() {
        if (head == null) head = build("pipeline/gui_round_head", "core/round_head");
        return head;
    }
    public static RenderPipeline cover() {
        if (cover == null) cover = build("pipeline/gui_music_cover", "core/music_cover");
        return cover;
    }
    public static RenderPipeline slotOutline() {
        if (slotOutline == null) slotOutline = build("pipeline/gui_hotbar_slot_outline", "core/hotbar_slot_outline");
        return slotOutline;
    }
    public static RenderPipeline crisp() {
        if (crisp == null) crisp = build("pipeline/gui_crisp", "core/gui_crisp");
        return crisp;
    }
    public static RenderPipeline text() {
        if (text == null) text = build("pipeline/gui_text", "core/gui_text");
        return text;
    }
    public static RenderPipeline alphaMask() {
        if (alphaMask == null) alphaMask = build("pipeline/gui_alpha_mask", "core/gui_alpha_mask");
        return alphaMask;
    }
    private static RenderPipeline build(String location, String fragment) {
        RenderPipeline base = RenderPipelines.GUI_TEXTURED;
        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("hustle263", location))
            .withVertexShader(base.getShaders().get(ShaderType.VERTEX))
            .withFragmentShader(Identifier.fromNamespaceAndPath("hustle263", fragment))
            .withPolygonMode(base.getPolygonMode())
            .withCull(base.isCull())
            .withDepthStencilState(java.util.Optional.ofNullable(base.getDepthStencilState()))
            .withPrimitiveTopology(base.getPrimitiveTopology());
        base.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
        var targets = base.getColorTargetStates();
        for (int i = 0; i < targets.size(); i++) builder.withColorTargetState(i, targets.get(i));
        var formats = base.getVertexFormatBindings();
        for (int i = 0; i < formats.size(); i++) builder.withVertexBinding(i, formats.get(i));
        if (base.pushConstantSize() > 0) builder.withPushConstantSize(base.pushConstantSize());
        return builder.build();
    }
}
