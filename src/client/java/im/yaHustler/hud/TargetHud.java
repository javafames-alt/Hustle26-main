package im.yaHustler.hud;

import im.yaHustler.font.FigmaFont;
import im.yaHustler.font.MsdfPipeline;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.ui.Draw;
import im.yaHustler.ui.InterfaceAssets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class TargetHud extends HudElement {
    private static final float H = 34f, RADIUS = 9f, HEAD = 22f, RING = 9.5f;
    private static final long KEEP_MS = 1000;
    private static final int ABSORPTION = 0xffffcc00;
    private static final EquipmentSlot[] GEAR = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private LivingEntity target, shownTarget;
    private long lastSeen, lastFrame;
    private float alpha, hp = -1, trail = -1, dt;
    private boolean showHealth, showEquipment;
    private int gearCount;
    private int hpValue = Integer.MIN_VALUE;
    private String hpText = "";
    private int measuredWidth = 120;

    public TargetHud(NumberSetting x, NumberSetting y) {
        super(x, y);
    }

    @Override
    protected float radius() {
        return RADIUS;
    }

    @Override
    protected float shadowAlpha() {
        return alpha;
    }

    @Override
    protected void measure(Minecraft client, boolean editing) {
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
            if (shownTarget != target) hp = trail = -1;
            shownTarget = target;
        }
        if (shownTarget == null || alpha <= .02f) {
            if (!show) shownTarget = null;
            return;
        }

        var options = HudStyle.options();
        showEquipment = options == null || options.targetEquipment.enabled();
        showHealth = options == null || options.targetHealth.enabled();
        String name = shownTarget.getName().getString();
        float nameWidth = FigmaFont.mediumWidth(name, 8f / 11);
        gearCount = 0;
        if (showEquipment) {
            for (EquipmentSlot slot : GEAR) {
                if (!shownTarget.getItemBySlot(slot).isEmpty()) gearCount++;
            }
        }
        float centerWidth = Math.max(nameWidth, gearCount * 11f);
        centerWidth = Math.clamp(centerWidth, 40f, 120f);
        measuredWidth = (int)Math.ceil(6f + HEAD + 7f + centerWidth + 7f + (showHealth ? 8f + RING * 2 : 0));
        size(measuredWidth, (int) H);
    }

    private LivingEntity find(Minecraft client) {
        var combatTarget = im.yaHustler.port.CombatHooks.target();
        if (combatTarget != null && combatTarget.isAlive()) return combatTarget;
        if (client.crosshairPickEntity instanceof LivingEntity living && living.isAlive() && living != client.player) return living;
        var lastHit = client.player.getLastHurtMob();
        if (lastHit != null && lastHit.isAlive() && client.player.tickCount - client.player.getLastHurtMobTimestamp() < 60) return lastHit;
        return null;
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, Minecraft client, int ix, int iy) {
        LivingEntity entity = shownTarget;
        if (entity == null) return;
        float x = ix, y = iy, a = alpha;
        int accent = HudStyle.accent();

        float max = Math.max(1, entity.getMaxHealth());
        float health = Math.max(0, entity.getHealth());
        float gold = Math.max(0, entity.getAbsorptionAmount());
        hp = ease(hp, Math.clamp(health / max, 0, 1), 9.2f);
        trail = trail < hp ? hp : ease(trail, hp, 7.4f);

        HudStyle.panel(g, ix, iy, measuredWidth, (int) H, a, RADIUS);

        float hx = x + 6f, hy = y + (H - HEAD) / 2f;
        head(g, entity, hx, hy, a);

        float tx = hx + HEAD + 7f;
        float cy = y + H / 2f;
        float ringX = x + measuredWidth - 7f - RING;
        float nameRight = showHealth ? ringX - RING - 8 : x + measuredWidth - 7;
        String name = FigmaFont.fitMedium(entity.getName().getString(), (int)(nameRight - tx), 7.8f / 11);
        HudStyle.text(g, name, tx, cy - (gearCount > 0 ? 6 : 0), fade(HudStyle.TEXT, a), 7.8f, true);

        float ixPos = tx;
        float itemY = cy + 3f;
        if (gearCount > 0) {
            float prevOpacity = im.yaHustler.ui.GuiOpacity.push(a);
            try {
                for (EquipmentSlot slot : GEAR) {
                    ItemStack stack = entity.getItemBySlot(slot);
                    if (stack.isEmpty()) continue;
                    referenceItem(g, stack, ixPos, itemY);
                    ixPos += 11f;
                }
            } finally {
                im.yaHustler.ui.GuiOpacity.restore(prevOpacity);
            }
        }

        if (showHealth) {
            Draw.circle(g, ringX, cy, RING + .5f, fade(HudStyle.FIELD, a));
            HudStyle.arc(g, ringX, cy, RING, 1, fade(0x70566886, a));
            HudStyle.arc(g, ringX, cy, RING, trail, fade(0xff8196b5, a));
            HudStyle.arc(g, ringX, cy, RING, hp, fade(gold > 0 ? ABSORPTION : accent, a));
            float textScale = 6.5f / 11;
            String hpVal = FigmaFont.fitMedium(hp(health + gold), (int)(RING * 2 - 3), textScale);
            float tw = FigmaFont.mediumWidth(hpVal, textScale);
            HudStyle.text(g, hpVal, ringX - tw / 2f, cy, fade(HudStyle.TEXT, a), 6.5f, true);
        }
    }

    private static void referenceItem(GuiGraphicsExtractor g, ItemStack stack, float x, float y) {
        if (stack.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(9.5f / 16f, 9.5f / 16f);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
        if (stack.isDamageableItem()) {
            float remaining = 1 - stack.getDamageValue() / (float) Math.max(1, stack.getMaxDamage());
            Draw.fillF(g, x, y + 9.5f, 9.5f * remaining, 1f, HudStyle.accent());
        }
    }

    private static void head(GuiGraphicsExtractor g, LivingEntity entity, float x, float y, float a) {
        if (entity instanceof AbstractClientPlayer player) {
            Identifier skin = player.getSkin().body().texturePath();
            int tint = fade(hurtTint(entity, HudStyle.textColor()), a);
            face(g, skin, x, y, 8, tint);
            face(g, skin, x, y, 40, tint);
        } else {
            Draw.roundedF(g, x, y, HEAD, HEAD, 4f, fade(0x40101412, a));
            InterfaceAssets.centeredIcon(g, "user", x + HEAD / 2f, y + HEAD / 2f, 10, fade(HudStyle.accent(), a));
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
