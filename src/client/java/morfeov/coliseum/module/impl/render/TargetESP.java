package morfeov.coliseum.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.setting.BooleanSetting;
import morfeov.coliseum.setting.ModeSetting;
import morfeov.coliseum.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(id = "target_esp", name = "TargetESP", category = Category.RENDER,
    description = "Визуализация цели и объекта под прицелом")
public final class TargetESP extends morfeov.coliseum.port.PortModule {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Призраки", "Призраки", "Призрачный Всадник", "Молнии", "Кристаллы", "Ромб", "Кольцо", "Призрак", "Духи", "Мечи", "Молния", "Circle", "Ghosts"));
    private final NumberSetting size = add(new NumberSetting("Размер", 75, 50, 100, 5).visibleWhen(() -> mode.is("Ромб")));
    private final BooleanSetting hitRed = add(new BooleanSetting("Краснеть при ударе", true));
    private final BooleanSetting onHover = add(new BooleanSetting("On Hover", true));
    private final BooleanSetting onCombat = add(new BooleanSetting("Aura Target", true));
    private final BooleanSetting playersOnly = add(new BooleanSetting("Players Only", false));
    private final NumberSetting range = add(new NumberSetting("Hover Range", 8.0, 1.0, 30.0, 0.5));

    private static final float FADE_SPEED = 6.0f;
    private static final int SEGMENTS = 72;
    private static final float LINE = 0.025f;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / SEGMENTS;
            COS[i] = (float) Math.cos(angle);
            SIN[i] = (float) Math.sin(angle);
        }
    }

    private final AuraTargetEspRenderer originalEsp=new AuraTargetEspRenderer();
    private final NumberSetting sourceSpeed=add(new NumberSetting("ESP Скорость",1,.2,3,.1));
    private final NumberSetting sourceSize=add(new NumberSetting("ESP Размер",1,.4,2.5,.05));
    private final BooleanSetting sourceGlow=add(new BooleanSetting("ESP Свечение",true));
    private final Ghosts ghosts = new Ghosts();
    private final NewCodeTargetSprites sprites = new NewCodeTargetSprites();
    private final SwordTargetEspRenderer swords = new SwordTargetEspRenderer();
    private final LightningTargetEspRenderer lightning = new LightningTargetEspRenderer();
    private String lastMode;
    private LivingEntity last;
    private float fade;
    private long lastNano = System.nanoTime();

    @Override
    protected void onEnable() {
        fade = 0.0f;
        last = null;
        ghosts.reset();
        swords.reset();
        lightning.reset();
        lastMode = mode.get();
        lastNano = System.nanoTime();
    }

    @Override
    protected void onDisable() {
        fade = 0.0f;
        last = null;
        ghosts.reset();
        swords.reset();
        lightning.reset();
    }

    @Override public void onAttack(morfeov.coliseum.port.event.EventAttackEntity event) {
        if (hitRed.enabled()) swords.onAttack();
    }

    @Override public void onDisconnect(morfeov.coliseum.port.event.EventDisconnect event) { onDisable(); }

    public void submit(PoseStack poseStack, SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = Math.min((now - lastNano) / 1.0E9f, 0.1f);
        lastNano = now;

        if (mc.player == null || mc.level == null) {
            fade = 0.0f;
            last = null;
            ghosts.reset();
            return;
        }

        LivingEntity picked = pick(mc);
        if (!mode.get().equals(lastMode)) {
            ghosts.reset(); swords.reset(); lightning.reset(); last = null; fade = 0;
            lastMode = mode.get();
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if(mode.is("Призраки")||mode.is("Призрак")||mode.is("Ghosts")||mode.is("Призрачный Всадник")||mode.is("Молнии")||mode.is("Кристаллы")) {
            originalEsp.render(poseStack,collector,picked,new AuraTargetEspRenderer.Style(){
                public String mode(){return TargetESP.this.mode.is("Призрак")||TargetESP.this.mode.is("Ghosts")?"Призраки":TargetESP.this.mode.get();}
                public int baseColor(){return morfeov.coliseum.module.impl.themes.Theme.accent();}
                public boolean damageRed(){return hitRed.enabled();}public boolean glow(){return sourceGlow.enabled();}
                public float speed(){return sourceSpeed.floatValue();}public float size(){return sourceSize.floatValue();}
            });
            return;
        }
        if (mode.is("Мечи")) {
            swords.render(mc, poseStack, collector, picked, partialTick, hitRed.enabled(), 1);
            return;
        }
        if (mode.is("Молния")) {
            lightning.render(mc, poseStack, collector, picked, dt, partialTick, hitRed.enabled());
            return;
        }
        if (mode.is("Ghosts")) {
            ghosts.render(mc, poseStack, collector, picked, dt);
            return;
        }
        if (picked != null) last = picked;
        fade = Math.clamp(fade + (picked != null ? dt : -dt) * FADE_SPEED, 0.0f, 1.0f);

        if (last != null && !last.isAlive() || fade <= 0.001f) {
            last = null;
            return;
        }
        if (last == null) return;

        if (mode.is("Circle") || mode.is("Кольцо")) drawCircle(mc, poseStack, collector, last, fade);
        else sprites.render(mc, poseStack, collector, last, fade, mode.get(), size.floatValue(), hitRed.enabled());
    }

    private void drawCircle(Minecraft mc, PoseStack poseStack, SubmitNodeCollector collector, LivingEntity target, float alpha) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        float cx = (float) (Mth.lerp(partialTick, target.xOld, target.getX()) - cam.x);
        float cy = (float) (Mth.lerp(partialTick, target.yOld, target.getY()) - cam.y);
        float cz = (float) (Mth.lerp(partialTick, target.zOld, target.getZ()) - cam.z);

        float height = target.getBbHeight() + 0.15f;
        float radius = target.getBbWidth() * 0.8f;
        double time = System.currentTimeMillis() % 1750.0;
        boolean goingDown = time > 875.0;
        double t = goingDown ? time / 875.0 - 1.0 : 1.0 - time / 875.0;
        double ease = t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;
        float y = (float) (cy + height * ease);
        float trail = (float) (height * 0.8 * Math.min(ease, 1.0 - ease) * (goingDown ? -1.0 : 1.0));

        int color = NewCodeTargetSprites.color(target, hitRed.enabled(), 0);
        float r = (color >> 16 & 0xff) / 255.0f;
        float g = (color >> 8 & 0xff) / 255.0f;
        float b = (color & 0xff) / 255.0f;
        float a = (color >>> 24) / 255.0f * alpha;

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
            for (int i = 0; i < SEGMENTS; i++) {
                float x0 = cx + COS[i] * radius;
                float z0 = cz + SIN[i] * radius;
                float x1 = cx + COS[i + 1] * radius;
                float z1 = cz + SIN[i + 1] * radius;

                vc.addVertex(pose, x0, y, z0).setColor(r, g, b, a * 0.55f);
                vc.addVertex(pose, x1, y, z1).setColor(r, g, b, a * 0.55f);
                vc.addVertex(pose, x1, y + trail, z1).setColor(r, g, b, 0.0f);
                vc.addVertex(pose, x0, y + trail, z0).setColor(r, g, b, 0.0f);

                vc.addVertex(pose, x0, y - LINE, z0).setColor(r, g, b, a);
                vc.addVertex(pose, x1, y - LINE, z1).setColor(r, g, b, a);
                vc.addVertex(pose, x1, y + LINE, z1).setColor(r, g, b, a);
                vc.addVertex(pose, x0, y + LINE, z0).setColor(r, g, b, a);
            }
        });
    }

    private LivingEntity pick(Minecraft mc) {
        if (onHover.enabled()) {
            LivingEntity hovered = hovered(mc);
            if (hovered != null) return hovered;
        }
        if (onCombat.enabled()) {
            var combatTarget=morfeov.coliseum.port.CombatHooks.target();
            if(valid(mc,combatTarget))return combatTarget;
        }
        return null;
    }

    private LivingEntity hovered(Minecraft mc) {
        float max = range.floatValue();
        if (mc.crosshairPickEntity instanceof LivingEntity living
                && valid(mc, living) && living.distanceTo(mc.player) <= max) return living;
        if (mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living
                && valid(mc, living) && living.distanceTo(mc.player) <= max) return living;
        return null;
    }

    private boolean valid(Minecraft mc, LivingEntity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        return !playersOnly.enabled() || entity instanceof Player;
    }
}
