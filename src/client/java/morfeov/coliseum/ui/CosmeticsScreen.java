package morfeov.coliseum.ui;

import com.mojang.blaze3d.Blaze3D;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.font.Fonts;
import morfeov.coliseum.font.Icons;
import morfeov.coliseum.module.impl.themes.Theme;
import morfeov.coliseum.module.impl.themes.ThemeColor;
import morfeov.coliseum.skin.SkinEntry;
import morfeov.coliseum.skin.SkinManager;
import morfeov.coliseum.util.ColorUtil;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

public final class CosmeticsScreen extends Screen {
    private static final int SIDE = 84, PREVIEW_W = 118, GAP = 8, CARD_H = 64, CARD_GAP = 6, HEADER = 32, ROW_H = 16;
    private static final float DOLL = 1.25f;
    private final Screen parent;
    private int x, y, w, h, px, gridX, gridY, gridW, gridH, cols, cardW, scroll, contentH;
    private int skinRowY, offRowY, selfY, layersY, folderX, refreshX, folderW, refreshW, buttonsY;
    private int text, muted, dim, accent, card, cardHover, field, fieldHover, selected, border, popup;

    public CosmeticsScreen(Screen parent) {
        super(Component.literal("Косметика"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int total = Math.min(width - 20, 560);
        w = total - PREVIEW_W - GAP;
        h = Math.min(height - 24, 270);
        x = (width - total) / 2;
        y = (height - h) / 2;
        px = x + w + GAP;
        gridX = x + SIDE + 10;
        gridY = y + HEADER;
        gridW = x + w - 12 - gridX;
        gridH = y + h - 10 - gridY;
        cols = Math.max(2, (gridW + CARD_GAP) / (66 + CARD_GAP));
        cardW = (gridW - (cols - 1) * CARD_GAP) / cols;
        skinRowY = y + 52;
        offRowY = skinRowY + ROW_H + 18;
        selfY = y + h - 40;
        layersY = selfY + 16;
        folderW = Math.round(Fonts.width("Открыть папку", .58f)) + 12;
        refreshW = Math.round(Fonts.width("Обновить", .58f)) + 12;
        refreshX = x + w - 12 - refreshW;
        folderX = refreshX - 4 - folderW;
        buttonsY = y + 10;
        SkinManager.refresh(false);
    }

    private void colors() {
        int background = ColorUtil.opaque(Theme.color(ThemeColor.BACKGROUND));
        text = Theme.color(ThemeColor.TEXT);
        muted = Theme.color(ThemeColor.TEXT_OFF);
        dim = ColorUtil.lerp(muted, background, .4f);
        accent = Theme.accent();
        card = ColorUtil.alpha(ColorUtil.lerp(background, 0xffffffff, .06f), 0xc0);
        cardHover = ColorUtil.alpha(ColorUtil.lerp(background, 0xffffffff, .1f), 0xd0);
        field = ColorUtil.lerp(background, 0xffffffff, .045f);
        fieldHover = ColorUtil.lerp(background, 0xffffffff, .09f);
        selected = Theme.color(ThemeColor.SELECTED);
        border = Theme.color(ThemeColor.OUTLINE);
        popup = Theme.color(ThemeColor.BACKGROUND);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, ColorUtil.alpha(Theme.color(ThemeColor.BACKGROUND), 0x28));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        colors();
        Draw.panel(g, x, y, w, h, SIDE);
        Fonts.drawIcon(g, Icons.LOGO, x + 12, y + 11, accent, .9f);
        Fonts.drawMedium(g, "Косметика", x + 27, y + 12.5f, text, .74f);

        Fonts.drawMedium(g, "Прочее", x + 12, skinRowY - 11, dim, .55f);
        sideRow(g, Icons.USER, "SkinChanger", skinRowY, true, mx, my);
        boolean off = SkinManager.selectedEntry() == null;
        boolean offHover = Draw.inside(mx, my, x + 6, offRowY, SIDE - 12, ROW_H);
        if (offHover) Draw.rounded(g, x + 6, offRowY, SIDE - 12, ROW_H, 4, 0x0cffffff);
        cross(g, x + 15, offRowY + ROW_H / 2f, off ? text : muted, 2.4f);
        Fonts.draw(g, "Выключено", x + 24, offRowY + 5, off ? text : offHover ? ColorUtil.lerp(muted, text, .3f) : muted, .62f);

        Fonts.drawMedium(g, "SkinChanger", gridX, y + 12, text, .8f);
        String count = SkinManager.entries().size() + " скинов";
        Fonts.draw(g, count, gridX + Fonts.width("SkinChanger", .8f) + 5, y + 14, muted, .56f);
        chip(g, "Открыть папку", folderX, folderW, mx, my);
        chip(g, "Обновить", refreshX, refreshW, mx, my);

        SkinEntry hovered = null;
        boolean hoverDefault = false;
        List<SkinEntry> entries = SkinManager.entries();
        SkinEntry worn = SkinManager.selectedEntry();
        contentH = ((entries.size() + cols) / cols) * (CARD_H + CARD_GAP) - CARD_GAP;
        scroll = Math.clamp(scroll, 0, Math.max(0, contentH - gridH));
        g.enableScissor(gridX - 2, gridY - 2, gridX + gridW + 2, gridY + gridH + 2);
        for (int i = 0; i <= entries.size(); i++) {
            int cx = gridX + i % cols * (cardW + CARD_GAP), cy = gridY + i / cols * (CARD_H + CARD_GAP) - scroll;
            if (cy + CARD_H < gridY || cy > gridY + gridH) continue;
            SkinEntry entry = i == 0 ? null : entries.get(i - 1);
            boolean hover = my >= gridY && my < gridY + gridH && Draw.inside(mx, my, cx, cy, cardW, CARD_H);
            boolean active = entry == worn;
            if (hover) {
                if (entry == null) hoverDefault = true;
                else hovered = entry;
            }
            if (active) Draw.rounded(g, cx - 1, cy - 1, cardW + 2, CARD_H + 2, 6, ColorUtil.alpha(accent, 0xd0));
            Draw.rounded(g, cx, cy, cardW, CARD_H, 5, hover ? cardHover : card);
            float dw = (entry != null && entry.slim() ? 14 : 16) * DOLL;
            float dx = cx + (cardW - dw) / 2f, dy = cy + 5;
            if (entry != null) doll(g, entry.texture(), entry.slim(), dx, dy);
            else {
                PlayerSkin original = SkinManager.originalSkin();
                if (original == null && minecraft.player != null) original = minecraft.player.getSkin();
                if (original != null) doll(g, original.body().texturePath(), original.model() == PlayerModelType.SLIM, dx, dy);
            }
            String name = Fonts.fit(entry == null ? "Стандартный" : entry.name(), cardW - 8, .56f);
            Fonts.draw(g, name, cx + (cardW - Fonts.width(name, .56f)) / 2f, cy + CARD_H - 10,
                active ? accent : hover ? text : muted, .56f);
        }
        g.disableScissor();
        if (contentH > gridH) {
            int thumb = Math.max(16, gridH * gridH / contentH);
            int ty = gridY + (gridH - thumb) * scroll / Math.max(1, contentH - gridH);
            Draw.rounded(g, x + w - 7, ty, 2, thumb, 1, Theme.color(ThemeColor.SCROLL));
        }
        if (entries.isEmpty()) {
            Fonts.draw(g, "Закиньте .png скины в папку labymod-neo/skins", gridX, gridY + CARD_H + 10, muted, .58f);
        }

        drawPreview(g, mx, my, hovered, hoverDefault);
        super.extractRenderState(g, mx, my, delta);
    }

    private void drawPreview(GuiGraphicsExtractor g, int mx, int my, SkinEntry hovered, boolean hoverDefault) {
        Draw.rounded(g, px, y, PREVIEW_W, h, 9, border);
        Draw.rounded(g, px + 1, y + 1, PREVIEW_W - 2, h - 2, 8, popup);
        Fonts.drawMedium(g, "Предпросмотр", px + 10, y + 12, text, .7f);
        if (minecraft.player != null) {
            SkinManager.preview(hovered != null || hoverDefault, hovered);
            int top = y + 30, bottom = selfY - 8;
            int size = Math.max(20, (bottom - top) * 10 / 21);
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, px + 6, top, px + PREVIEW_W - 6, bottom, size, .0625f, mx, my, minecraft.player);
            SkinManager.preview(false, null);
        }
        option(g, "На себя", selfY, SkinManager.self(), mx, my);
        option(g, "3D слой", layersY, SkinManager.layers3d(), mx, my);
    }

