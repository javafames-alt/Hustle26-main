package im.yaHustler.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import im.yaHustler.font.AtlasFont;
import im.yaHustler.hud.HudStyle;
import im.yaHustler.mixin.TagsGameRendererInvoker;
import im.yaHustler.module.Category;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.impl.themes.Theme;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.ColorSetting;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.ui.BackdropStyle;
import im.yaHustler.ui.Draw;
import im.yaHustler.ui.FigmaBackdropBlur;
import im.yaHustler.ui.GuiVisibility;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@ModuleInfo(id = "tags", name = "Tags", category = Category.RENDER,
    description = "Hustle 26.3 Tags: ники, HP, дистанция, предметы, броня и руки")
public final class Tags extends im.yaHustler.module.Module implements AutoCloseable {
    public final NumberSetting scale = add(new NumberSetting("Масштаб", 1, .5, 3, .05));
    public final NumberSetting cornerRadius = add(new NumberSetting("Скругление", 5.5, 0, 6, .5));
    public final BooleanSetting distanceScaling = add(new BooleanSetting("Уменьшать вдали", true));
    public final BooleanSetting players = add(new BooleanSetting("Отображать: Игроков", true));
    public final BooleanSetting friends = add(new BooleanSetting("Отображать: Друзей", true));
    public final BooleanSetting self = add(new BooleanSetting("Отображать: Себя", true));
    public final BooleanSetting names = add(new BooleanSetting("Элементы: Ники", true));
    public final BooleanSetting items = add(new BooleanSetting("Элементы: Предметы", true));
    public final BooleanSetting armor = add(new BooleanSetting("Элементы: Броню", false));
    public final BooleanSetting hands = add(new BooleanSetting("Элементы: Руки", true));
    public final BooleanSetting enchantments = add(new BooleanSetting("Чары", false));
    public final BooleanSetting blur = add(new BooleanSetting("Блюр", true));
    public final BooleanSetting blurTheme = add(new BooleanSetting("BlurTheme", false).visibleWhen(blur::enabled));
    public final BooleanSetting box = add(new BooleanSetting("Бокс", false));
    public final BooleanSetting customBoxColor = add(new BooleanSetting("Кастомный цвет бокса", false).visibleWhen(box::enabled));
    public final ColorSetting boxColor = add(new ColorSetting("Цвет бокса", 0xffa894ff).visibleWhen(() -> box.enabled() && customBoxColor.enabled()));
    private final EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final List<float[]> surfaces = new ArrayList<>();
    private final AtlasFont font = new AtlasFont("hustle263_semi", true);
    private final FigmaBackdropBlur backdrop = new FigmaBackdropBlur();
    private final Matrix4f projection = new Matrix4f();
    private final Vector4f clip = new Vector4f();
    private final Vector2f projected = new Vector2f();

    public boolean shows(Avatar avatar) {
        var mc = Minecraft.getInstance();
        if (!enabled() || mc.player == null || mc.level == null || mc.gui.hud.isHidden() || GuiVisibility.hidesHud()
            || !(avatar instanceof Player player) || !player.isAlive()) return false;
        if (player == mc.player) return self.enabled() && !mc.options.getCameraType().isFirstPerson();
        return mc.player.isAlliedTo(player) ? friends.enabled() : players.enabled();
    }

    public boolean replacesName(Avatar player) { return names.enabled() && shows(player); }

