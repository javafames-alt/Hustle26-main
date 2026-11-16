package morfeov.coliseum.hud;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.font.FigmaFont;
import morfeov.coliseum.font.MsdfPipeline;
import morfeov.coliseum.setting.NumberSetting;
import morfeov.coliseum.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class TargetHud extends HudElement {
    private static final float W = 96, H = 36, RADIUS = 6, HEAD = 26, TEXT_X = 37;
    private static final int EQUIPMENT_ROW = 16;
    private static final long KEEP_MS = 1000;
    private static final int ABSORPTION = 0xffffcc00;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private LivingEntity target, shownTarget;
    private long lastSeen, lastFrame;
    private float alpha, hp = -1, trail = -1, absorption = -1, dt;
    private int hpValue = Integer.MIN_VALUE;
    private String hpText = "";
    private boolean equipment, healthLabel;
    private int panelHeight;

    public TargetHud(NumberSetting x, NumberSetting y) { super(x, y); }

    @Override
    protected float radius() { return RADIUS; }
    @Override protected float shadowTop() { return equipment ? EQUIPMENT_ROW : 0; }
    @Override protected float shadowAlpha() { return alpha; }

    @Override
    protected void measure(Minecraft client, boolean editing) {
        var options = HudStyle.options();
        equipment = options == null || options.targetEquipment.enabled();
        healthLabel = options == null || options.targetHealth.enabled();
        panelHeight = healthLabel ? (int)H : 32;
        long now = System.nanoTime();
        dt = lastFrame == 0 ? 0 : Math.min(.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        long millis = System.currentTimeMillis();
        LivingEntity current = editing ? client.player : find(client);
        if (current != null) {
            target = current;
            lastSeen = millis;
        } else if (target != null && (!target.isAlive() || target.isRemoved() || millis - lastSeen > KEEP_MS)) {
            target = null;
        }
        boolean show = target != null;
        alpha = approach(alpha, show ? 1 : 0, dt * (show ? 9 : 5));
        if (show) {
            if (shownTarget != target) hp = trail = absorption = -1;
            shownTarget = target;
        }
        if (shownTarget == null || alpha <= .02f) {
            if (!show) shownTarget = null;
            return;
        }
        size((int) W, panelHeight + (equipment ? EQUIPMENT_ROW : 0));
    }
    private LivingEntity find(Minecraft client) {
        var combatTarget=morfeov.coliseum.port.CombatHooks.target();
        if(combatTarget!=null && combatTarget.isAlive())return combatTarget;
        if (client.crosshairPickEntity instanceof LivingEntity living && living.isAlive() && living != client.player) return living;
        return null;
    }
    @Override
    protected void draw(GuiGraphicsExtractor g, Minecraft client, int ix, int iy) {
        LivingEntity entity = shownTarget;
        float x = ix, y = iy + (equipment ? EQUIPMENT_ROW : 0), a = alpha;
        int accent = HudStyle.accent();

        float max = Math.max(1, entity.getMaxHealth());
        float health = Math.max(0, entity.getHealth()), gold = Math.max(0, entity.getAbsorptionAmount());
        hp = ease(hp, Math.clamp(health / max, 0, 1), 9.2f);
        trail = trail < hp ? hp : ease(trail, hp, 7.4f);
        absorption = ease(absorption, Math.clamp(gold / 20f, 0, 1), 9.2f);

        HudStyle.panel(g, ix, (int)y, (int) W, panelHeight, a, RADIUS);
        float hx = x + 5, hy = y + (panelHeight - HEAD) / 2, tx = x + TEXT_X, bw = W - TEXT_X - 5;
        head(g, entity, hx, hy, a);
        String healthText = hp(health + gold);
        String targetName = FigmaFont.fitMedium(entity.getName().getString(), Math.round(bw), 7.5f / 11);
        FigmaFont.drawMedium(g, targetName, tx, y + 7, fade(HudStyle.TEXT, a), 7.5f / 11);
        float barY = y + (healthLabel ? 27 : 22);
        if (healthLabel) {
            float textWidth = FigmaFont.width(healthText, 7f / 11);
            FigmaFont.draw(g, healthText, tx + bw - textWidth, barY - 3, fade(HudStyle.TEXT, a), 7f / 11);
            bw = Math.max(4, bw - textWidth - 4);
        }
        Draw.roundedF(g, tx, barY, bw, 2, 1, fade(0x66101412, a));
        Draw.roundedF(g, tx, barY, bw * trail, 2, 1, fade(0xb4ff5f5f, a));
        Draw.roundedF(g, tx, barY, bw * hp, 2, 1, fade(accent, a));
        if (gold > 0) Draw.roundedF(g, tx + bw * Math.max(0, hp - absorption), barY, bw * Math.min(hp, absorption), 2, 1, fade(ABSORPTION, a));
        if (!equipment) return;
        float itemX = x + 4, itemY = iy + 1;
        float previousOpacity = morfeov.coliseum.ui.GuiOpacity.push(a);
        try {
            referenceItem(g, entity.getMainHandItem(), itemX, itemY); itemX += 14;
            referenceItem(g, entity.getOffhandItem(), itemX, itemY); itemX += 14;
            for (EquipmentSlot slot : ARMOR) {
                referenceItem(g, entity.getItemBySlot(slot), itemX, itemY); itemX += 14;
            }
        } finally { morfeov.coliseum.ui.GuiOpacity.restore(previousOpacity); }
    }
    private static void referenceItem(GuiGraphicsExtractor g, ItemStack stack, float x, float y) {
        if (stack.isEmpty()) return;
        g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(11.5f / 16, 11.5f / 16); g.item(stack, 0, 0); g.pose().popMatrix();
        if (stack.isDamageableItem()) {
            float remaining = 1 - stack.getDamageValue() / (float)Math.max(1, stack.getMaxDamage());
            Draw.fillF(g, x + 1, y + 12, 9.5f * remaining, 1, HudStyle.accent());
        }
    }
    private static void head(GuiGraphicsExtractor g, LivingEntity entity, float x, float y, float a) {
        if (entity instanceof AbstractClientPlayer player) {
            Identifier skin = player.getSkin().body().texturePath();
            int tint = fade(hurtTint(entity, HudStyle.textColor()), a);
            face(g, skin, x, y, 8, tint);
            face(g, skin, x, y, 40, tint);
        } else {
            FigmaFont.draw(g, "?", x + (HEAD - FigmaFont.width("?", 1.2f)) / 2f, y + HEAD / 2f - 4.8f, fade(hurtTint(entity, 0xffdcdcdc), a), 1.2f);
        }
    }
    private static int hurtTint(LivingEntity entity, int color) {
        if (entity.hurtTime <= 0) return color;
        float k = Math.clamp(entity.hurtTime / 10f, 0, 1);
        k = 1 - (1 - k) * (1 - k);
        int gr = color >> 8 & 0xff, b = color & 0xff;
        gr = Math.round(gr + (70 - gr) * k);
        b = Math.round(b + (70 - b) * k);
        return color & 0xffff0000 | gr << 8 | b;
    }
    private static void face(GuiGraphicsExtractor g, Identifier skin, float x, float y, int u, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(HEAD / 8f, HEAD / 8f);
        g.blit(MsdfPipeline.head(), skin, 0, 0, u, 8, 8, 8, 8, 8, 64, 64, color);
        g.pose().popMatrix();
    }
    private float ease(float current, float target, float speed) {
        return current < 0 ? target : current + (target - current) * Math.min(1, dt * speed);
    }
    private static float approach(float current, float target, float step) {
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
    }
    private static int fade(int color, float a) {
        return Math.round((color >>> 24) * Math.clamp(a, 0, 1)) << 24 | color & 0xffffff;
    }
    private String hp(float health) {
        int value = Math.round(health * 10);
        if (value != hpValue) {
            hpValue = value;
            hpText = value % 10 == 0 ? Integer.toString(value / 10) : value / 10 + "." + value % 10;
        }
        return hpText;
    }
}
