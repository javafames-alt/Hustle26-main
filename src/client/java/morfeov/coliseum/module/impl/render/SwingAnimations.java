package morfeov.coliseum.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import morfeov.coliseum.animation.Swing3DAnimation;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.punchy.PunchyBridge;
import morfeov.coliseum.setting.BooleanSetting;
import morfeov.coliseum.setting.ModeSetting;
import morfeov.coliseum.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

@ModuleInfo(id = "swinganimations", name = "Swing Animations", category = Category.RENDER,
    description = "Анимации взмаха и красивые руки")
public final class SwingAnimations extends Module {
    private static SwingAnimations instance;
    private static final String MODE_1="Мод 1",MODE_2="Мод 2",MODE_3="Мод 3",MODE_4="Мод 4",MODE_5="Мод 5",MODE_360="360";
    private final NumberSetting rotateAngle=add(new NumberSetting("Угол поворота",75,0,360,1)).visibleWhen(()->this.mode.is(MODE_1));
    private final NumberSetting spinSpeed360=add(new NumberSetting("Скорость прокрута",1,.1,10,.1)).visibleWhen(()->this.mode.is(MODE_360));
    private final NumberSetting tipTilt=add(new NumberSetting("Наклон кончика",-20,-90,90,1)).visibleWhen(()->this.mode.get().startsWith("Мод ")&&!this.mode.is(MODE_5));
    private final NumberSetting swingIntensity=add(new NumberSetting("Интенсивность взмаха",5,1,10,1)).visibleWhen(()->this.mode.get().startsWith("Мод "));
    private final BooleanSetting spinAlways=add(new BooleanSetting("Крутится всегда",false)).visibleWhen(()->this.mode.is(MODE_360));
    private boolean deltaActive(){return enabled()&&(mode.get().startsWith("Мод ")||mode.is("360"))&&(!onlyAura.enabled()||morfeov.coliseum.port.CombatHooks.target()!=null);}
    public boolean applyArmAnimation(PoseStack matrices, HumanoidArm arm, float swingProgress, float equipProgress) {
        if (matrices == null || arm == null || !deltaActive()) {
            return false;
        }

        String mode = this.mode.get();

        if (MODE_360.equals(mode)) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isUsingItem() && Minecraft.getInstance().player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                return false;
            }

            float rot = 0.0f;
            if (spinAlways.enabled()) {
                rot = (System.currentTimeMillis() * spinSpeed360.floatValue() * 0.25f) % 360.0f;
                if (swingProgress > 0.0f) {
                    float swingRot = net.minecraft.util.Mth.clamp(swingProgress * spinSpeed360.floatValue(), 0.0f, 1.0f) * 360.0f;
                    rot = (rot + swingRot) % 360.0f;
                }
            } else if (swingProgress > 0.0f) {
                rot = net.minecraft.util.Mth.clamp(swingProgress * spinSpeed360.floatValue(), 0.0f, 1.0f) * 360.0f;
            }

