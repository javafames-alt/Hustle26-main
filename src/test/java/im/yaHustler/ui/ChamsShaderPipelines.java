package im.yaHustler.ui;
import java.util.*;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import im.yaHustler.render.chams.*;
import im.yaHustler.module.impl.render.Chams;
public final class ChamsShaderPipelines {
    public static List<RenderPipeline> collect(){
        ChamsBlurPass.initMaskPipeline();ChamsBlurPass.initShaderPipeline();ChamsGlowPass.initShaderPipelines();
        var result=new ArrayList<RenderPipeline>(List.of(ChamsBlurPass.maskPipeline,ChamsBlurPass.blurPipeline,ChamsGlowPass.maskPipeline,ChamsGlowPass.surfacePipeline,ChamsGlowPass.visibilityPipeline,ChamsGlowPass.blurPipeline,ChamsGlowPass.compositePipeline));
        for(int flags=0;flags<4;flags++)result.add(Chams.type(flags,net.minecraft.resources.Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png")).pipeline());
        return result;
    }
}
