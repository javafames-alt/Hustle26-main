package im.yaHustler.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import im.yaHustler.cosmetics.LocalCosmetics;
import im.yaHustler.cosmetics.WeaponCosmetics;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.skin.SkinManager;
import im.yaHustler.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static im.yaHustler.ui.CosmeticsLayout.Rect;

/** Embedded wardrobe. Shared layout bounds keep tabs, clipped cards and drag controls in sync. */
final class GuiCosmeticsPage {
    private static final String[] LABELS = {"Все", "Крылья", "Спина", "Питомцы", "Шляпы", "Плащи", "Оружие", "Скины"};
    private static final String[] TYPES = {"", "wings", "bodywear", "pet", "hat", "cape", "weapon", "skin"};
    private static final String[] ICONS = {"user", "visuals", "frame", "user", "mc", "frame", "combat", "player"};
    private static final float MIN_ZOOM = .5f, MAX_ZOOM = 1.5f;
    private static final int TEXT = 0xffeaf0ec, MUTED = 0xffafbeb4;
    private record Hit(int index, Rect bounds) {}
    private final List<Hit> hits = new ArrayList<>();
    private CosmeticsLayout layout;
    private int category, scroll, maxScroll, shownCount, accent;
    private float yaw = 25, pitch, zoom = 1, scrollGrab;
    private boolean rotating, autoRotate, draggingZoom, draggingScroll, equippedOnly;
    private long lastFrame;
    private String query = "";

    void layout(int x, int y, int w, int h) { layout = new CosmeticsLayout(x, y, w, h); }

