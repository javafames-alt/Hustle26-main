package im.yaHustler.ui;

import com.mojang.blaze3d.Blaze3D;
import im.yaHustler.font.Fonts;
import im.yaHustler.font.Icons;
import im.yaHustler.skin.SkinEntry;
import im.yaHustler.skin.SkinManager;
import im.yaHustler.util.ColorUtil;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

final class SkinChangerView {
    private static final int LIST_W = 84, PREVIEW_W = 104, GAP = 8, CARD_H = 76, CARD_GAP = 6, TOOLBAR_H = 14;
    private static final float DOLL = 1.5f;

    private int x, y, w, h, gridX, gridY, gridW, gridH, previewX, cols, cardW, scroll, contentH;
    private int folderX, refreshX, toggleX, toolbarY, folderW, refreshW;
    private float toggleAnim = -1;

    void draw(GuiGraphicsExtractor g, ThemeEditor.Colors c, int x, int y, int w, int h, int mx, int my, float delta) {
        layout(x, y, w, h);
        List<SkinEntry> entries = SkinManager.entries();
        SkinEntry selected = SkinManager.selectedEntry();

        Fonts.drawMedium(g, "ГАРДЕРОБ", x + 2, y + 3, ColorUtil.lerp(c.muted(), c.card(), .2f), .55f);
        int rowY = y + 13;
        Draw.rounded(g, x, rowY, LIST_W, 18, 4, c.selected());
        Fonts.drawIcon(g, Icons.USER, x + 5, rowY + 4.5f, c.accent(), .8f);
        Fonts.drawMedium(g, "Skin Changer", x + 18, rowY + 6, c.text(), .66f);
        String count = Integer.toString(entries.size());
        Fonts.draw(g, count, x + LIST_W - 5 - Fonts.width(count, .58f), rowY + 6.5f, c.muted(), .58f);
        Fonts.drawMedium(g, "НАДЕТО", x + 2, rowY + 28, ColorUtil.lerp(c.muted(), c.card(), .2f), .55f);
        Fonts.draw(g, Fonts.fit(selected == null ? "Стандартный скин" : selected.name(), LIST_W - 4, .6f), x + 2, rowY + 38, c.muted(), .6f);
        g.fill(x + LIST_W + GAP / 2, y, x + LIST_W + GAP / 2 + 1, y + h, c.line());

        Fonts.drawMedium(g, "Skin Changer", gridX, y + 2, c.text(), .95f);
        float titleW = Fonts.width("Skin Changer", .95f);
        Fonts.draw(g, entries.size() + " скинов", gridX + titleW + 5, y + 5, c.muted(), .58f);

        boolean on = SkinManager.layers3d();
        toggleAnim = toggleAnim < 0 ? (on ? 1 : 0) : toggleAnim + ((on ? 1 : 0) - toggleAnim) * .25f;
        chip(g, c, "Открыть папку", folderX, toolbarY, folderW, mx, my);
        chip(g, c, "Обновить", refreshX, toolbarY, refreshW, mx, my);
        Fonts.draw(g, "3D слой", toggleX, toolbarY + 4.5f, on ? c.text() : c.muted(), .6f);
        Draw.toggle(g, toggleX + Fonts.width("3D слой", .6f) + 4, toolbarY + 1.5f, toggleAnim,
            Draw.inside(mx, my, toggleX, toolbarY, toggleWidth(), TOOLBAR_H));

        contentH = ((entries.size() + 1 + cols - 1) / cols) * (CARD_H + CARD_GAP) - CARD_GAP;
        scroll = Math.clamp(scroll, 0, Math.max(0, contentH - gridH));
        SkinEntry hovered = null;
        boolean hoverDefault = false;
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        for (int i = 0; i <= entries.size(); i++) {
            int cx = gridX + i % cols * (cardW + CARD_GAP), cy = gridY + i / cols * (CARD_H + CARD_GAP) - scroll;
            if (cy + CARD_H < gridY || cy > gridY + gridH) continue;
            SkinEntry entry = i == 0 ? null : entries.get(i - 1);
            boolean hover = my >= gridY && my < gridY + gridH && Draw.inside(mx, my, cx, cy, cardW, CARD_H);
            boolean worn = entry == selected;
            if (hover) {
                if (entry == null) hoverDefault = true;
                else hovered = entry;
            }
            if (worn) Draw.rounded(g, cx - 1, cy - 1, cardW + 2, CARD_H + 2, 6, ColorUtil.alpha(c.accent(), 0xb0));
            Draw.rounded(g, cx, cy, cardW, CARD_H, 5, hover ? c.cardHover() : c.card());
            Draw.rounded(g, cx + 3, cy + 3, cardW - 6, CARD_H - 27, 4, ColorUtil.alpha(0xff000000, 0x40));
            int dw = Math.round((entry != null && entry.slim() ? 14 : 16) * DOLL);
            float dx = cx + (cardW - dw) / 2f, dy = cy + 3 + (CARD_H - 27 - 32 * DOLL) / 2f;
            if (entry != null) doll(g, entry.texture(), entry.slim(), dx, dy);
            else {
                PlayerSkin original = SkinManager.originalSkin();
                if (original == null) original = Minecraft.getInstance().player.getSkin();
                doll(g, original.body().texturePath(), original.model() == PlayerModelType.SLIM, dx, dy);
            }
            String name = entry == null ? "Стандартный" : entry.name();
            Fonts.drawMedium(g, Fonts.fit(name, cardW - 10, .64f), cx + 5, cy + CARD_H - 21, c.text(), .64f);
            String status = worn ? "Надето" : hover ? "Надеть" : "Примерить";
            Fonts.draw(g, status, cx + 5, cy + CARD_H - 11, worn ? c.accent() : c.muted(), .56f);
        }
        g.disableScissor();
        if (contentH > gridH) {
            int thumb = Math.max(16, gridH * gridH / contentH);
            int ty = gridY + (gridH - thumb) * scroll / Math.max(1, contentH - gridH);
            Draw.rounded(g, gridX + gridW + 2, ty, 2, thumb, 1, ColorUtil.alpha(c.accent(), 0x70));
        }
        if (entries.isEmpty()) {
            Fonts.draw(g, "Закиньте .png скины в папку", gridX, gridY + CARD_H + 10, c.muted(), .6f);
            Fonts.draw(g, "labymod-neo/skins", gridX, gridY + CARD_H + 19, c.accent(), .6f);
        }

        drawPreview(g, c, mx, my, hovered, hoverDefault);
    }