    private void option(GuiGraphicsExtractor g, String label, int oy, boolean on, int mx, int my) {
        boolean hover = Draw.inside(mx, my, px + 6, oy - 2, PREVIEW_W - 12, 14);
        if (on) check(g, px + 14, oy + 5, accent);
        else cross(g, px + 14, oy + 5, hover ? text : muted, 2.4f);
        Fonts.draw(g, label, px + 23, oy + 2, on || hover ? text : muted, .6f);
    }

    private void sideRow(GuiGraphicsExtractor g, String icon, String label, int ry, boolean active, int mx, int my) {
        boolean hover = Draw.inside(mx, my, x + 6, ry, SIDE - 12, ROW_H);
        if (active) Draw.rounded(g, x + 6, ry, SIDE - 12, ROW_H, 4, selected);
        else if (hover) Draw.rounded(g, x + 6, ry, SIDE - 12, ROW_H, 4, 0x0cffffff);
        Fonts.drawIcon(g, icon, x + 10, ry + 3.5f, active ? accent : muted, .8f);
        if (active) Fonts.drawMedium(g, label, x + 24, ry + 5, text, .62f);
        else Fonts.draw(g, label, x + 24, ry + 5, muted, .62f);
    }

    private void chip(GuiGraphicsExtractor g, String label, int cx, int cw, int mx, int my) {
        boolean hover = Draw.inside(mx, my, cx, buttonsY, cw, 14);
        Draw.rounded(g, cx, buttonsY, cw, 14, 4, hover ? fieldHover : field);
        Fonts.draw(g, label, cx + 6, buttonsY + 4.5f, hover ? text : muted, .58f);
    }