    public synchronized void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        surfaces.clear();
        var mc = Minecraft.getInstance();
        if (!enabled() || mc.player == null || mc.level == null || mc.gui.hud.isHidden() || GuiVisibility.hidesHud()) return;
        var state = mc.gameRenderer.gameRenderState();
        var camera = state.levelRenderState.cameraRenderState;
        var bob = new PoseStack();
        var renderer = (TagsGameRendererInvoker) mc.gameRenderer;
        renderer.hustle263$tagsHurt(camera, bob);
        if (state.optionsRenderState.bobView) renderer.hustle263$tagsBob(camera, bob);
        projection.set(camera.projectionMatrix).mul(bob.last().pose()).mul(camera.viewRotationMatrix);
        if (!projection.isFinite()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        var visible = new ArrayList<Entity>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player || items.enabled() && (entity instanceof ItemEntity || entity instanceof Projectile)) visible.add(entity);
        }
        visible.sort(Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(camera.pos)).reversed());
        for (Entity entity : visible) {
            if (entity instanceof Player player) {
                if (shows(player)) drawPlayer(g, mc, player, partial, camera.pos);
            } else drawDropped(g, mc, entity, partial, camera.pos);
        }
    }

    private boolean project(Minecraft mc, Vec3 point, Vec3 camera) {
        clip.set((float)(point.x - camera.x), (float)(point.y - camera.y), (float)(point.z - camera.z), 1);
        projection.transform(clip);
        if (!clip.isFinite() || clip.w <= .001f || clip.z < -clip.w || clip.z > clip.w) return false;
        projected.set((clip.x / clip.w + 1) * mc.getWindow().getGuiScaledWidth() / 2f,
            (1 - clip.y / clip.w) * mc.getWindow().getGuiScaledHeight() / 2f);
        return true;
    }

    private void drawPlayer(GuiGraphicsExtractor g, Minecraft mc, Player player, float partial, Vec3 camera) {
        Vec3 position = player.getPosition(partial);
        float distance = (float)camera.distanceTo(position);
        float size = scale.floatValue() * (distanceScaling.enabled()
            ? Math.max(.3f, 1 - distance * .008f) * Math.max(.7f, 1 - distance * .005f) : 1);
        if (box.enabled()) drawBox(g, mc, player, position, camera);
        if (hands.enabled() && project(mc, position, camera)) drawHands(g, player, projected.x, projected.y + 2, size);
        if (!names.enabled() || !project(mc, position.add(0, player.getBbHeight() + .3, 0), camera)) return;
        String name = player.getGameProfile().name();
        boolean friend = im.yaHustler.friend.Friends.contains(name);
        name = im.yaHustler.module.impl.misc.NameProtect.protect(name);
        if (friend) name = "[F] " + name;
        Component prefix = player.getTeam() == null ? Component.empty() : player.getTeam().getPlayerPrefix();
        String prefixText = prefix.getString().strip();
        int hp = health(mc, player);
        String health = hp == 1000 ? "?" : Integer.toString(hp);
        float absorption = Math.max(0, player.getAbsorptionAmount());
        int hearts = (int)Math.ceil(absorption / 2);
        float prefixWidth = prefixText.isEmpty() ? 0 : width(prefixText, 6) + 3;
        float tagWidth = 14 + prefixWidth + width(name, 6) + 5 + width("[" + health + "]", 6)
            + (hearts > 0 ? width("/", 6) + 1 + hearts * 6 : 0);
        float top = -9 - Math.min(6, distance * .08f);
        g.pose().pushMatrix();
        g.pose().translate(projected.x, projected.y);
        g.pose().scale(size, size);
        float left = -tagWidth / 2, cursor = left + 7, textY = top + 9 - im.yaHustler.font.FigmaFont.height(6f / 11) / 2;
        panel(g, left, top, tagWidth, 18);
        if (friend) Draw.roundedF(g, left, top, tagWidth, 18, Math.min(cornerRadius.floatValue(),9),0x8c155c32);
        if (!prefixText.isEmpty()) {
            styled(g, prefix, cursor, textY, 6, 0xfff1f1f1);
            cursor += prefixWidth;
        }
        text(g, name, cursor, textY, 6, 0xfff1f1f1);
        cursor += width(name, 6) + 5;
        cursor = segment(g, "[", cursor, textY, 0xffa0a0a0);
        cursor = segment(g, health, cursor, textY, 0xffff4646);
        if (hearts > 0) {
            cursor = segment(g, "/", cursor, textY, 0xffa0a0a0) + 1;
            for (int i = 0; i < hearts; i++) {
                String sprite = absorption - i * 2 <= 1 ? "absorbing_half" : "absorbing_full";
                g.pose().pushMatrix();
                g.pose().translate(cursor, textY);
                g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    net.minecraft.resources.Identifier.withDefaultNamespace("hud/heart/" + sprite), 0, 0, 6, 6);
                g.pose().popMatrix();
                cursor += 6;
            }
        }
        segment(g, "]", cursor, textY, 0xffa0a0a0);
        drawEquipment(g, player, left + 2, top - 10.5f);
        g.pose().popMatrix();
    }

    private void drawHands(GuiGraphicsExtractor g, Player player, float x, float y, float size) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size, size);
        float top = 1;
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.isEmpty()) continue;
            String count = stack.getCount() > 1 ? " " + stack.getCount() + "x" : "";
            float nameWidth = width(stack.getHoverName().getString(), 6), totalWidth = nameWidth + width(count, 6);
            panel(g, -totalWidth / 2 - 7, top, totalWidth + 14, 18);
            float textY=top+9-im.yaHustler.font.FigmaFont.height(6f/11)/2;
            styled(g, stack.getHoverName(), -totalWidth / 2, textY, 6, 0xfff1f1f1);
            text(g, count, -totalWidth / 2 + nameWidth, textY, 6, 0xfff1f1f1);
            top += 20;
        }
        g.pose().popMatrix();
    }

    private void drawEquipment(GuiGraphicsExtractor g, Player player, float x, float y) {
        var stacks = new ArrayList<ItemStack>(6);
        ItemStack main = player.getMainHandItem();
        if (armor.enabled() || specialWeapon(main)) if (!main.isEmpty()) stacks.add(main);
        for (EquipmentSlot slot : armorSlots) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && (armor.enabled() || specialArmor(stack))) stacks.add(stack);
        }
        if (armor.enabled() && !player.getOffhandItem().isEmpty()) stacks.add(player.getOffhandItem());
        float cursor = armor.enabled() ? x : -(stacks.size() * 8 + Math.max(0, stacks.size() - 1) * 2) / 2f;
        for (ItemStack stack : stacks) {
            drawItem(g, stack, cursor, y, armor.enabled());
            if (enchantments.enabled()) drawEnchantments(g, stack, cursor, y);
            else drawSpecialEnchantment(g, stack, cursor, y);
            cursor += 10;
        }
    }

    private void drawItem(GuiGraphicsExtractor g, ItemStack original, float x, float y, boolean decorated) {
        ItemStack stack = original.copy();
        stack.remove(DataComponents.ENCHANTMENTS);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(.5f, .5f);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
        if (!decorated) return;
        if (original.getCount() > 1) {
            String count = Integer.toString(original.getCount());
            text(g, count, x + 8 - width(count, 4), y + 4, 4, 0xfff1f1f1);
        }
        if (original.isDamageableItem() && original.getDamageValue() > 0) {
            float remaining = 1 - original.getDamageValue() / (float)original.getMaxDamage();
            Draw.fillF(g, x + .5f, y + 7, 7, 1, 0xff080808);
            Draw.fillF(g, x + .5f, y + 7, 7 * remaining, .5f, remaining < .2f ? 0xffff4646 : 0xff70ff70);
        }
    }

    private void drawEnchantments(GuiGraphicsExtractor g, ItemStack stack, float x, float y) {
        var entries = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet();
        int line = entries.size();
        for (var entry : entries) {
            String name = entry.getKey().value().description().getString();
            String label = name.substring(0, name.offsetByCodePoints(0, Math.min(2, name.codePointCount(0, name.length())))) + entry.getIntValue();
            float size = Math.min(4, 10 / Math.max(1, width(label, 1)));
            text(g, label, x + 4 - width(label, size) / 2, y - line-- * 5.5f, size, 0xfff1f1f1);
        }
    }

    private int enchantment(ItemStack stack, String id) {
        for (var entry : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            if (entry.getKey().unwrapKey().map(key -> key.identifier().getPath().equals(id)).orElse(false)) return entry.getIntValue();
        }
        return 0;
    }

    private boolean specialMace(ItemStack stack) { return stack.is(Items.MACE) && enchantment(stack, "breach") == 3 && enchantment(stack, "density") == 5; }
    private boolean specialWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) && (enchantment(stack, "sharpness") == 8 || enchantment(stack, "knockback") > 0) || specialMace(stack);
    }
    private boolean specialArmor(ItemStack stack) { return enchantment(stack, "protection") == 5 && enchantment(stack, "mending") > 0; }

    private void drawSpecialEnchantment(GuiGraphicsExtractor g, ItemStack stack, float x, float y) {
        String label;
        int level = 0;
        if (stack.is(ItemTags.SWORDS) && enchantment(stack, "sharpness") == 8 || specialMace(stack)) label = "Кр";
        else if (stack.is(ItemTags.SWORDS) && (level = enchantment(stack, "knockback")) > 0) label = "От";
        else if (specialArmor(stack)) { label = "По"; level = enchantment(stack, "mending"); }
        else return;
        String number = level > 0 ? Integer.toString(level) : "";
        float left = x + 4 - width(label + number, 4.5f) / 2;
        text(g, label, left, y - 5.5f, 4.5f, 0xfff1f1f1);
        text(g, number, left + width(label, 4.5f), y - 5.5f, 4.5f, 0xffff4646);
    }

    private void drawDropped(GuiGraphicsExtractor g, Minecraft mc, Entity entity, float partial, Vec3 camera) {
        ItemStack stack = entity instanceof ItemEntity item ? item.getItem()
            : entity instanceof ItemSupplier supplier ? supplier.getItem()
            : entity instanceof AbstractArrow arrow ? arrow.getPickupItemStackOrigin()
            : entity instanceof AbstractWindCharge ? Items.WIND_CHARGE.getDefaultInstance() : ItemStack.EMPTY;
        if (stack.isEmpty()) return;
        Vec3 position = entity.getPosition(partial);
        if (!project(mc, position.add(0, .25, 0), camera)) return;
        float size = scale.floatValue() * (distanceScaling.enabled()
            ? Math.max(.65f, 1.1f - (float)camera.distanceTo(position) * .004f) : 1);
        float tagWidth = 12 + width(stack.getHoverName().getString(), 6);
        g.pose().pushMatrix();
        g.pose().translate(projected.x, projected.y);
        g.pose().scale(size, size);
        panel(g, -tagWidth / 2, -16, tagWidth, 16);
        styled(g, stack.getHoverName(), -tagWidth / 2 + 6, -8-im.yaHustler.font.FigmaFont.height(6f/11)/2, 6, 0xfff1f1f1);
        g.pose().popMatrix();
    }

    private void drawBox(GuiGraphicsExtractor g, Minecraft mc, Player player, Vec3 position, Vec3 camera) {
        var bounds = player.getBoundingBox().move(position.subtract(player.position()));
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            if (!project(mc, new Vec3((corner & 1) == 0 ? bounds.minX : bounds.maxX,
                (corner & 2) == 0 ? bounds.minY : bounds.maxY, (corner & 4) == 0 ? bounds.minZ : bounds.maxZ), camera)) return;
            left = Math.min(left, projected.x); right = Math.max(right, projected.x);
            top = Math.min(top, projected.y); bottom = Math.max(bottom, projected.y);
        }
        int color = 0xff000000 | (customBoxColor.enabled() ? boxColor.color() : Theme.accent()) & 0xffffff;
        Draw.fillF(g, left - .5f, top - .5f, right - left + 1, 1.75f, 0xff000000);
        Draw.fillF(g, left - .5f, bottom - 1.25f, right - left + 1, 1.75f, 0xff000000);
        Draw.fillF(g, left - .5f, top, 1.75f, bottom - top, 0xff000000);
        Draw.fillF(g, right - 1.25f, top, 1.75f, bottom - top, 0xff000000);
        Draw.fillF(g, left, top, right - left, .75f, color);
        Draw.fillF(g, left, bottom - .75f, right - left, .75f, color);
        Draw.fillF(g, left, top, .75f, bottom - top, color);
        Draw.fillF(g, right - .75f, top, .75f, bottom - top, color);
    }

    private float width(String value, float size) { return font.widthAt(value, size); }
    private int health(Minecraft mc, Player player) {
        var scoreboard = mc.level.getScoreboard();
        for (var objective : scoreboard.getObjectives()) {
            var score = scoreboard.getPlayerScoreInfo(player, objective);
            if (score != null) return score.value();
        }
        return Math.round(Math.max(0, player.getHealth()));
    }
    private void text(GuiGraphicsExtractor g, String value, float x, float y, float size, int color) { font.draw(g, value, x, y, color, size / AtlasFont.BASE); }
    private float segment(GuiGraphicsExtractor g, String value, float x, float y, int color) {
        text(g, value, x, y, 6, color);
        return x + width(value, 6);
    }
    private void styled(GuiGraphicsExtractor g, Component value, float x, float y, float size, int color) {
        float[] cursor = {x};
        value.visit((style, part) -> {
            int tint = style.getColor() == null || style.getColor().getValue() == 0xffffff ? color : color & 0xff000000 | style.getColor().getValue();
            text(g, part, cursor[0], y, size, tint);
            cursor[0] += width(part, size);
            return Optional.empty();
        }, Style.EMPTY);
    }
    private void panel(GuiGraphicsExtractor g, float x, float y, float width, float height) {
        float radius = Math.min(cornerRadius.floatValue(), Math.min(width, height) / 2);
        if (blur.enabled()) surfaces.add(BackdropStyle.transformedSurface(x, y, width, height, radius, g.pose()));
        Draw.roundedF(g, x, y, width, height, radius, HudStyle.panelFill(blur.enabled(), 1, blur.enabled() && blurTheme.enabled()));
    }
    public synchronized void renderBackdrop(Minecraft mc) {
        if (enabled() && blur.enabled() && mc.level != null && !mc.gui.hud.isHidden() && !GuiVisibility.hidesHud()) {
            for (int from = 0; from < surfaces.size(); from += BackdropStyle.LIMIT) {
                float[][] batch = surfaces.subList(from, Math.min(from + BackdropStyle.LIMIT, surfaces.size())).toArray(float[][]::new);
                backdrop.render(mc.gameRenderer.mainRenderTarget(), mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight(), batch, cornerRadius.floatValue(), HudStyle.blurStrength(), blurTheme.enabled() ? .12f : 0);
            }
        }
        surfaces.clear();
    }
    @Override protected void onDisable() { close(); }
    @Override public synchronized void close() { surfaces.clear(); backdrop.close(); }
}
