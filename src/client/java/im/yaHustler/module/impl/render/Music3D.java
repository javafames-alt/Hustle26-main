package im.yaHustler.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.Map;
import im.yaHustler.hud.Lyrics;
import im.yaHustler.hud.MediaSession;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.impl.themes.Theme;
import im.yaHustler.module.impl.themes.ThemeColor;
import im.yaHustler.render.music.Music3DScene;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

@ModuleInfo(id = "music_3d", name = "Music3D", category = Category.RENDER,
    description = "Анимированные 3D-строки песни из MediaPlayer в мире по направлению взгляда, в цветах темы")
public final class Music3D extends Module {
    private final NumberSetting distance = add(new NumberSetting("Music3D Distance", 4.5, 1.5, 12, .5));
    private final NumberSetting size = add(new NumberSetting("Music3D Size", 1, .5, 2, .1));
    private final NumberSetting trail = add(new NumberSetting("Music3D Trail", 1.6, 0, 4, .1));
    private final BooleanSetting glow = add(new BooleanSetting("Music3D Glow", true));
    private final Music3DScene scene = new Music3DScene();
    private final Map<String, GlyphRun> layouts = new LinkedHashMap<>();
    private ClientLevel level;

    private record GlyphRun(int[] glyphs, float[] offsets, float width) {}

    @Override protected void onDisable() { reset(); }

    private void reset() {
        scene.clear();
        layouts.clear();
        level = null;
    }

    @Override public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { reset(); return; }
        if (level != null && level != mc.level) reset();
        MediaSession.keepAlive();
    }

    public void submit(PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !camera.initialized) { reset(); return; }
        if (level != mc.level) { reset(); level = mc.level; }
        MediaSession.keepAlive();
        long position = MediaSession.positionMs(), duration = MediaSession.durationMs();
        if (!MediaSession.present() || duration > 0 && position >= duration) { scene.clear(); return; }
        String artist = MediaSession.artist(), title = MediaSession.title();
        Lyrics.ensure(artist, title);
        scene.update(artist + '\0' + title, position, MediaSession.playing(), Lyrics.cue(position),
                System.nanoTime(), trail.get(), () -> placement(mc, camera));
        if (mc.gui.hud.isHidden() || camera.isPanoramicMode) return;
        int accent = Theme.accent(), secondary = Theme.color(ThemeColor.ACCENT_2);
        for (var caption : scene.captions()) draw(mc.font, new PoseStack(), collector, camera, caption, accent, secondary);
    }

    private Music3DScene.Placement placement(Minecraft mc, CameraRenderState camera) {
        Vector3f forward = camera.orientation.transform(new Vector3f(0, 0, -1));
        Vector3f up = camera.orientation.transform(new Vector3f(0, 1, 0));
        Vec3 direction = new Vec3(forward.x, forward.y, forward.z);
        Vec3 origin = camera.pos.add(-up.x * .22, -up.y * .22, -up.z * .22);
        Vec3 target = origin.add(direction.scale(distance.get()));
        var hit = mc.level.clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mc.player));
        double range = hit.getType() == HitResult.Type.MISS ? distance.get()
                : Math.max(.08, origin.distanceTo(hit.getLocation()) * .65);
        return new Music3DScene.Placement(origin.add(direction.scale(range)), camera.orientation, range);
    }

    private void draw(Font font, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera,
                      Music3DScene.Caption caption, int accent, int secondary) {
        double age = caption.age(scene.clock());
        float entry = ease((float)Math.clamp(age / .5, 0, 1));
        float retirement = caption.retirement(scene.clock(), trail.get());
        float alpha = 1 - ease(retirement);
        if (alpha < .04f || entry < .01f) return;
        GlyphRun run = layout(font, caption.line().text());
        var placement = caption.placement();
        if (placement.position().distanceToSqr(camera.pos) > 64 * 64) return;
        float scale = (float)Math.min(.032 * size.get(), placement.distance() * 1.3 / Math.max(1, run.width));
        scale *= (.86f + .14f * entry) * (1 - .12f * ease(retirement));
        Vec3 offset = placement.position().subtract(camera.pos);
        pose.pushPose();
        try {
            pose.translate(offset.x, offset.y, offset.z);
            pose.rotate(placement.rotation());
            pose.translate(0, .9f * ease(retirement) - .12f * (1 - entry), -.12f * (1 - entry));
            pose.scale(scale, -scale, scale);
            for (int i = 0; i < run.glyphs.length; i++) {
                float delay = run.glyphs.length <= 1 ? 0 : .16f * i / (run.glyphs.length - 1);
                float reveal = ease((float)Math.clamp((age - delay) / .32, 0, 1));
                float opacity = alpha * reveal;
                if (opacity < .04f) continue;
                float sung = ease(Math.clamp(caption.progress() * run.glyphs.length - i, 0, 1));
                float wave = (float)Math.sin(scene.clock() * 2.4 - i * .32) * .35f;
                int color = ColorUtil.lerp(ColorUtil.lerp(accent, Theme.color(ThemeColor.BACKGROUND), .28f),
                        ColorUtil.lerp(secondary, 0xffffffff, .38f), sung);
                pose.pushPose();
                try {
                    pose.translate(run.offsets[i] - run.width / 2, -font.lineHeight / 2f + (1 - reveal) * 3 + wave - sung * .35f, sung * .2f);
                    pose.pushPose();
                    try {
                        pose.translate(.65f, .8f, -1.1f);
                        text(collector, pose, run.glyphs[i], argb(ColorUtil.lerp(accent, 0xff000000, .65f), opacity * .75f), 0);
                    } finally { pose.popPose(); }
                    text(collector, pose, run.glyphs[i], argb(color, opacity),
                            glow.enabled() ? argb(accent, opacity * .22f) : 0);
                } finally { pose.popPose(); }
            }
        } finally { pose.popPose(); }
    }

    private GlyphRun layout(Font font, String text) {
        GlyphRun cached = layouts.get(text);
        if (cached != null) return cached;
        int[] points = text.codePoints().toArray();
        int[] glyphs = new int[points.length];
        float[] offsets = new float[points.length];
        float width = 0;
        for (int i = 0; i < points.length; i++) {
            glyphs[i] = points[i];
            offsets[i] = width;
            width += im.yaHustler.font.Fonts.INTER_BOLD.width(new String(Character.toChars(glyphs[i])));
        }
        GlyphRun result = new GlyphRun(glyphs, offsets, width);
        if (layouts.size() >= 8) layouts.remove(layouts.keySet().iterator().next());
        layouts.put(text, result);
        return result;
    }

    private static void text(SubmitNodeCollector collector, PoseStack pose, int glyph, int color, int outline) {
        if(outline!=0)for(int i=0;i<4;i++) {
            pose.pushPose();
            try {
                pose.translate(i==0?-.3f:i==1?.3f:0,i==2?-.3f:i==3?.3f:0,-.02f);
                im.yaHustler.font.Fonts.INTER_BOLD.worldGlyph(pose,glyph,outline);
            } finally {pose.popPose();}
        }
        im.yaHustler.font.Fonts.INTER_BOLD.worldGlyph(pose, glyph, color);
    }

    private static int argb(int color, float alpha) { return Math.round(Math.clamp(alpha, 0, 1) * 255) << 24 | color & 0xffffff; }
    private static float ease(float t) { return t * t * (3 - 2 * t); }
}

