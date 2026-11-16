package im.yaHustler.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.setting.ModeSetting;
import im.yaHustler.setting.NumberSetting;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

@ModuleInfo(id="companion",name="Companion",category=Category.RENDER,description="Парящий спутник Claude Code или Codex возле плеча")
public final class Companion extends Module {
    private final ModeSetting character=add(new ModeSetting("Логотип","Claude Code","Claude Code","Codex"));
    private final ModeSetting shoulder=add(new ModeSetting("Плечо","Правое","Правое","Левое"));
    private final NumberSetting size=add(new NumberSetting("Размер",.28,.15,.5,.01));
    private final NumberSetting motion=add(new NumberSetting("Покачивание",.035,0,.1,.005));
    private static final int[][] FACES = {{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};

    public void submit(PoseStack pose, SubmitNodeCollector queue, AvatarRenderState state, PlayerModel model) {
        if(!enabled())return;
        float time=state.ageInTicks*.065f;
        boolean claude=character.is("Claude Code");
        float stride=state.walkAnimationPos * 2.3f;
        float speed=Math.min(1, Math.abs(state.walkAnimationSpeed) * 3);
        pose.pushPose();
        try {
            model.body.translateAndRotate(pose);
            pose.translate(shoulder.is("Правое")?-.4f:.4f,-.22f+Math.sin(time)*motion.floatValue() - Math.abs(Math.sin(stride))*speed*.018,0);
            pose.rotate(Axis.YP.rotationDegrees((float)Math.sin(time*.7f)*12));
            float scale=size.floatValue();
            pose.scale(scale,scale,scale);
            queue.submitCustomGeometry(pose,RenderTypes.debugQuads(),(entry,vertices)->{
                int shell=claude?0xffd97955:0xffa694f4;
                int limbs=claude?0xffac4e33:0xff6152a9;
                box(entry,vertices,-.40f,-.28f,-.23f,.40f,.13f,.23f,shell);
                if (!claude) {
                    box(entry,vertices,-.31f,-.21f,-.26f,.31f,.04f,-.20f,0xff252039);
                    box(entry,vertices,-.04f,-.42f,-.03f,.04f,-.28f,.03f,limbs);
                    box(entry,vertices,-.09f,-.49f,-.08f,.09f,-.40f,.08f,0xffc0b0ff);
                }
                int legs=claude?4:2;
                for (int side : new int[]{-1,1}) {
                    float eye=side*.16f;
                    box(entry,vertices,eye-.035f,-.17f,-.28f,eye+.035f,-.08f,-.225f,claude?0xff381f1d:0xffe9f5ff);
                    for (int leg=0;leg<legs;leg++) {
                        float z=(leg-(legs-1)*.5f)*.13f;
                        float phase=stride+((leg+(side<0?1:0))%2)*(float)Math.PI;
                        float reach=z+(float)Math.cos(phase)*speed*.11f;
                        float foot=.33f-Math.max(0,(float)Math.sin(phase))*speed*.13f;
                        float x=side*(claude?.50f:.32f);
                        box(entry,vertices,Math.min(side*.29f,x)-.03f,.04f,z-.045f,
                                Math.max(side*.29f,x)+.03f,.14f,z+.045f,limbs);
                        box(entry,vertices,x-.045f,.12f,Math.min(z,reach)-.043f,
                                x+.045f,foot,Math.max(z,reach)+.043f,limbs);
                    }
                }
            });
        } finally {pose.popPose();}
    }

    private static void box(PoseStack.Pose pose, VertexConsumer vertices, float x0, float y0, float z0,
                            float x1, float y1, float z1, int color) {
        for (int[] face : FACES) for (int corner : face) {
            float x=(corner==1||corner==2||corner==5||corner==6)?x1:x0;
            float y=(corner==2||corner==3||corner==6||corner==7)?y1:y0;
            vertices.addVertex(pose.pose(),x,y,corner>=4?z1:z0).setColor(color);
        }
    }
}