    void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, String query, int mx, int my) {
        layout(x, y, w, h);
        accent = FigmaGuiRenderer.accent();
        if (!this.query.equals(query)) { scroll = 0; hits.clear(); this.query = query; }
        List<Integer> items = shownItems(query);
        shownCount = items.size();
        maxScroll = layout.maxScroll(shownCount);
        scroll = Math.clamp(scroll, 0, maxScroll);
        drawHeader(g);
        drawCategories(g, mx, my);
        drawCards(g, items, mx, my);
        drawFooter(g, mx, my);
        drawPreview(g, mx, my);
    }

    private List<Integer> shownItems(String query) {
        List<Integer> result = new ArrayList<>();
        String needle = query.strip().toLowerCase(Locale.ROOT);
        if (category == 7) {
            var skins = SkinManager.entries();
            for (int i = 0; i < skins.size(); i++)
                if (skins.get(i).name().toLowerCase(Locale.ROOT).contains(needle)) result.add(i);
        } else {
            for (int i = 0; i < LocalCosmetics.size(); i++)
                if (WeaponCosmetics.matchesCategory(TYPES[category], LocalCosmetics.type(i))
                        && (!equippedOnly || LocalCosmetics.isSelected(i))
                        && LocalCosmetics.name(i).toLowerCase(Locale.ROOT).contains(needle)) result.add(i);
        }
        return result;
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        Rect catalog = layout.catalog;
        FigmaFont.drawMedium(g, category == 7 ? "Ваши скины" : "Гардероб", catalog.x() + 1, catalog.y() + 1, TEXT, 9f / 11);
        FigmaFont.drawRegular(g, category == 7 ? "Локальные PNG-скины" : "Соберите свой образ", catalog.x() + 1, catalog.y() + 13, MUTED, 6f / 11);
        String count = Integer.toString(shownCount);
        float width = Math.max(19, FigmaFont.width(count, 6f / 11) + 12);
        Draw.roundedF(g, catalog.right() - width, catalog.y() + 1, width, 15, 4, ColorUtil.alpha(accent, 32));
        FigmaFont.draw(g, count, catalog.right() - width / 2 - FigmaFont.width(count, 6f / 11) / 2, catalog.y() + 6, accent, 6f / 11);
    }

    private void drawCategories(GuiGraphicsExtractor g, int mx, int my) {
        for (int i = 0; i < LABELS.length; i++) {
            Rect box = layout.category(i);
            boolean selected = category == i, hot = box.contains(mx, my);
            Draw.rounded(g, box.x(), box.y(), box.w(), box.h(), 4,
                selected ? ColorUtil.alpha(accent, 42) : hot ? 0x20ffffff : 0x28121c16);
            if (selected) Draw.roundedF(g, box.x() + 6, box.bottom() - 2, box.w() - 12, 1, .5f, ColorUtil.alpha(accent, 190));
            InterfaceAssets.centeredIcon(g, ICONS[i], box.x() + 8, box.centerY(), 6, selected ? accent : MUTED);
            float font = 6.2f / 11;
            FigmaFont.draw(g, FigmaFont.fit(LABELS[i], box.w() - 20, font), box.x() + 15,
                box.centerY() - FigmaFont.mediumHeight(font) / 2, selected ? TEXT : MUTED, font);
        }
    }

    private void drawCards(GuiGraphicsExtractor g, List<Integer> items, int mx, int my) {
        hits.clear();
        Rect grid = layout.grid;
        g.enableScissor(grid.x(), grid.y(), grid.right(), grid.bottom());
        try {
            for (int row = 0; row < items.size(); row++) {
                Rect card = layout.card(row, scroll), visible = card.intersect(grid);
                if (visible.h() == 0) continue;
                int index = items.get(row);
                boolean selected = category == 7 ? SkinManager.selectedEntry() == SkinManager.entries().get(index) : LocalCosmetics.isSelected(index);
                boolean hot = visible.contains(mx, my);
                int body = ColorUtil.alpha(ColorUtil.lerp(0xff18231c, accent, selected ? .19f : .025f), hot ? 138 : 104);
                Draw.rounded(g, card.x(), card.y(), card.w(), card.h(), 6, body);
                Draw.rounded(g, card.x() + 5, card.y() + 5, card.w() - 10, 41, 4, 0x17101a13);
                String name;
                if (category == 7) {
                    var skin = SkinManager.entries().get(index);
                    drawSkinFace(g, skin.texture(), skin.size(), card);
                    name = skin.name();
                } else {
                    if (WeaponCosmetics.isWeapon(index)) WeaponCosmetics.drawPreview(g, index, card.x() + (card.w() - 38) / 2f, card.y() + 6, 38);
                    else CosmeticArtwork.draw(g, LocalCosmetics.icon(index), card.x() + 7, card.y() + 6, card.w() - 14, 39, 0xffffffff);
                    name = LocalCosmetics.name(index).replace(" [Hustle 26.3]", "");
                }
                drawCardName(g, name, card);
                if (selected) {
                    Draw.circle(g, card.right() - 8, card.y() + 8, 4, accent);
                    InterfaceAssets.centeredIcon(g, "checkmark", card.right() - 8, card.y() + 8, 5, 0xffffffff);
                }
                hits.add(new Hit(index, visible));
            }
            if (items.isEmpty()) {
                String title = category == 7 ? "Пока нет скинов" : "Ничего не найдено";
                FigmaFont.drawMedium(g, title, grid.x() + 8, grid.y() + 28, TEXT, 7.5f / 11);
                String help = category == 7 ? "Добавьте PNG через кнопку «Папка»" : equippedOnly ? "Выберите аксессуары во вкладке «Все»" : "Попробуйте другую категорию или поиск";
                FigmaFont.drawRegular(g, FigmaFont.fitRegular(help, grid.w() - 16, 6f / 11), grid.x() + 8, grid.y() + 43, MUTED, 6f / 11);
            }
        } finally { g.disableScissor(); }
        if (maxScroll > 0) {
            Rect bar = scrollbarThumb();
            Draw.rounded(g, layout.scrollbar.x(), grid.y(), 2, grid.h(), 1, 0x14ffffff);
            Draw.rounded(g, bar.x(), bar.y(), bar.w(), bar.h(), 1, ColorUtil.alpha(accent, draggingScroll ? 220 : 115));
        }
    }

    private void drawCardName(GuiGraphicsExtractor g, String name, Rect card) {
        float font = 6.3f / 11;
        int width = card.w() - 12, split = -1;
        if (FigmaFont.width(name, font) > width) {
            for (int i = 0; i < name.length(); i++)
                if (name.charAt(i) == ' ' && FigmaFont.width(name.substring(0, i), font) <= width) split = i;
        }
        String first = split > 0 ? name.substring(0, split) : name;
        String second = split > 0 ? name.substring(split + 1).strip() : "";
        FigmaFont.draw(g, FigmaFont.fit(first, width, font), card.x() + 6, card.y() + (second.isEmpty() ? 56 : 51), TEXT, font);
        if (!second.isEmpty()) FigmaFont.drawRegular(g, FigmaFont.fitRegular(second, width, 6f / 11), card.x() + 6, card.y() + 61, MUTED, 6f / 11);
    }

    private void drawSkinFace(GuiGraphicsExtractor g, net.minecraft.resources.Identifier texture, int size, Rect card) {
        g.pose().pushMatrix();
        try {
            g.pose().translate(card.centerX() - 15, card.y() + 10);
            g.pose().scale(30f / 8, 30f / 8);
            int unit = size / 64;
            for (int u : new int[]{8, 40}) im.yaHustler.ui.GuiTextures.blit(g, net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture,
                0, 0, u * unit, 8 * unit, 8, 8, 8 * unit, 8 * unit, size, size, GuiOpacity.color(0xffffffff));
        } finally { g.pose().popMatrix(); }
    }

    private void drawFooter(GuiGraphicsExtractor g, int mx, int my) {
        if (category == 7) {
            button(g, layout.skinAction(0), "Папка", "config", false, mx, my);
            button(g, layout.skinAction(1), "Обновить", "reload", false, mx, my);
            button(g, layout.skinAction(2), "Сбросить", "x", false, mx, my);
        } else {
            button(g, layout.clear, "Снять всё", "x", false, mx, my);
            button(g, layout.equipped, "Надетые", "checkmark", equippedOnly, mx, my);
        }
    }

    private void drawPreview(GuiGraphicsExtractor g, int mx, int my) {
        Rect box = layout.preview;
        Draw.rounded(g, box.x(), box.y(), box.w(), box.h(), 7, 0x6517201a);
        FigmaFont.drawMedium(g, "Ваш образ", box.x() + 9, box.y() + 10, TEXT, 7.7f / 11);
        String count = Integer.toString(LocalCosmetics.selectedIndices().size());
        FigmaFont.draw(g, count, box.right() - 10 - FigmaFont.width(count, 6.5f / 11), box.y() + 11, accent, 6.5f / 11);
        Draw.fillF(g, box.x() + 9, box.y() + 24, box.w() - 18, .5f, 0x14ffffff);
        long now = System.nanoTime();
        if (autoRotate && lastFrame != 0) yaw += Math.min(.05f, (now - lastFrame) / 1e9f) * 22;
        lastFrame = now;
        Rect model = layout.model;
        preview(g, model.x(), model.y(), model.right(), model.bottom());
        String hint = "ЛКМ: повернуть / колесо: зум";
        FigmaFont.drawRegular(g, hint, box.centerX() - FigmaFont.width(hint, 4.8f / 11) / 2, model.bottom() + 5, MUTED, 4.8f / 11);
        button(g, layout.layers, "3D-слои скина", "player", SkinManager.layers3d(), mx, my);
        button(g, layout.zoomOut, "-", null, false, mx, my);
        button(g, layout.zoomIn, "+", null, false, mx, my);
        Rect track = layout.zoomTrack;
        float progress = (zoom - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM);
        Draw.roundedF(g, track.x(), track.centerY() - 1, track.w(), 2, 1, 0x2cffffff);
        Draw.roundedF(g, track.x(), track.centerY() - 1, track.w() * progress, 2, 1, accent);
        Draw.circle(g, track.x() + track.w() * progress, track.centerY(), 2.3f, 0xfff3f7f4);
        button(g, layout.autoRotate, "Авто", "activity", autoRotate, mx, my);
        button(g, layout.resetView, "Сброс", null, false, mx, my);
    }

    private void button(GuiGraphicsExtractor g, Rect box, String text, String icon, boolean selected, int mx, int my) {
        Draw.rounded(g, box.x(), box.y(), box.w(), box.h(), 4,
            selected ? ColorUtil.alpha(accent, 50) : box.contains(mx, my) ? 0x25ffffff : 0x31101a14);
        float font = 6.3f / 11, textWidth = FigmaFont.width(text, font), groupWidth = textWidth + (icon == null ? 0 : 11);
        float left = box.centerX() - groupWidth / 2;
        if (icon != null) InterfaceAssets.centeredIcon(g, icon, left + 3, box.centerY(), 6, selected ? accent : MUTED);
        FigmaFont.draw(g, text, left + (icon == null ? 0 : 11), box.centerY() - FigmaFont.mediumHeight(font) / 2, selected ? accent : TEXT, font);
    }

    private void preview(GuiGraphicsExtractor g,int left,int top,int right,int bottom){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
        state.shadowPieces.clear();state.outlineColor=0;
        if(state instanceof AvatarRenderState avatar){
            avatar.boundingBoxWidth/=avatar.scale;avatar.boundingBoxHeight/=avatar.scale;avatar.scale=1;
            avatar.bodyRot=180+yaw;avatar.yRot=0;avatar.xRot=pitch;
            avatar.pose=Pose.STANDING;avatar.isCrouching=false;avatar.isFallFlying=false;avatar.isVisuallySwimming=false;
            avatar.walkAnimationPos=avatar.walkAnimationSpeed=0;
            avatar.nameTag=null;
            if(category==6){
                Integer sword=LocalCosmetics.selectedForType("sword"),mace=LocalCosmetics.selectedForType("mace");
                avatar.rightHandItemStack=sword==null?new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD):WeaponCosmetics.preview(sword);
                avatar.leftHandItemStack=mace==null?new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MACE):WeaponCosmetics.preview(mace);
                mc.getItemModelResolver().updateForLiving(avatar.rightHandItemState,avatar.rightHandItemStack,net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,mc.player);
                mc.getItemModelResolver().updateForLiving(avatar.leftHandItemState,avatar.leftHandItemStack,net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND,mc.player);
                avatar.rightArmPose=avatar.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
            }
        }
        float scale=Math.min((bottom-top)/2.4f,45)*zoom;
        var placement=GuiEntityPlacement.from(g.pose(),left,top,right,bottom,scale);
        g.entity(state,placement.scale(),new Vector3f(0,state.boundingBoxHeight/2+.0625f,0),
            new Quaternionf().rotateZ((float)Math.PI),new Quaternionf(),placement.left(),placement.top(),placement.right(),placement.bottom());
    }
    boolean click(double mx, double my, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || layout == null || !layout.bounds.contains(mx, my)) return false;
        for (int i = 0; i < LABELS.length; i++) if (layout.category(i).contains(mx, my)) {
            release(); category = i; scroll = 0; hits.clear(); return true;
        }
        if (layout.layers.contains(mx, my)) { SkinManager.setLayers3d(!SkinManager.layers3d()); return true; }
        if (layout.autoRotate.contains(mx, my)) { autoRotate = !autoRotate; return true; }
        if (layout.resetView.contains(mx, my)) { yaw = 25; pitch = 0; zoom = 1; autoRotate = false; return true; }
        if (layout.zoomOut.contains(mx, my)) { zoom = Math.max(MIN_ZOOM, zoom - .1f); return true; }
        if (layout.zoomIn.contains(mx, my)) { zoom = Math.min(MAX_ZOOM, zoom + .1f); return true; }
        if (layout.zoomTrack.contains(mx, my)) { draggingZoom = true; updateZoom(mx); return true; }
        if (layout.model.contains(mx, my)) { rotating = true; autoRotate = false; return true; }
        if (maxScroll > 0 && new Rect(layout.scrollbar.x() - 2, layout.grid.y(), 7, layout.grid.h()).contains(mx, my)) {
            Rect thumb = scrollbarThumb();
            scrollGrab = thumb.contains(mx, my) ? (float)my - thumb.y() : thumb.h() / 2f;
            draggingScroll = true; updateScroll(my); return true;
        }
        if (category == 7) {
            if (layout.skinAction(0).contains(mx, my)) { com.mojang.blaze3d.Blaze3D.openPath(SkinManager.folder()); return true; }
            if (layout.skinAction(1).contains(mx, my)) { SkinManager.refresh(true); hits.clear(); return true; }
            if (layout.skinAction(2).contains(mx, my)) { SkinManager.select(null); return true; }
        } else {
            if (layout.clear.contains(mx, my)) { LocalCosmetics.unequipAll(); hits.clear(); return true; }
            if (layout.equipped.contains(mx, my)) { equippedOnly = !equippedOnly; scroll = 0; hits.clear(); return true; }
        }
        for (Hit hit : hits) if (hit.bounds.contains(mx, my)) {
            if (category == 7) {
                var skins = SkinManager.entries();
                if (hit.index < skins.size()) {
                    var skin = skins.get(hit.index);
                    SkinManager.select(SkinManager.selectedEntry() == skin ? null : skin);
                    SkinManager.setSelf(true);
                }
            } else LocalCosmetics.toggle(hit.index);
            return true;
        }
        return true;
    }

    private Rect scrollbarThumb() {
        Rect track = layout.scrollbar;
        int height = Math.max(18, Math.round(track.h() * track.h() / (float)(track.h() + maxScroll)));
        int y = track.y() + Math.round((track.h() - height) * scroll / (float)Math.max(1, maxScroll));
        return new Rect(track.x(), y, 2, height);
    }
    private void updateScroll(double my) {
        Rect thumb = scrollbarThumb();
        float progress = ((float)my - layout.grid.y() - scrollGrab) / Math.max(1, layout.grid.h() - thumb.h());
        scroll = Math.round(Math.clamp(progress, 0, 1) * maxScroll);
        hits.clear();
    }
    private void updateZoom(double mx) {
        double progress = Math.clamp((mx - layout.zoomTrack.x()) / layout.zoomTrack.w(), 0, 1);
        zoom = MIN_ZOOM + (float)progress * (MAX_ZOOM - MIN_ZOOM);
    }
    boolean drag(double mx, double my, double dx, double dy) {
        if (draggingZoom) { updateZoom(mx); return true; }
        if (draggingScroll) { updateScroll(my); return true; }
        if (!rotating) return false;
        yaw += (float)dx * 1.4f; pitch = Math.clamp(pitch + (float)dy * .6f, -35, 35); return true;
    }
    boolean release() {
        boolean active = rotating || draggingZoom || draggingScroll;
        rotating = draggingZoom = draggingScroll = false;
        return active;
    }
    void scroll(double mx, double my, double amount) {
        if (layout == null) return;
        if (layout.preview.contains(mx, my)) zoom = Math.clamp(zoom + (float)amount * .08f, MIN_ZOOM, MAX_ZOOM);
        else if (layout.grid.contains(mx, my)) { scroll = Math.clamp(scroll - (int)(amount * 24), 0, maxScroll); hits.clear(); }
    }
}
