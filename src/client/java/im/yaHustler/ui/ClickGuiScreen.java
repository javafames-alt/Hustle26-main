package im.yaHustler.ui;

import com.mojang.blaze3d.platform.InputConstants;
import im.yaHustler.Hustle26;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleRegistry;
import im.yaHustler.util.ColorUtil;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.ColorSetting;
import im.yaHustler.setting.ModeSetting;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ClickGuiScreen extends Screen {
    private long lastFrameNanos;
    private float frameSeconds;
    private static final int HEADER = 40, CARD_H = 34, GAP_Y = 8, FOOTER = 43, WINDOW_RADIUS = 13;
    private static final int HUD_PANEL = 0xff101521, HUD_FIELD = 0xff141c2b, HUD_PILL = 0xff192334,
        HUD_TEXT = 0xfff0f4fc, HUD_MUTED = 0xffa2b1c9, HUD_RULE = 0x1affffff;
    private static final int SETTINGS_PAD = 6;
    private int textColor, mutedColor, accentColor, lineColor, cardColor, cardHoverColor, popupBorderColor;
    private enum View { MODULES, COSMETICS, THEMES, CONFIGS, EVENTS }
    private enum Tab {
        COMBAT(Category.COMBAT, "combat"), MOVEMENT(Category.MOVEMENT, "movement"),
        PLAYER(Category.PLAYER, "player"), RENDER(Category.RENDER, "visuals"),
        MISC(Category.MISC, "misc"), COSMETICS(View.COSMETICS, "Cosmetics", "user"),
        CONFIGS(View.CONFIGS, "Configs", "config"), THEMES(View.THEMES, "Themes", "theme"),
        EVENTS(View.EVENTS, "Events", "activity"), BOTS(null, "Bots", "monitor");

        final View view;
        final Category category;
        final String title, icon;

        Tab(Category category, String icon) {
            this(View.MODULES, category, category.label, icon);
        }

        Tab(View view, String title, String icon) {
            this(view, null, title, icon);
        }

        Tab(View view, Category category, String title, String icon) {
            this.view = view;
            this.category = category;
            this.title = title;
            this.icon = icon;
        }
    }
    private static final Tab[] TABS = Tab.values();
    private static final int TAB_HEIGHT = 22, TAB_PAD = 4;
    private View view = View.MODULES;
    private final GuiCosmeticsPage cosmeticsPage = new GuiCosmeticsPage();
    private im.yaHustler.setting.KeySetting actionBinding;
    private final GuiEventsPage eventsPage = new GuiEventsPage();
    private final GuiThemesPage themesPage = new GuiThemesPage();
    private GuiConfigsPage configsPage;
    private boolean creatingProfile;
    private SmoothSearchBox profileName;
    private String createError = "";
    private final ModuleRegistry registry;
    private static Category category = Category.COMBAT;
    private int scroll, contentHeight;
    private float visibleScroll;
    private int columnWidth, columns;
    private SmoothSearchBox search;
    private int x, y, panelWidth, panelHeight;
    private boolean favoritesOnly;
    private long visibleRevision = -1;
    private String visibleQuery;
    private Category visibleCategory;
    private boolean visibleFavorites;
    private List<Module> visibleModules = List.of();
    private final List<ModuleHit> moduleHits = new ArrayList<>();
    private List<Module> layoutModules;
    private int[] layoutHeights = new int[0];
    private ModuleGridLayout.Placement[] layoutCards = new ModuleGridLayout.Placement[0];
    private int layoutX = Integer.MIN_VALUE, layoutY, layoutWidth, layoutPanelHeight, layoutScroll;
    private final java.util.Map<Module, Float> expandAnim = new java.util.HashMap<>();
    private final List<SettingHit> settingHits = new ArrayList<>();
    private static final java.util.Set<Module> expanded = new java.util.HashSet<>();
    private final UiAnimationEngine animation = new UiAnimationEngine();
    private final GuiMotion windowMotion = new GuiMotion();
    private GuiMotion.Frame windowFrame = GuiMotion.initialFrame();
    private final GuiPageMotion pageMotion = new GuiPageMotion();
    private GuiPageMotion.Frame pageFrame = new GuiPageMotion.Frame(1, 0, 0);
    private View pendingView;
    private Category pendingCategory;
    private int pendingDirection;
    private boolean draggingWindow, draggingScrollbar;
    private float scrollGrab;
    private static int windowDx, windowDy;
    private float visibility() { return windowFrame.opacity(); }
    private static Tab tabFor(View view, Category category) {
        for (Tab tab : TABS) if (tab.view == view && (view != View.MODULES || tab.category == category)) return tab;
        return Tab.COMBAT;
    }
    private void navigate(View nextView, Category nextCategory) {
        if (pendingView == nextView && pendingCategory == nextCategory) return;
        if (pendingView == null && view == nextView && category == nextCategory) return;
        cosmeticsPage.release();
        pendingView = nextView;
        pendingCategory = nextCategory;
        pendingDirection = tabFor(nextView, nextCategory).ordinal() >= tabFor(view, category).ordinal() ? 1 : -1;
        long now = System.nanoTime();
        pageMotion.leave(now, pendingDirection);
        pageFrame = pageMotion.sample(now);
        draggingScrollbar = false;
        openMode = null; binding = null; actionBinding = null; settingHits.clear();
        if (draggingSlider != null) { draggingSlider.set(draggingSlider.get()); draggingSlider = null; }
    }
    private void advancePage(long now) {
        if (pendingView == null || !pageMotion.readyToEnter(now) || windowMotion.closing()) return;
        view = pendingView;
        visibleRevision = -1;
        category = pendingCategory;
        pendingView = null;
        if (view == View.CONFIGS) {
            if (configsPage == null) configsPage = new GuiConfigsPage(registry); else configsPage.refresh();
        }
        if (view == View.EVENTS) eventsPage.open();
        search.setValue("");
        scroll = 0;
        visibleScroll = 0;
        settingHits.clear();
        pageMotion.start(now, pendingDirection);
        pageFrame = pageMotion.sample(now);
        rebuildHitboxes();
    }
    private void toggleExpanded(Module module) { if (!expanded.remove(module)) expanded.add(module); }
    private Module binding;
    private ModeSetting openMode;
    private final java.util.Map<Module,List<Setting<?>>> groupedSettings=new java.util.IdentityHashMap<>();
    private List<Setting<?>> shownSettings(Module module){
        var result=groupedSettings.computeIfAbsent(module,m->BooleanGroup.combine(m.settings()));
        for(var s:result)if(s instanceof BooleanGroup group)group.refresh();
        return result;
    }
    private int dropdownX, dropdownY, dropdownRows, dropdownScroll, dropdownWidth = 68;
    private record RowCache(int width, Object value, SettingRowLayout layout) {}
    private final java.util.Map<Setting<?>, RowCache> rowLayouts = new java.util.HashMap<>();
    private NumberSetting draggingSlider;
    private float sliderX, sliderW;
    public ClickGuiScreen(ModuleRegistry registry) {
        super(Component.literal("Hustle 26.3"));
        this.registry = registry;
        for (Module module : expanded) expandAnim.put(module, 1f);
    }
    @Override
    protected void init() {
        String previousQuery = search == null ? "" : search.getValue();
        if (search != null) search.setFocused(false);
        panelWidth = Math.clamp(width - 32, 344, 560);
        panelHeight = Math.clamp(height - 24, 260, 360);
        int nextColumns = contentWidth() >= 480 ? 3 : 2;
        if (columns != nextColumns) layoutModules = null;
        columns = nextColumns;
        columnWidth = (contentWidth() - GAP_Y * (columns - 1)) / columns;
        x = (width - panelWidth) / 2 + windowDx;
        y = (height - panelHeight) / 2 + windowDy;
        search = new SmoothSearchBox(searchBoxX() + 20, y + 9,
            searchBoxWidth() - 26, 20, "Поиск", "Поиск…");
        search.textScale(7.5f / 11);
        search.visible = search.active = true;
        search.useFigmaFont();
        search.setValue(previousQuery);
        search.setResponder(query -> { scroll = 0; visibleScroll = 0; settingHits.clear(); });
        addRenderableWidget(search);
        profileName = new SmoothSearchBox(dialogX() + 12, dialogY() + 31, 216, 20, "Имя конфига", "Название конфига");
        profileName.useFigmaFont(); profileName.visible = profileName.active = creatingProfile;
        addRenderableWidget(profileName);
        rebuildHitboxes();
    }
    private int searchBoxWidth() { return 124; }
    private int favoriteX() { return x + panelWidth - 32; }
    private int searchBoxX() { return favoriteX() - 6 - searchBoxWidth(); }
    private int contentX() { return x + 12; }
    private int contentTop() { return y + HEADER; }
    private int contentBottom() { return y + panelHeight - FOOTER; }
    private int contentWidth() { return panelWidth - 24; }
    private List<Module> visible() {
        if (!moduleView()) return List.of();
        String query = search.getValue();
        long revision = registry.revision();
        if (revision != visibleRevision) {
            expanded.retainAll(registry.all());
            expandAnim.keySet().retainAll(registry.all());
            groupedSettings.keySet().retainAll(registry.all());
            rowLayouts.clear();
        }
        if (revision != visibleRevision || !query.equals(visibleQuery) || category != visibleCategory || favoritesOnly != visibleFavorites) {
            visibleModules = List.copyOf(query.isBlank() ? registry.visible(category, query, favoritesOnly) : registry.search(query, favoritesOnly));
            visibleRevision = revision; visibleQuery = query; visibleCategory = category; visibleFavorites = favoritesOnly;
        }
        return visibleModules;
    }
    private boolean moduleView() { return view == View.MODULES; }
    private im.yaHustler.module.impl.render.ClickGui options() { return registry.get(im.yaHustler.module.impl.render.ClickGui.class); }
    public boolean blurEnabled() { return true; }
    public float blurStrength() { return Math.max(8, options() == null ? 14 : options().blurStrength.floatValue()) * visibility(); }
    public GuiTransform transform() {
        GuiTransform base = GuiTransform.centered(options() == null ? 1f : options().scale.floatValue(), width, height, panelWidth, panelHeight);
        float cx=x+panelWidth/2f,cy=y+panelHeight/2f;
        return base.animated(windowFrame.scale(),windowFrame.offsetY(),cx,cy);
    }
    public float optionsOpacity() { return options() == null ? .9f : options().opacity.floatValue(); }
    public void setOptionsOpacity(float opacity) { if (options() != null) options().opacity.set((double)opacity); }
    public boolean themeBlur() { return options() == null || options().themeBlur.enabled(); }
    public int glassShaderMode() { return options() == null ? 2 : options().glassShader.modes().indexOf(options().glassShader.get()); }
    public float glassShaderStrength() { return (options() == null ? .75f : options().shaderStrength.floatValue() / 100f) * visibility() * optionsOpacity(); }
    public float glassShaderSpeed() { return options() == null ? 1 : options().shaderSpeed.floatValue(); }
    public float backdropOpacity() { return visibility(); }
    private int moduleRadius() { return options() == null ? 5 : options().cornerRadius.intValue(); }
    public float windowRight() { return transform().screenX(x + panelWidth); }
    public float windowTop() { return transform().screenY(y); }
    public void drawSurface(GuiGraphicsExtractor g, float sx, float sy, float w, float h, float radius) {
        var style = options();
        if (style == null || style.shadow.enabled()) Draw.shadow(g, sx, sy + 2, w, h, radius,
            style == null ? .65f : style.shadowStrength.floatValue() / 100, style == null ? 8 : style.shadowSize.floatValue());
        Draw.roundedF(g, sx, sy, w, h, radius,
            InterfaceTheme.alpha(InterfaceTheme.tint(HUD_PANEL, themeBlur(), .10f), optionsOpacity() * .92f));
    }
    private MouseButtonEvent localEvent(MouseButtonEvent e) { GuiTransform t = transform(); return new MouseButtonEvent(t.uiX(e.x()), t.uiY(e.y()), e.buttonInfo()); }
    public boolean resizeGesture(double vertical) {
        boolean ctrl = InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
        boolean alt = InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
        if (!ctrl || !alt || vertical == 0 || options() == null) return false;
        options().scale.set((double) transform().scale() - Math.signum(vertical) * .04);
        return true;
    }
    private int dialogX() { return x + (panelWidth - 240) / 2; }
    private int dialogY() { return y + (panelHeight - 98) / 2; }
    private void createProfile() { creatingProfile = true; createError = ""; profileName.visible = profileName.active = true; profileName.setValue(""); search.visible = search.active = false; setFocused(profileName); profileName.setFocused(true); }
    private void closeCreate() { creatingProfile = false; profileName.visible = profileName.active = false; profileName.setFocused(false); search.visible = search.active = true; clearFocus(); }
    private void saveCreatedProfile() { createError = configsPage.create(profileName.getValue().strip()); if (createError.isEmpty()) closeCreate(); }
    private void drawCreate(GuiGraphicsExtractor g, int mx, int my) {
        Draw.rounded(g, x, y, panelWidth, panelHeight, 16, 0x80000000);
        int dx = dialogX(), dy = dialogY();
        Draw.rounded(g, dx - 1, dy - 1, 242, 100, 9, ColorUtil.alpha(accentColor, 120));
        Draw.rounded(g, dx, dy, 240, 98, 8, ColorUtil.opaque(FigmaGuiRenderer.panelColor()));
        FigmaFont.drawMedium(g, "Создать конфиг", dx + 12, dy + 12, textColor, .95f);
        Draw.rounded(g, dx + 10, dy + 30, 220, 22, 5, HUD_FIELD);
        FigmaFont.draw(g, FigmaFont.fit(createError, 216, .5f), dx + 12, dy + 57, 0xffffa0ad, .5f);
        Draw.rounded(g, dx + 12, dy + 71, 103, 19, 5, accentColor);
        Draw.rounded(g, dx + 124, dy + 71, 104, 19, 5, HUD_FIELD);
        FigmaFont.drawMedium(g, "Создать", dx + 42, dy + 77, textColor, .7f);
        FigmaFont.draw(g, "Отмена", dx + 157, dy + 77, textColor, .7f);
    }
    private SettingRowLayout rowLayout(Setting<?> setting) {
        int width = columnWidth - 16;
        var cached = rowLayouts.get(setting);
        if (cached != null && cached.width == width && java.util.Objects.equals(cached.value, setting.get())) return cached.layout;
        var layout = SettingRowLayout.measure(setting, width);
        rowLayouts.put(setting, new RowCache(width, setting.get(), layout));
        return layout;
    }
    private int rowHeight(Setting<?> setting) { return rowLayout(setting).height(); }
    private int settingsHeight(Module module) {
        int h = 0;
        for (Setting<?> setting : shownSettings(module)) if (setting.visible()) h += rowHeight(setting);
        return h == 0 ? 0 : h + SETTINGS_PAD;
    }
    private static boolean hasSettings(Module module) {
        return module.settings().stream().anyMatch(Setting::visible);
    }
    private float expandProgress(Module module) { return expandAnim.getOrDefault(module, 0f); }
    private int cardHeight(Module module) {
        return CARD_H + Math.round(expandProgress(module) * settingsHeight(module));
    }
    private void rebuildHitboxes() {
        boolean moved = layoutX != x || layoutY != y || layoutWidth != columnWidth || layoutPanelHeight != panelHeight;
        List<Module> modules = visible();
        boolean changed = modules != layoutModules;
        if (layoutHeights.length != modules.size()) { layoutHeights = new int[modules.size()]; changed = true; }
        for (int i = 0; i < modules.size(); i++) {
            int height = cardHeight(modules.get(i));
            if (layoutHeights[i] != height) { layoutHeights[i] = height; changed = true; }
        }
        if (changed) {
            layoutCards = ModuleGridLayout.pack(layoutHeights, GAP_Y, columns);
            layoutModules = modules;
            contentHeight = ModuleGridLayout.contentHeight(layoutCards);
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - (contentBottom() - contentTop())));
        if (!changed && !moved && layoutScroll == scroll) return;
        layoutX = x; layoutY = y; layoutWidth = columnWidth;
        layoutPanelHeight = panelHeight; layoutScroll = scroll;
        moduleHits.clear();
        for (int i = 0; i < modules.size(); i++) {
            var card = layoutCards[i];
            moduleHits.add(new ModuleHit(modules.get(i), contentX() + card.column() * (columnWidth + GAP_Y), contentTop() + card.top() - scroll, columnWidth, card.height()));
        }
    }
    private void refreshColors() {
        textColor = HUD_TEXT;
        mutedColor = HUD_MUTED;
        accentColor = FigmaGuiRenderer.accent();
        lineColor = HUD_RULE;
        cardColor = InterfaceTheme.alpha(0xff182132, optionsOpacity());
        cardHoverColor = InterfaceTheme.alpha(0xff202c40, optionsOpacity());
        popupBorderColor = ColorUtil.alpha(accentColor, 40);
    }
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
    }
    public float[][] backdropSurfaces() { GuiTransform t = transform(); float[] main = {t.screenX(x), t.screenY(y), panelWidth * t.scale(), panelHeight * t.scale()};
        if (minecraft.gui.screen() instanceof ThemePickerScreen picker) return new float[][]{main, picker.popupSurface()};
        if (minecraft.gui.screen() instanceof HudEditorScreen editor) return new float[][]{main, editor.popupSurface()};
        return new float[][]{main}; }
    public float backdropRadius() { return WINDOW_RADIUS * transform().scale(); }
    private int tabWidth(Tab tab, Tab selected) {
        return tab == selected ? 24 + (int)Math.ceil(FigmaFont.mediumWidth(tab.title, .66f)) : 22;
    }

    private int dockWidth(Tab selected) {
        int result = TAB_PAD * 2 + 8;
        for (Tab tab : TABS) result += tabWidth(tab, selected);
        return result;
    }

    private int dockX(Tab selected) { return x + (panelWidth - dockWidth(selected)) / 2; }
    private int dockY() { return y + panelHeight - TAB_HEIGHT - TAB_PAD * 2 - 7; }

    private void drawDock(GuiGraphicsExtractor g, int mx, int my) {
        Tab selected = tabFor(view, category);
        int left = dockX(selected), top = dockY(), dockWidth = dockWidth(selected);
        Draw.rounded(g, left, top, dockWidth, TAB_HEIGHT + TAB_PAD * 2, 9, HUD_FIELD);
        Draw.roundedOutline(g, left, top, dockWidth, TAB_HEIGHT + TAB_PAD * 2, 9, .6f, lineColor);
        int ty = top + TAB_PAD, tx = left + TAB_PAD;
        float cy = ty + TAB_HEIGHT / 2f;
        for (Tab tab : TABS) {
            if (tab == Tab.COSMETICS) {
                Draw.fillF(g, tx + 3.5f, cy - 6, .5f, 12, lineColor);
                tx += 8;
            }
            int tw = tabWidth(tab, selected);
            boolean active = tab == selected;
            boolean hover = Draw.inside(mx, my, tx, ty, tw, TAB_HEIGHT);
            int color = active ? accentColor : hover ? textColor : mutedColor;
            if (active || hover) Draw.rounded(g, tx, ty, tw, TAB_HEIGHT, 6,
                active ? ColorUtil.alpha(accentColor, 46) : cardHoverColor);
            InterfaceAssets.centeredIcon(g, tab.icon, active ? tx + 10.5f : tx + tw / 2f, cy, 9, color);
            if (active) FigmaFont.drawMedium(g, tab.title, tx + 20, cy - FigmaFont.mediumHeight(.66f) / 2, color, .66f);
            tx += tw;
        }
    }

    private Tab tabAt(double mx, double my) {
        Tab selected = tabFor(view, category);
        int tx = dockX(selected) + TAB_PAD, ty = dockY() + TAB_PAD;
        for (Tab tab : TABS) {
            if (tab == Tab.COSMETICS) tx += 8;
            int tw = tabWidth(tab, selected);
            if (Draw.inside(mx, my, tx, ty, tw, TAB_HEIGHT)) return tab;
            tx += tw;
        }
        return null;
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        int hx = contentX(), cy = y + 19;
        Draw.rounded(g, hx, cy - 10, 20, 20, 6, ColorUtil.alpha(accentColor, 30));
        InterfaceAssets.icon(g, "brand", hx + 3, cy - 7, 14, accentColor);
        FigmaFont.drawMedium(g, "Hustle", hx + 28, cy - FigmaFont.mediumHeight(.94f) / 2, textColor, .94f);
        float focus = animation.animate01("search:focus", search.isFocused() ? 1 : 0, 18, 0);
        int searchColor = Draw.lerp(HUD_FIELD, Draw.lerp(HUD_FIELD, accentColor, .20f), focus);
        Draw.rounded(g, searchBoxX(), search.getY(), searchBoxWidth(), search.getHeight(), 6, searchColor);
        FigmaGuiRenderer.searchIcon(g, searchBoxX() + 6, cy - 4, 8, Draw.lerp(mutedColor, accentColor, focus));
        if (moduleView()) {
            Draw.rounded(g, favoriteX(), y + 9, 20, 20, 6,
                favoritesOnly ? ColorUtil.alpha(accentColor, 46) : HUD_FIELD);
            InterfaceAssets.centeredIcon(g, "like", favoriteX() + 10, cy, 8, favoritesOnly ? accentColor : mutedColor);
        }
        Draw.fillF(g, hx, y + 33, contentWidth(), .5f, lineColor);
    }

    private void photoSwitch(GuiGraphicsExtractor g, float x, float y, float enabled) {
        int knob = ColorUtil.luminance(accentColor) > .8f ? 0xff1a1f1c : 0xfff8faf9;
        Draw.smoothShape(g, x, y, 18, 10, 5, false, Draw.lerp(0x24ffffff, accentColor, enabled));
        Draw.smoothShape(g, x + 1.5f + 8 * enabled, y + 1.5f, 7, 7, 3.5f, false, Draw.lerp(HUD_MUTED, knob, enabled));
    }
    private static float pillWidth(String text) { return FigmaFont.width(text, 6.25f / 11) + 7; }
    private void hudPill(GuiGraphicsExtractor g, String text, float left, float centerY, int background, int color) {
        float scale = 6.25f / 11;
        Draw.roundedF(g, left, centerY - 4.75f, pillWidth(text), 9.5f, 3, background);
        FigmaFont.draw(g, text, left + 3.5f, centerY - FigmaFont.height(scale) / 2, color, scale);
    }
    private String bindLabel(Module module) {
        if (module == binding) return "…";
        return module.hasKey() ? FigmaFont.fit(module.keyName(), 32, .59f) : "";
    }

    private float bindWidth(String name) { return 18 + FigmaFont.width(name, .59f); }
    private float bindRight(ModuleHit hit) { return hit.x + hit.w - (hasSettings(hit.module) ? 44 : 28); }

    private void drawModuleCard(GuiGraphicsExtractor g, ModuleHit hit, int mx, int my, boolean contentHovered) {
        Module module = hit.module;
        boolean headerHover = contentHovered && Draw.inside(mx, my, hit.x, hit.y, hit.w, CARD_H);
        float enabledTarget = module.enabled() ? 1f : 0f;
        float enabled = animation.animate01("module:enabled:" + module.id(), enabledTarget, 22, enabledTarget);
        float over = animation.animate01("module:hover:" + module.id(), headerHover ? 1 : 0, 20, 0);
        int radius = moduleRadius();
        Draw.rounded(g, hit.x, hit.y, hit.w, hit.h, radius, Draw.lerp(cardColor, cardHoverColor, over));
        Draw.roundedOutline(g, hit.x, hit.y, hit.w, hit.h, radius, .65f,
            Draw.lerp(lineColor, ColorUtil.alpha(accentColor, 78), enabled));

        float cy = hit.y + CARD_H / 2f;
        float boxX = hit.x + hit.w - 23;
        Draw.roundedF(g, boxX, cy - 8, 16, 16, 5, Draw.lerp(HUD_FIELD, accentColor, enabled));
        Draw.roundedOutline(g, boxX, cy - 8, 16, 16, 5, .6f, lineColor);
        int ink = ColorUtil.luminance(accentColor) > .55f ? HUD_PANEL : HUD_TEXT;
        InterfaceAssets.centeredIcon(g, "checkmark", boxX + 8, cy, 7, Draw.lerp(0xff52647f, ink, enabled));
        if (hasSettings(module)) {
            InterfaceAssets.centeredIcon(g, expanded.contains(module) ? "arrow_up" : "arrow",
                boxX - 8, cy, 5, mutedColor);
        }

        String key = bindLabel(module);
        float keyRight = bindRight(hit), keyX = keyRight - bindWidth(key);
        Draw.roundedF(g, keyX, cy - 6.5f, bindWidth(key), 13, 4,
            binding == module ? ColorUtil.alpha(accentColor, 65) : HUD_PILL);
        InterfaceAssets.centeredIcon(g, "keyboard", keyX + 7, cy, 8, module.hasKey() ? accentColor : mutedColor);
        if (!key.isEmpty()) FigmaFont.draw(g, key, keyX + 14, cy - FigmaFont.height(.59f) / 2, textColor, .59f);

        int titleWidth = (int)keyX - hit.x - 15;
        String label = FigmaFont.fitMedium(module.name(), titleWidth, .77f);
        boolean described = !module.description().isBlank();
        FigmaFont.drawMedium(g, label, hit.x + 9, hit.y + (described ? 7 : 12), textColor, .77f);
        if (described) {
            String description = FigmaFont.fitRegular(module.description(), titleWidth, .57f);
            FigmaFont.drawRegular(g, description, hit.x + 9, hit.y + 20, mutedColor, .57f);
        }

        float open = expandProgress(module);
        if (open <= 0f) return;
        Draw.fillF(g, hit.x + 6, hit.y + CARD_H, hit.w - 12, .5f, ColorUtil.alpha(0xffffff, Math.round(26 * open)));
        float settingsAlpha = GuiOpacity.push(open);
        try { drawSettings(g, hit, mx, my); } finally { GuiOpacity.restore(settingsAlpha); }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        advancePage(now);
        windowFrame = windowMotion.frame(now);
        if (windowFrame.finished()) { super.onClose(); return; }
        animation.beginFrame(minecraft.getFps());
        float previousOpacity = GuiOpacity.push(visibility());
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), GuiOpacity.color(0x55070b13));
        GuiTransform t = transform();
        g.pose().pushMatrix(); g.pose().translate(t.offsetX(), t.offsetY()); g.pose().scale(t.scale(), t.scale());
        try { drawMenu(g, (int) t.uiX(mouseX), (int) t.uiY(mouseY), delta); }
        finally { GuiOpacity.restore(previousOpacity); g.pose().popMatrix(); }
    }
    private void drawMenu(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        refreshColors();
        drawSurface(g, x, y, panelWidth, panelHeight, WINDOW_RADIUS);
        Draw.roundedOutline(g, x, y, panelWidth, panelHeight, WINDOW_RADIUS, .7f, lineColor);
        drawHeader(g);
        drawDock(g, mouseX, mouseY);
        long now = System.nanoTime();
        frameSeconds = lastFrameNanos == 0 ? 0f : Math.min(.1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        for (Module module : registry.all()) {
            float target = expanded.contains(module) ? 1f : 0f;
            float e = animation.animate01("module:expanded:" + module.id(), target, 18, expandProgress(module));
            if (e == 0f) expandAnim.remove(module); else expandAnim.put(module, e);
        }
        rebuildHitboxes();
        pageFrame = pageMotion.sample(System.nanoTime());
        if (moduleView()) {
            visibleScroll = Math.clamp(visibleScroll, 0, Math.max(0, contentHeight - (contentBottom() - contentTop())));
            visibleScroll += (scroll - visibleScroll) * (1 - (float)Math.exp(-18 * frameSeconds));
            if (Math.abs(scroll - visibleScroll) < .05f) visibleScroll = scroll;
            pageFrame = new GuiPageMotion.Frame(pageFrame.opacity(), pageFrame.x(), pageFrame.y() + scroll - visibleScroll);
        }
        int pageMouseX = (int)pageFrame.localX(mouseX), pageMouseY = (int)pageFrame.localY(mouseY);
        boolean contentHovered = Draw.inside(mouseX, mouseY, contentX(), contentTop(), contentWidth(), contentBottom() - contentTop());
        g.enableScissor(contentX(), contentTop(), contentX() + contentWidth(), contentBottom());
        g.pose().pushMatrix(); g.pose().translate(pageFrame.x(), pageFrame.y());
        float pageOpacity = GuiOpacity.push(pageFrame.opacity());
        try {
            if (view == View.COSMETICS) cosmeticsPage.draw(g, contentX(), contentTop(), contentWidth(), contentBottom() - contentTop(), search.getValue(), pageMouseX, pageMouseY);
            else if (view == View.THEMES) themesPage.style(cardColor, cardHoverColor, moduleRadius(), textColor, mutedColor).draw(g, contentX(), contentTop(), contentWidth(), contentBottom() - contentTop(), search.getValue(), pageMouseX, pageMouseY);
            else if (view == View.CONFIGS) configsPage.draw(g, contentX(), contentTop(), contentWidth(), contentBottom() - contentTop(), search.getValue(), pageMouseX, pageMouseY);
            else if (view == View.EVENTS) eventsPage.draw(g, contentX(), contentTop(), contentWidth(), contentBottom() - contentTop(), search.getValue(), pageMouseX, pageMouseY);
            else if (moduleHits.isEmpty()) {
                String message = search.getValue().isBlank() && !favoritesOnly ? "В этой категории пока нет функций" : "Ничего не найдено";
                FigmaFont.draw(g, message, contentX() + 8, contentTop() + 12, mutedColor, .76f);
            }
            settingHits.clear();
            for (ModuleHit hit : moduleHits) {
                if (hit.y + pageFrame.y() + hit.h <= contentTop() || hit.y + pageFrame.y() >= contentBottom()) continue;
                drawModuleCard(g, hit, pageMouseX, pageMouseY, contentHovered);
            }
        } finally { GuiOpacity.restore(pageOpacity); g.pose().popMatrix(); g.disableScissor(); }
        pageOpacity = GuiOpacity.push(pageFrame.opacity());
        try { drawScrollbar(g); } finally { GuiOpacity.restore(pageOpacity); }
        g.enableScissor(contentX(), contentTop(), contentX() + contentWidth(), contentBottom());
        g.pose().pushMatrix(); g.pose().translate(pageFrame.x(), pageFrame.y());
        pageOpacity = GuiOpacity.push(pageFrame.opacity());
        try { drawDropdown(g, pageMouseX, pageMouseY); }
        finally { GuiOpacity.restore(pageOpacity); g.pose().popMatrix(); g.disableScissor(); }
        if (creatingProfile) drawCreate(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    private float[] scrollbar() {
        int viewport = contentBottom() - contentTop();
        float thumb = Math.max(18, viewport * (float)viewport / Math.max(viewport, contentHeight));
        float top = contentTop() + (viewport - thumb) * visibleScroll / Math.max(1f, contentHeight - viewport);
        return new float[]{contentX() + contentWidth() - 1.25f, top, 1.25f, thumb};
    }
    private void drawScrollbar(GuiGraphicsExtractor g) {
        if (!moduleView() || contentHeight <= contentBottom() - contentTop()) return;
        float[] bar = scrollbar(); Draw.roundedF(g, bar[0], bar[1], bar[2], bar[3], .625f, ColorUtil.alpha(accentColor, 120));
    }
    private void drawDropdown(GuiGraphicsExtractor g, int mx, int my) {
        if (openMode == null || settingHits.stream().noneMatch(h -> h.setting == openMode)) { openMode = null; return; }
        int dropdownH = dropdownRows * 13 + 2;
        Draw.shadow(g, dropdownX, dropdownY + 1, dropdownWidth, dropdownH, 4, .8f, 5);
        Draw.rounded(g, dropdownX, dropdownY, dropdownWidth, dropdownH, 4, ColorUtil.alpha(InterfaceTheme.tint(HUD_PANEL, themeBlur(), .08f), 0xf5));
        for (int i = 0; i < dropdownRows; i++) {
            int oy = dropdownY + 1 + i * 13; String option = openMode.modes().get(dropdownScroll + i);
            boolean selected = openMode.is(option), hot = Draw.inside(mx, my, dropdownX + 1, oy, dropdownWidth - 2, 13);
            if (selected) Draw.roundedF(g, dropdownX + 2, oy + .5f, dropdownWidth - 4, 12, 3, ColorUtil.alpha(accentColor, 0x26));
            else if (hot) Draw.roundedF(g, dropdownX + 2, oy + .5f, dropdownWidth - 4, 12, 3, 0x0effffff);
            boolean multiple=openMode instanceof BooleanGroup;
            FigmaFont.draw(g, FigmaFont.fit(option, dropdownWidth - (multiple?23:10), 6.5f / 11), dropdownX + 5, oy + 4, selected ? accentColor : textColor, 6.5f / 11);
            if(multiple&&selected)InterfaceAssets.centeredIcon(g,"checkmark",dropdownX+dropdownWidth-8,oy+6.5f,6,accentColor);
        }
    }
    private void drawSettings(GuiGraphicsExtractor g, ModuleHit hit, int mouseX, int mouseY) {
        int settingsOffset = CARD_H + 3;
        int top = Math.max(hit.y + settingsOffset, (int)Math.ceil(contentTop() - pageFrame.y()));
        int bottom = Math.min(hit.y + hit.h, (int)Math.floor(contentBottom() - pageFrame.y()));
        if (bottom <= top) return;
        g.enableScissor(hit.x, top, hit.x + hit.w, bottom);
        float px = hit.x + 8, pw = hit.w - 16;
        int ry = hit.y + settingsOffset;
        for (Setting<?> setting : shownSettings(hit.module)) {
            if (!setting.visible()) continue;
            var layout = rowLayout(setting);
            int rh = layout.height();
            boolean clickable = ry >= top && ry + rh <= bottom;
            SettingPresentation.Info info = SettingPresentation.of(setting);
            float labelY = ry + layout.labelY(), labelCenter = ry + layout.labelCenter();
            InterfaceAssets.centeredIcon(g, info.icon(), px+3.5f, labelY + FigmaFont.mediumHeight(7.2f/11)/2, 6.4f,
                    setting instanceof BooleanSetting bool && bool.enabled() ? accentColor : mutedColor);
            for (String line : layout.labels()) {
                FigmaFont.draw(g, line, px + 11, labelY, textColor, 7.2f / 11);
                labelY += SettingRowLayout.LABEL_STEP;
            }
            float helpY = ry + layout.helpY();
            for (String line : layout.help()) {
                FigmaFont.drawRegular(g, line, px, helpY, mutedColor, 6.6f / 11);
                helpY += SettingRowLayout.HELP_STEP;
            }
            switch (setting) {
                case NumberSetting n -> {
                    String value = n.display() + info.unit();
                    hudPill(g, value, px + pw - pillWidth(value), ry + layout.controlY() + FigmaFont.height(6.6f / 11) / 2, HUD_PILL, accentColor);
                    float trackW = pw, ty = ry + layout.sliderY();
                    float fill = trackW * animation.animate("slider:" + System.identityHashCode(n), (float)n.progress(), 22, (float)n.progress());
                    Draw.roundedF(g, px, ty - .1f, trackW, 2, 1, 0x66101412);
                    Draw.roundedF(g, px, ty - .1f, fill, 2, 1, accentColor);
                    Draw.circle(g, px + fill, ty + .9f, 2.6f, 0xfff8faf9);
                    if (clickable) settingHits.add(new SettingHit(n, (int)px - 3, (int)ty - 5, (int)pw + 6, 12, px, trackW));
                }
                case ModeSetting m -> {
                    int bx = (int)(px + pw - 68), by = (int)(ry + layout.controlY()), controlH = (int)Math.ceil(layout.controlHeight());
                    boolean boxHot = Draw.inside(mouseX, mouseY, bx, by, 68, controlH);
                    Draw.roundedF(g, bx, by, 68, controlH, 3, openMode == m ? ColorUtil.alpha(accentColor, 0x30) : boxHot ? 0x40101412 : HUD_FIELD);
                    float valueY = by + 4;
                    for (String line : layout.values()) { FigmaFont.draw(g, line, bx + 5, valueY, textColor, 6.5f / 11); valueY += SettingRowLayout.VALUE_STEP; }
                    InterfaceAssets.icon(g, openMode == m ? "arrow_up" : "arrow", bx + 58, by + (controlH - 6)/2f, 6, mutedColor);
                    if (clickable) settingHits.add(new SettingHit(m, bx, by, 68, controlH, -1, 0));
                    if (openMode == m && clickable) {
                        dropdownWidth = Math.min(contentWidth(), Math.max(68, (int)Math.ceil(m.modes().stream().mapToDouble(v -> FigmaFont.width(v, 6.5f/11)).max().orElse(0)) + (m instanceof BooleanGroup?25:12)));
                        dropdownX = Math.min(bx, contentX() + contentWidth() - dropdownWidth);
                        int spaceBelow = contentBottom() - (by + controlH + 2 + (int)Math.ceil(pageFrame.y()));
                        int spaceAbove = by + (int)Math.floor(pageFrame.y()) - contentTop() - 3;
                        boolean below = spaceBelow >= spaceAbove || spaceBelow >= m.modes().size() * 13 + 2;
                        dropdownRows = Math.clamp((Math.max(spaceBelow, spaceAbove) - 2) / 13, 1, Math.min(9, m.modes().size()));
                        dropdownScroll = Math.clamp(dropdownScroll, 0, m.modes().size() - dropdownRows);
                        dropdownY = below ? by + controlH + 2 : by - dropdownRows * 13 - 3;
                    }
                }
                case BooleanSetting b -> {
                    float state = animation.animate01("setting:" + System.identityHashCode(b), b.enabled() ? 1 : 0, 30, b.enabled() ? 1 : 0);
                    photoSwitch(g, px + pw - 18, labelCenter - 5, state);
                    if (clickable) settingHits.add(new SettingHit(b, (int)px, ry, (int)pw, rh, 0, 0));
                }
                case im.yaHustler.setting.KeySetting k -> {
                    boolean listening=k==actionBinding;
                    String keyText=listening?"...":FigmaFont.fitRegular(k.label(),54,6.5f/11);
                    int kw=Math.clamp((int)Math.ceil(FigmaFont.width(keyText,6.5f/11))+10,22,68);
                    int bx=(int)(px+pw-kw);
                    int by=(int)(ry+layout.controlY());
                    int bh=(int)Math.ceil(layout.controlHeight());
                    Draw.rounded(g,bx,by,kw,bh,3,listening?ColorUtil.alpha(accentColor,0x60):HUD_FIELD);
                    int keyColor=listening?0xffffffff:k.get()==-1?HUD_MUTED:accentColor;
                    FigmaFont.draw(g,keyText,bx+(kw-FigmaFont.width(keyText,6.5f/11))/2f,by+4,keyColor,6.5f/11);
                    if(clickable)settingHits.add(new SettingHit(k,bx,ry,kw,rh,0,0));
                }
                case im.yaHustler.setting.ItemSetting item -> {
                    int bx=(int)(px+pw-68);
                    int by=(int)(ry+layout.controlY());
                    Draw.rounded(g,bx,by,68,(int)Math.ceil(layout.controlHeight()),3,HUD_FIELD);
                    float vy=by+4;
                    for(String line:layout.values()){FigmaFont.draw(g,line,bx+4,vy,textColor,6.5f/11);vy+=SettingRowLayout.VALUE_STEP;}
                    if(clickable)settingHits.add(new SettingHit(item,bx,ry,68,rh,0,0));
                }
                case im.yaHustler.setting.TextSetting text -> {
                    int bx=(int)(px+pw-68),by=(int)(ry+layout.controlY());
                    Draw.rounded(g,bx,by,68,(int)Math.ceil(layout.controlHeight()),3,HUD_FIELD);
                    float vy=by+4;for(String line:layout.values()){FigmaFont.draw(g,line,bx+4,vy,textColor,6.5f/11);vy+=SettingRowLayout.VALUE_STEP;}
                    if(clickable)settingHits.add(new SettingHit(text,bx,ry,68,rh,0,0));
                }
                case ColorSetting c -> {
                    float cx = px + pw - 30, cy = ry + layout.controlY();
                    boolean hot = Draw.inside(mouseX, mouseY, (int)cx, (int)cy, 30, 13);
                    Draw.roundedF(g, cx, cy, 30, 13, 3, hot ? 0x40101412 : HUD_FIELD);
                    Draw.roundedF(g, cx + 2, cy + 2, 17, 9, 2, 0xff343b49);
                    Draw.roundedF(g, cx + 2, cy + 2, 17, 9, 2, c.color());
                    InterfaceAssets.icon(g, "arrow", cx + 22, cy + 4, 5, mutedColor);
                    if (clickable) settingHits.add(new SettingHit(c, (int)cx - 2, ry, 34, rh, 0, 0));
                }
                default -> {}
            }
            ry += rh;
        }
        g.disableScissor();
    }
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (windowMotion.closing()) return true;
        if (actionBinding != null) {
            actionBinding.set(-100 - event.button());
            actionBinding = null;
            return true;
        }
        if (binding != null && event.button() > InputConstants.MOUSE_BUTTON_MIDDLE) {
            binding.setKey(-100 - event.button());
            binding = null;
            return true;
        }
        return clickMenu(localEvent(event), doubleClick);
    }
    private boolean clickMenu(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (pendingView != null && Draw.inside(mx, my, contentX(), y + HEADER, contentWidth(), contentBottom() - y - HEADER)) return true;
        double pageMx = pageFrame.localX(mx), pageMy = pageFrame.localY(my);
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (openMode != null && left && Draw.inside(mx, my, contentX(), contentTop(), contentWidth(), contentBottom() - contentTop())) {
            if (Draw.inside(pageMx, pageMy, dropdownX, dropdownY, dropdownWidth, dropdownRows * 13 + 2)) {
                int row = Math.clamp((int)(pageMy - dropdownY - 1) / 13, 0, dropdownRows - 1);
                openMode.set(openMode.modes().get(dropdownScroll + row)); if(!(openMode instanceof BooleanGroup))openMode = null; return true;
            }
            boolean onBox = settingHits.stream().anyMatch(h -> h.setting == openMode && Draw.inside(pageMx, pageMy, h.x, h.y, h.w, h.h));
            if (!onBox) openMode = null;
        }
        if (left && moduleView() && contentHeight > contentBottom() - contentTop() && mx >= contentX() + contentWidth() - 4 && mx <= contentX() + contentWidth() + 2 && my >= contentTop() && my < contentBottom()) {
            float[] bar = scrollbar(); draggingScrollbar = true;
            scrollGrab = my >= bar[1] && my <= bar[1] + bar[3] ? (float)my - bar[1] : bar[3] / 2;
            updateScrollbar(my); return true;
        }
        if (creatingProfile) {
            if (!left) return true;
            if (Draw.inside(mx, my, dialogX() + 12, dialogY() + 71, 103, 19)) { saveCreatedProfile(); return true; }
            if (Draw.inside(mx, my, dialogX() + 124, dialogY() + 71, 104, 19)) { closeCreate(); return true; }
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
            rebuildHitboxes();
            if (Draw.inside(mx, my, contentX(), contentTop(), contentWidth(), contentBottom() - contentTop())) {
                for (SettingHit hit : settingHits) {
                    if (hit.setting instanceof im.yaHustler.setting.KeySetting k && Draw.inside(pageMx, pageMy, hit.x, hit.y, hit.w, hit.h)) {
                        binding = null; actionBinding = k; clearFocus(); return true;
                    }
                }
                for (ModuleHit hit : moduleHits) {
                    if (Draw.inside(pageMx, pageMy, hit.x, hit.y, hit.w, CARD_H)) {
                        binding = binding == hit.module ? null : hit.module;
                        clearFocus(); return true;
                    }
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
        Module previousBinding = binding;
        binding = null;
        if (!left && !right) return super.mouseClicked(event, doubleClick);
        rebuildHitboxes();
        boolean inContent = Draw.inside(mx, my, contentX(), contentTop(), contentWidth(), contentBottom() - contentTop());
        if (right) {
            if (inContent) {
                for (ModuleHit hit : moduleHits) {
                    if (Draw.inside(pageMx, pageMy, hit.x, hit.y, hit.w, CARD_H)) {
                        if (overBind(hit, pageMx, pageMy)) hit.module.setKey(-1);
                        else if (hasSettings(hit.module)) toggleExpanded(hit.module);
                        clearFocus(); return true;
                    }
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
        Tab tab = tabAt(mx, my);
        if (tab != null) {
            clearFocus();
            if (tab == Tab.BOTS) minecraft.gui.setScreen(new BotScreen(this));
            else navigate(tab.view, tab.category == null ? category : tab.category);
            return true;
        }
        if (moduleView() && Draw.inside(mx, my, favoriteX(), y + 9, 20, 20)) {
            favoritesOnly = !favoritesOnly;
            scroll = 0;
            visibleScroll = 0;
            clearFocus();
            return true;
        }
        if (inContent && view == View.COSMETICS) { clearFocus(); return cosmeticsPage.click(pageMx,pageMy,event.button()); }
        if (inContent && view == View.EVENTS) return eventsPage.click(pageMx, pageMy);
        if (inContent && view == View.THEMES) return themesPage.click(pageMx, pageMy, () -> minecraft.gui.setScreen(new ThemePickerScreen(this)));
        if (inContent && view == View.CONFIGS) return configsPage.click(pageMx, pageMy, this::createProfile);
        if (inContent) {
            for (SettingHit hit : settingHits) {
                if (!Draw.inside(pageMx, pageMy, hit.x, hit.y, hit.w, hit.h)) continue;
                switch (hit.setting) {
                    case BooleanSetting b -> b.toggle();
                    case ModeSetting m -> { openMode = openMode == m ? null : m; dropdownScroll = 0; }
                    case NumberSetting n -> {
                        draggingSlider = n; sliderX = hit.trackX; sliderW = hit.trackW;
                        n.setProgress((pageMx - sliderX) / sliderW, false);
                    }
                    case im.yaHustler.setting.KeySetting k -> {actionBinding=k;}
                    case im.yaHustler.setting.ItemSetting item -> {if(minecraft.player!=null)item.capture(minecraft.player.getMainHandItem());}
                    case im.yaHustler.setting.TextSetting text -> minecraft.gui.setScreen(new TextSettingScreen(this,text));
                    case ColorSetting c -> minecraft.gui.setScreen(new ThemePickerScreen(this, c));
                    default -> {}
                }
                clearFocus(); return true;
            }
            for (ModuleHit hit : moduleHits) {
                if (!Draw.inside(pageMx, pageMy, hit.x, hit.y, hit.w, hit.h)) continue;
                if (pageMy < hit.y + CARD_H) {
                    if (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT))
                        hit.module.setFavorite(!hit.module.favorite());
                    else if (overBind(hit, pageMx, pageMy)) binding = previousBinding == hit.module ? null : hit.module;
                    else if (hit.module instanceof im.yaHustler.module.impl.render.ClickGui
                        || hasSettings(hit.module) && pageMx >= hit.x + hit.w - 40 && pageMx < hit.x + hit.w - 24) {
                        toggleExpanded(hit.module);
                    } else hit.module.toggle();
                }
                clearFocus(); return true;
            }
        }
        if (left && search.visible && Draw.inside(mx, my, searchBoxX(), search.getY(), searchBoxWidth(), search.getHeight())
            && !search.isMouseOver(mx, my)) {
            setFocused(search); search.setFocused(true); return true;
        }
        if (left && Draw.inside(mx, my, x, y, panelWidth, 28) && !search.isMouseOver(mx, my)) { draggingWindow = true; clearFocus(); return true; }
        if (!search.isMouseOver(mx, my)) clearFocus();
        return super.mouseClicked(event, doubleClick);
    }
    private boolean overBind(ModuleHit hit, double mx, double my) {
        float right = bindRight(hit), w = bindWidth(bindLabel(hit.module));
        return Draw.inside(mx, my, right - w, hit.y + (CARD_H - 13) / 2f, w, 13);
    }
    private void updateScrollbar(double mouseY) {
        float[] bar = scrollbar(); float travel = contentBottom() - contentTop() - bar[3];
        scroll = Math.round(Math.clamp(((float)mouseY - contentTop() - scrollGrab) / Math.max(1, travel), 0, 1) * Math.max(0, contentHeight - (contentBottom() - contentTop())));
        visibleScroll = scroll;
    }
    @Override
    public boolean mouseDragged(MouseButtonEvent raw, double dx, double dy) {
        if (windowMotion.closing() || pendingView != null) return true;
        MouseButtonEvent event = localEvent(raw); dx /= transform().scale(); dy /= transform().scale();
        if(view==View.COSMETICS && cosmeticsPage.drag(pageFrame.localX(event.x()),pageFrame.localY(event.y()),dx,dy))return true;
        if (draggingScrollbar) { updateScrollbar(event.y()); return true; }
        if (draggingWindow) {
            x += (int) Math.round(dx); y += (int) Math.round(dy);
            windowDx = x - (width - panelWidth) / 2; windowDy = y - (height - panelHeight) / 2;
            search.setX(searchBoxX() + 20); search.setY(y + 9);
            return true;
        }
        if (draggingSlider != null) {
            draggingSlider.setProgress((pageFrame.localX(event.x()) - sliderX) / sliderW, false);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override
    public boolean mouseReleased(MouseButtonEvent raw) {
        MouseButtonEvent event = localEvent(raw);
        if(cosmeticsPage.release())return true;
        if (draggingScrollbar) { draggingScrollbar = false; return true; }
        if (draggingWindow) { draggingWindow = false; return true; }
        if (draggingSlider != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            draggingSlider.set(draggingSlider.get());
            draggingSlider = null;
            return true;
        }
        return super.mouseReleased(event);
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (windowMotion.closing()) return true;
        if (resizeGesture(vertical)) return true;
        GuiTransform t = transform(); mx = t.uiX(mx); my = t.uiY(my);
        if (creatingProfile) return true;
        if (openMode != null && Draw.inside(pageFrame.localX(mx), pageFrame.localY(my), dropdownX, dropdownY, dropdownWidth, dropdownRows * 13 + 2)) {
            dropdownScroll = Math.clamp(dropdownScroll - (int)Math.signum(vertical) * 3, 0, Math.max(0, openMode.modes().size() - dropdownRows)); return true;
        }
        if (Draw.inside(mx, my, contentX(), contentTop(), contentWidth(), contentBottom() - contentTop())) {
            if (pendingView != null) return true;
            if(view==View.COSMETICS){cosmeticsPage.scroll(pageFrame.localX(mx),pageFrame.localY(my),vertical);return true;}
            if (view == View.EVENTS) { eventsPage.scroll(vertical); return true; }
            if (view == View.THEMES) { themesPage.scroll(vertical); return true; }
            if (view == View.CONFIGS) { configsPage.scroll(vertical); return true; }
            scroll = Math.clamp(scroll - (int) (vertical * 20), 0,
                Math.max(0, contentHeight - (contentBottom() - contentTop())));
            rebuildHitboxes(); return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (creatingProfile) {
            if (event.key() == InputConstants.KEY_ESCAPE) { closeCreate(); return true; }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) { saveCreatedProfile(); return true; }
            return super.keyPressed(event);
        }
        if(actionBinding!=null) {
            int key=event.key();
            if(key==InputConstants.KEY_ESCAPE||key==InputConstants.KEY_DELETE||key==InputConstants.KEY_BACKSPACE)actionBinding.set(-1);
            else actionBinding.set(key);
            actionBinding=null;return true;
        }
        if (binding != null) {
            int key = event.key();
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) binding.setKey(-1);
            else binding.setKey(key);
            binding = null;
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (search.isFocused() || !search.getValue().isEmpty()) {
                search.setValue(""); clearFocus(); return true;
            }
            if (openMode != null) { openMode = null; return true; }
        }
        if (Hustle26.OPEN_GUI.matches(event)) { onClose(); return true; }
        if (event.hasControlDown() && event.key() == InputConstants.KEY_F) {
            setFocused(search); search.setFocused(true); return true;
        }
        return super.keyPressed(event);
    }
    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }
    @Override public void onClose() {
        cosmeticsPage.release();
        if (draggingSlider != null) { draggingSlider.set(draggingSlider.get()); draggingSlider = null; }
        if (search != null) search.setFocused(false);
        if (profileName != null) profileName.setFocused(false);
        windowMotion.closeFrame(System.nanoTime());
    }
    @Override public void removed() { if (search != null) search.setFocused(false); if (profileName != null) profileName.setFocused(false); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
    private record SettingHit(Setting<?> setting, int x, int y, int w, int h, float trackX, float trackW) {}
    private record ModuleHit(Module module, int x, int y, int w, int h) {}
}