    private static void cross(GuiGraphicsExtractor g, float cx, float cy, int color, float r) {
        stroke(g, cx - r, cy - r, cx + r, cy + r, color);
        stroke(g, cx - r, cy + r, cx + r, cy - r, color);
    }

    private static void check(GuiGraphicsExtractor g, float cx, float cy, int color) {
        stroke(g, cx - 3, cy, cx - 1, cy + 2.2f, color);
        stroke(g, cx - 1, cy + 2.2f, cx + 3.2f, cy - 2.3f, color);
    }

    private static void stroke(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int color) {
        int steps = (int) Math.ceil(Math.hypot(x2 - x1, y2 - y1) * 3);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            Draw.circle(g, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, .6f, color);
        }
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
        morfeov.coliseum.ui.GuiTextures.blit(g, RenderPipelines.GUI_TEXTURED, skin, x, y, u, v, w, h, w, h, 64, 64);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        if (Draw.inside(mx, my, x + 6, offRowY, SIDE - 12, ROW_H)) { SkinManager.select(null); return true; }
        if (Draw.inside(mx, my, folderX, buttonsY, folderW, 14)) { Blaze3D.openPath(SkinManager.folder()); return true; }
        if (Draw.inside(mx, my, refreshX, buttonsY, refreshW, 14)) { SkinManager.refresh(true); return true; }
        if (Draw.inside(mx, my, px + 6, selfY - 2, PREVIEW_W - 12, 14)) { SkinManager.setSelf(!SkinManager.self()); return true; }
        if (Draw.inside(mx, my, px + 6, layersY - 2, PREVIEW_W - 12, 14)) { SkinManager.setLayers3d(!SkinManager.layers3d()); return true; }
        if (Draw.inside(mx, my, gridX, gridY, gridW, gridH)) {
            List<SkinEntry> entries = SkinManager.entries();
            for (int i = 0; i <= entries.size(); i++) {
                int cx = gridX + i % cols * (cardW + CARD_GAP), cy = gridY + i / cols * (CARD_H + CARD_GAP) - scroll;
                if (!Draw.inside(mx, my, cx, cy, cardW, CARD_H)) continue;
                SkinManager.select(i == 0 ? null : entries.get(i - 1));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Draw.inside(mx, my, gridX, gridY, gridW, gridH)) {
            scroll = Math.clamp(scroll - (int) (vertical * 20), 0, Math.max(0, contentH - gridH));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (Coliseum.OPEN_GUI.matches(event)) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
