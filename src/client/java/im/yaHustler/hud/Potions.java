package im.yaHustler.hud;

import im.yaHustler.font.FigmaFont;
import im.yaHustler.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffectInstance;

public final class Potions extends HudElement {
    private static final int PAD = 5, HEADER = 18, ROW = 15, BOTTOM = 4, MIN_WIDTH = 84, ICON = 9;
    private static final float TITLE_SCALE = 7.5f / 11, TEXT_SCALE = 6.75f / 11;
    private final List<MobEffectInstance> effects = new ArrayList<>();
    private final List<String> names = new ArrayList<>(), levels = new ArrayList<>(), times = new ArrayList<>();
    private int width, height;
    private boolean icons, showTime;

    public Potions(NumberSetting x, NumberSetting y) { super(x, y); }

    @Override
    protected void measure(Minecraft client, boolean editing) {
        var o = HudStyle.options();
        icons = o == null || o.listIcons.enabled();
        showTime = o == null || o.potionTime.enabled();
        effects.clear(); names.clear(); levels.clear(); times.clear();
        float w = PAD + FigmaFont.width("Potions", TITLE_SCALE) + 6 + 10 * .82f + PAD;
        for (MobEffectInstance effect : client.player.getActiveEffects()) {
            if (!effect.showIcon()) continue;
            String name = effect.getEffect().value().getDisplayName().getString();
            String level = effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "";
            String time = duration(effect);
            effects.add(effect); names.add(name); levels.add(level); times.add(time);
            w = Math.max(w, PAD + (icons ? ICON + 4 : 0) + FigmaFont.width(name + level, TEXT_SCALE) + (showTime ? 10 + FigmaFont.width(time, TEXT_SCALE) : 0) + PAD);
        }
        if (effects.isEmpty() && !editing && (o == null || !o.emptyLists.enabled())) return;
        width = Math.max(MIN_WIDTH, (int) Math.ceil(w));
        height = effects.isEmpty() ? HEADER : HEADER + effects.size() * ROW + BOTTOM;
        size(width, height);
    }
    @Override
    protected void draw(GuiGraphicsExtractor g, Minecraft client, int x, int y) {
        HudStyle.panel(g, x, y, width, height);
        HudStyle.header(g, x, y, width, "Potions", "effects", !effects.isEmpty());
        for (int i = 0; i < effects.size(); i++) {
            int ry = y + HEADER + 3 + i * ROW;
            if (icons) g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effects.get(i).getEffect()), x + PAD, ry, ICON, ICON);
            float tx = x + PAD + (icons ? ICON + 4 : 0), ty = ry + 2;
            FigmaFont.draw(g, names.get(i), tx, ty, HudStyle.textColor(), TEXT_SCALE);
            if (!levels.get(i).isEmpty())
                FigmaFont.draw(g, levels.get(i), tx + FigmaFont.width(names.get(i), TEXT_SCALE), ty, HudStyle.mutedColor(), TEXT_SCALE);
            String time = times.get(i);
            if (showTime) FigmaFont.draw(g, time, x + width - PAD - FigmaFont.width(time, TEXT_SCALE), ty, effects.get(i).getDuration() <= 200 ? 0xffffb38a : HudStyle.accent(), TEXT_SCALE);
        }
    }
    private static String duration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) return "**:**";
        int seconds = effect.getDuration() / 20;
        int hours = seconds / 3600, minutes = seconds / 60 % 60, secs = seconds % 60;
        StringBuilder out = new StringBuilder(8);
        if (hours > 0) twoDigits(out.append(hours).append(':'), minutes);
        else out.append(minutes);
        return twoDigits(out.append(':'), secs).toString();
    }
    private static StringBuilder twoDigits(StringBuilder out, int value) {
        return out.append((char) ('0' + value / 10)).append((char) ('0' + value % 10));
    }
}