            if (rot != 0.0f) {
                matrices.rotateDegrees(com.mojang.math.Axis.XP, -rot);
            }
            return true;
        }

        int armX = arm == HumanoidArm.RIGHT ? 1 : -1;
        float anim = (float) Math.sin(((double) swingProgress) * Math.PI);
        float power = swingIntensity.floatValue() * 10.0f;
        boolean isMod5 = MODE_5.equals(mode);

        // Undo vanilla equip translation
        matrices.translate(-armX * 0.56f, 0.52f + 0.6f * equipProgress, 0.72f);

        // Delta base translation
        matrices.translate(armX * (isMod5 ? 0.5f : 0.72f), -0.5f, isMod5 ? -0.72f : -1.0f);

        if (!isMod5) {
            matrices.rotateDegrees(com.mojang.math.Axis.XP, -tipTilt.floatValue());
        }

        switch (mode) {
            case MODE_1 -> {
                matrices.rotateDegrees(com.mojang.math.Axis.YP, armX * 90.0f);
                matrices.rotateDegrees(com.mojang.math.Axis.ZP, armX * -70.0f);
                matrices.rotateDegrees(com.mojang.math.Axis.XP, -rotateAngle.floatValue() - (power * anim));
            }
            case MODE_2 -> {
                matrices.rotateDegrees(com.mojang.math.Axis.YP, armX * 90.0f);
                matrices.rotateDegrees(com.mojang.math.Axis.ZP, armX * -65.0f);
                matrices.rotateDegrees(com.mojang.math.Axis.XP, -65.0f + (power * anim));
            }
            case MODE_3 -> {
                matrices.rotateDegrees(com.mojang.math.Axis.YP, armX * -90.0f);
                matrices.rotateDegrees(com.mojang.math.Axis.ZP, armX * 60.0f);
                matrices.rotateDegrees(com.mojang.math.Axis.XP, 30.0f);
                matrices.rotateDegrees(com.mojang.math.Axis.ZP, armX * power * anim);
            }
            case MODE_4 -> {
                matrices.rotateDegrees(com.mojang.math.Axis.YP, armX * 90.0f);
                matrices.rotateDegrees(com.mojang.math.Axis.ZP, armX * -75.0f);
                matrices.rotateDegrees(com.mojang.math.Axis.XP, -45.0f - (power * anim));
                matrices.rotateDegrees(com.mojang.math.Axis.YP, armX * power * anim * 0.5f);
            }
            case MODE_5 -> {
                float strength = power / 80.0f;
                float swing = anim * anim;
                float twist = (float) Math.sin(((double) (swingProgress * swingProgress)) * Math.PI);
                matrices.rotateDegrees(com.mojang.math.Axis.YP, armX * (45.0f + (twist * -20.0f * strength)));
                matrices.rotateDegrees(com.mojang.math.Axis.ZP, armX * swing * -22.0f * strength);
                matrices.rotateDegrees(com.mojang.math.Axis.XP, swing * -85.0f * strength);
                matrices.rotateDegrees(com.mojang.math.Axis.YP, armX * -45.0f);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public final ModeSetting mode = add(new ModeSetting("Режим", "3D", "3D", "Punchly", "Мод 1", "Мод 2", "Мод 3", "Мод 4", "Мод 5", "360", "Классический"));
    public final BooleanSetting leftArm = add(new BooleanSetting("Левая рука", false))
        .visibleWhen(() -> mode.is("3D"));
    public final NumberSetting speed = add(new NumberSetting("Скорость", 7, 0, 10, 1))
        .visibleWhen(() -> mode.is("3D"));
    private final Swing3DAnimation animation3D = new Swing3DAnimation();
    private String previousMode = "3D";

    private final BooleanSetting prettyHands = add(new BooleanSetting("Pretty Hands Mod", true))
        .visibleWhen(() -> mode.is("Классический"));

    private final NumberSetting itemX = add(new NumberSetting("Item X", 0.0, -0.5, 0.5, 0.005))
        .visibleWhen(this::pretty);
    private final NumberSetting itemY = add(new NumberSetting("Item Y", 0.0, -0.5, 0.5, 0.005))
        .visibleWhen(this::pretty);
    private final NumberSetting itemZ = add(new NumberSetting("Item Z", 0.0, -0.5, 0.5, 0.005))
        .visibleWhen(this::pretty);
    private final NumberSetting itemSize = add(new NumberSetting("Item Size", 1.0, 0.1, 3.0, 0.05))
        .visibleWhen(this::pretty);

    private final BooleanSetting swing = add(new BooleanSetting("Swing Animation", true))
        .visibleWhen(() -> !mode.is("3D") && !pretty());
    private final ModeSetting style = add(new ModeSetting("Swing Style", "Smooth",
        "Smooth", "Static", "Down", "DropDown", "Poke", "SelfBack",
        "Feast", "ToBack", "Block", "Akrien", "Break", "Pander", "Slant"))
        .visibleWhen(()->mode.is("Классический")&&swinging());
    private final NumberSetting strength = add(new NumberSetting("Animation Strength", 1.0, 0.1, 3.0, 0.05))
        .visibleWhen(() -> swinging() && !style.is("Pander"));
    private final NumberSetting dropAngle = add(new NumberSetting("DropDown Angle", 12.0, 1.0, 360.0, 1.0))
        .visibleWhen(() -> swinging() && style.is("DropDown"));
    private final NumberSetting dropTilt = add(new NumberSetting("DropDown Tilt", 12.0, 1.0, 360.0, 1.0))
        .visibleWhen(() -> swinging() && style.is("DropDown"));
    private final BooleanSetting onlyAura = add(new BooleanSetting("Only With Aura", false))
        .visibleWhen(() -> !mode.is("3D") && !pretty());
    private final BooleanSetting swapHands = add(new BooleanSetting("Hand Swap", false))
        .visibleWhen(() -> !mode.is("3D") && !pretty());

    public SwingAnimations() {
        instance = this;
        prettyHands.onChanged(PunchyBridge::requestSync);
    }

    public static SwingAnimations getInstance() { return instance; }

    public static boolean wantsPunchy() { return instance != null && instance.enabled() && instance.pretty(); }

    public static boolean is3DActive() { return instance != null && instance.enabled() && instance.mode.is("3D"); }

    public Swing3DAnimation animation3D() { return animation3D; }

    public static boolean modifiesSwingDuration() { return is3DActive() || instance != null && instance.deltaActive(); }
    public static int swingDuration(int vanillaTicks) {
        return modifiesSwingDuration() ? Math.max(1, Math.round(vanillaTicks * instance.speed.floatValue() / 7f)) : vanillaTicks;
    }

    private boolean pretty() { return mode.is("Punchly") || mode.is("Классический") && prettyHands.enabled(); }

    private boolean swinging() { return deltaActive() || mode.is("Классический") && !pretty() && swing.enabled(); }

    @Override
    protected void onEnable() { animation3D.reset(); PunchyBridge.requestSync(); }

    @Override
    protected void onDisable() { animation3D.reset(); PunchyBridge.requestSync(); }

    @Override
    public void tick(Minecraft client) {
        // Keep Setting's profile-save listener; profiles can also change modes silently.
        if (!previousMode.equals(mode.get())) {
            previousMode = mode.get();
            animation3D.reset();
            PunchyBridge.requestSync();
        }
    }

    private static ItemStack lastItem = ItemStack.EMPTY;
    private static long swapTime;

    public static float equipOffset(float equip) {
        var player = Minecraft.getInstance().player;
        if (player == null) return equip;
        ItemStack held = player.getMainHandItem();
        long now = System.currentTimeMillis();
        if (!ItemStack.isSameItem(held, lastItem)) {
            lastItem = held.copy();
            swapTime = now;
        }
        return now - swapTime < 350 ? equip : 0.0f;
    }

    public static boolean shouldSwing(InteractionHand hand) {
        SwingAnimations module = instance;
        if (module == null || !module.enabled() || !module.swinging() || hand != InteractionHand.MAIN_HAND) return false;
        var player = Minecraft.getInstance().player;
        if (player == null || player.isUsingItem() && player.getUsedItemHand() == hand) return false;
        if (player.getMainHandItem().isEmpty()) return false;
        if (module.onlyAura.enabled()) {
            return morfeov.coliseum.port.CombatHooks.target()!=null;
        }
        return true;
    }

    public static int applySwing(PoseStack poseStack, InteractionHand hand, float progress) {
        if (!shouldSwing(hand)) return 0;
        SwingAnimations module = instance;
        var player = Minecraft.getInstance().player;
        HumanoidArm arm = player.getMainArm();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (module.swapHands.enabled()) arm = arm.getOpposite();
        if(module.deltaActive()) { poseStack.translate(side*.56f,-.52f,-.72f); module.applyArmAnimation(poseStack,arm,progress,0); return side; }
        SwingTransforms.apply(poseStack, arm, progress, module.style.get(), module.strength.floatValue(),
            module.dropAngle.floatValue(), module.dropTilt.floatValue());
        return side;
    }

    public void applyPunchyItemTranslate(PoseStack poseStack, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        float x = itemX.floatValue();
        float y = itemY.floatValue();
        float z = itemZ.floatValue();
        if (x == 0.0f && y == 0.0f && z == 0.0f) return;
        poseStack.translate(-x, -y, z);
    }

    public void applyPunchyItemScale(PoseStack poseStack, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        float scale = itemSize.floatValue();
        if (Math.abs(scale - 1.0f) < 1.0E-4f) return;
        poseStack.scale(scale, scale, scale);
    }
}