    private void drawPreview(GuiGraphicsExtractor g, ThemeEditor.Colors c, int mx, int my, SkinEntry hovered, boolean hoverDefault) {
        int px = previewX, py = y, pw = PREVIEW_W, ph = h;
        Draw.rounded(g, px, py, pw, ph, 6, c.popupBorder());
        Draw.rounded(g, px + 1, py + 1, pw - 2, ph - 2, 5, c.popup());
        g.enableScissor(px + 1, py + 1, px + pw - 1, py + ph - 1);
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph / 2, ColorUtil.alpha(c.accent(), 0x30), ColorUtil.alpha(c.accent(), 0));
        g.disableScissor();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            SkinManager.preview(hovered != null || hoverDefault, hovered);
            int top = py + 26, bottom = py + ph - 44;
            int size = Math.max(20, (bottom - top) * 10 / 21);
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, px + 6, top, px + pw - 6, bottom, size, .0625f, mx, my, player);
            SkinManager.preview(false, null);
        }
        Fonts.drawMedium(g, "ГАРДЕРОБ", px + 8, py + ph - 38, c.muted(), .52f);
        SkinEntry shown = hovered != null ? hovered : hoverDefault ? null : SkinManager.selectedEntry();
        String title = shown == null ? "Стандартный" : shown.name();
        Fonts.drawMedium(g, Fonts.fit(title, pw - 16, .8f), px + 8, py + ph - 29, c.text(), .8f);
        Fonts.draw(g, Fonts.fit("Наведите на скин, чтобы примерить", pw - 14, .5f), px + 8, py + ph - 14, c.muted(), .5f);
    }

    private static void doll(GuiGraphicsExtractor g, Identifier skin, boolean slim, float x, float y) {
        int arm = slim ? 3 : 4;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(DOLL, DOLL);
        part(g, skin, arm, 0, 8, 8, 8, 8);
        part(g, skin, arm, 0, 40, 8, 8, 8);
        part(g, skin, arm, 8, 20, 20, 8, 12);
        part(g, skin, arm, 8, 20, 36, 8, 12);
        part(g, skin, 0, 8, 44, 20, arm, 12);
        part(g, skin, 0, 8, 44, 36, arm, 12);
        part(g, skin, arm + 8, 8, 36, 52, arm, 12);
        part(g, skin, arm + 8, 8, 52, 52, arm, 12);
        part(g, skin, arm, 20, 4, 20, 4, 12);
        part(g, skin, arm, 20, 4, 36, 4, 12);
        part(g, skin, arm + 4, 20, 20, 52, 4, 12);
        part(g, skin, arm + 4, 20, 4, 52, 4, 12);
        g.pose().popMatrix();
    }

    private static void part(GuiGraphicsExtractor g, Identifier skin, int x, int y, int u, int v, int w, int h) {
        im.yaHustler.ui.GuiTextures.blit(g, RenderPipelines.GUI_TEXTURED, skin, x, y, u, v, w, h, w, h, 64, 64);
    }

    private void chip(GuiGraphicsExtractor g, ThemeEditor.Colors c, String text, int cx, int cy, int cw, int mx, int my) {
        boolean hover = Draw.inside(mx, my, cx, cy, cw, TOOLBAR_H);
        Draw.rounded(g, cx, cy, cw, TOOLBAR_H, 4, hover ? c.fieldHover() : c.field());
        Fonts.draw(g, text, cx + 6, cy + 4.5f, hover ? c.text() : c.muted(), .6f);
    }

    private int toggleWidth() { return Math.round(Fonts.width("3D слой", .6f) + 24); }

    private void layout(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        gridX = x + LIST_W + GAP;
        previewX = x + w - PREVIEW_W;
        gridW = previewX - GAP - gridX - 4;
        toolbarY = y + 16;
        folderW = Math.round(Fonts.width("Открыть папку", .6f)) + 12;
        refreshW = Math.round(Fonts.width("Обновить", .6f)) + 12;
        folderX = gridX;
        refreshX = folderX + folderW + 4;
        toggleX = refreshX + refreshW + 8;
        gridY = toolbarY + TOOLBAR_H + 7;
        gridH = y + h - gridY;
        cols = Math.max(1, (gridW + CARD_GAP) / (74 + CARD_GAP));
        cardW = (gridW - (cols - 1) * CARD_GAP) / cols;
    }

    boolean mouseClicked(double mx, double my) {
        if (!Draw.inside(mx, my, x, y, w, h)) return false;
        if (Draw.inside(mx, my, folderX, toolbarY, folderW, TOOLBAR_H)) {
            Blaze3D.openPath(SkinManager.folder());
            return true;
        }
        if (Draw.inside(mx, my, refreshX, toolbarY, refreshW, TOOLBAR_H)) {
            SkinManager.refresh(true);
            return true;
        }
        if (Draw.inside(mx, my, toggleX, toolbarY, toggleWidth(), TOOLBAR_H)) {
            SkinManager.setLayers3d(!SkinManager.layers3d());
            return true;
        }
        if (!Draw.inside(mx, my, gridX, gridY, gridW, gridH)) return true;
        List<SkinEntry> entries = SkinManager.entries();
        for (int i = 0; i <= entries.size(); i++) {
            int cx = gridX + i % cols * (cardW + CARD_GAP), cy = gridY + i / cols * (CARD_H + CARD_GAP) - scroll;
            if (!Draw.inside(mx, my, cx, cy, cardW, CARD_H)) continue;
            SkinManager.select(i == 0 ? null : entries.get(i - 1));
            return true;
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double vertical) {
        if (!Draw.inside(mx, my, gridX, gridY, gridW, gridH)) return false;
        scroll = Math.clamp(scroll - (int) (vertical * 20), 0, Math.max(0, contentH - gridH));
        return true;
    }
}
