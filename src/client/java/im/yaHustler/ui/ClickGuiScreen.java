package im.yaHustler.ui;

import com.mojang.blaze3d.platform.InputConstants;
import im.yaHustler.Hustle26;
import im.yaHustler.font.Fonts;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.font.Icons;
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
    private final java.util.Map<Object, Float> toggleAnim = new java.util.HashMap<>();
    private long lastFrameNanos;
    private float frameSeconds;
    private static final int HEADER = 38, CARD_H = 28, GAP_Y = 6;
    private static final int HUD_PANEL = 0xff171816, HUD_FIELD = 0x30101412, HUD_PILL = 0x32101412,
        HUD_TEXT = 0xfff1f3ef, HUD_MUTED = 0xffa8afa9, HUD_RULE = 0x12ffffff;
    private static final int NUMBER_H = 32, MODE_H = 27, BOOL_H = 26, COLOR_H = 26, SETTINGS_PAD = 6;
    private int textColor, mutedColor, dimColor, accentColor, accent2Color, lineColor, cardColor, cardHoverColor,
        descColor, labelColor, expandedColor, fieldColor, fieldHoverColor, popupBorderColor, popupColor;
    private enum View { MODULES, COSMETICS, THEMES, CONFIGS, EVENTS }
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
    private final String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    private static final Category[] COLUMNS = {Category.COMBAT, Category.MOVEMENT, Category.PLAYER, Category.RENDER, Category.MISC};
    private static final int[] NAV_Y = {47, 63, 79, 95, 111};
    private int scroll, contentHeight;
    private float visibleScroll;
    private int columnWidth, columnGap, sidebarWidth;
    private SmoothSearchBox search;
    private int x, y, panelWidth, panelHeight;
    private boolean favoritesOnly;
    private long visibleRevision = -1;
    private String visibleQuery;
    private Category visibleCategory;
    private boolean visibleFavorites;
    private List<Module> visibleModules = List.of();
    private final List<CategoryHit> categoryHits = new ArrayList<>();
    private final List<ModuleHit> moduleHits = new ArrayList<>();
    private List<Module> layoutModules;
    private int[] layoutHeights = new int[0];
    private ModuleGridLayout.Placement[] layoutCards = new ModuleGridLayout.Placement[0];
    private int layoutX = Integer.MIN_VALUE, layoutY, layoutWidth, layoutSidebar, layoutPanelHeight, layoutScroll;
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
    private int pageIndex() { return pageIndex(view, category); }
    private static int pageIndex(View view, Category category) {
        if (view == View.COSMETICS) return 5;
        if (view == View.CONFIGS) return 6;
        if (view == View.THEMES) return 7;
        if (view == View.EVENTS) return 8;
        for (int i = 0; i < COLUMNS.length; i++) if (COLUMNS[i] == category) return i;
        return 0;
    }
    private void navigate(View nextView, Category nextCategory) {
        if (pendingView == nextView && pendingCategory == nextCategory) return;
        if (pendingView == null && view == nextView && category == nextCategory) return;
        cosmeticsPage.release();
        pendingView = nextView;
        pendingCategory = nextCategory;
        pendingDirection = pageIndex(nextView, nextCategory) >= pageIndex() ? 1 : -1;
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
        for (Module module : registry.all()) {
            if (!module.enabled()) { expanded.remove(module); continue; }
            if (hasSettings(module)) { expanded.add(module); expandAnim.put(module, 1f); }
        }
    }
    @Override
    protected void init() {
        String previousQuery = search == null ? "" : search.getValue();
        if (search != null) search.setFocused(false);
        panelWidth = 472; panelHeight = 324;
        sidebarWidth = 100; columnGap = 8;
        columnWidth = (panelWidth - sidebarWidth - 16 - columnGap) / 2;
        x = (width - panelWidth) / 2 + windowDx;
        y = (height - panelHeight) / 2 + windowDy;
        search = new SmoothSearchBox(searchBoxX() + 16, y + 6,
            searchBoxWidth() - 22, 16, "Поиск модулей", "Search modules...");
        search.textScale(6.7f / 11);
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
    private int searchBoxWidth() { return 104; }
    private int searchBoxX() { return x + panelWidth - 10 - searchBoxWidth(); }
    private int contentX() { return x + sidebarWidth + 8; }
    private int contentTop() { return y + HEADER; }
    private int contentBottom() { return y + panelHeight - 4; }
    private int contentWidth() { return panelWidth - sidebarWidth - 16; }
    private int systemY() { return y + panelHeight - 132; }
    private int cosmeticsY() { return systemY() + 12; }
    private int configY() { return systemY() + 28; }
    private int themeY() { return systemY() + 44; }
    private int eventsY() { return systemY() + 60; }
    private int botsY() { return systemY() + 76; }
    private static String categoryTitle(Category cat) { return cat == Category.THEMES ? "Theme" : cat.label; }
    private List<Module> visible() {
        if (!moduleView()) return List.of();
        String query = search.getValue();
        long revision = registry.revision();
        if (revision != visibleRevision) {
            expanded.retainAll(registry.all());
            expandAnim.keySet().retainAll(registry.all());
            toggleAnim.keySet().retainAll(registry.all());
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
            InterfaceTheme.alpha(InterfaceTheme.tint(HUD_PANEL, themeBlur(), .08f), optionsOpacity() * .68f));
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
    private void openConfigs() { navigate(View.CONFIGS, category); }
    private void openThemes() { navigate(View.THEMES, category); }
    private void createProfile() { creatingProfile = true; createError = ""; profileName.visible = profileName.active = true; profileName.setValue(""); search.visible = search.active = false; setFocused(profileName); profileName.setFocused(true); }
    private void closeCreate() { creatingProfile = false; profileName.visible = profileName.active = false; profileName.setFocused(false); search.visible = search.active = true; clearFocus(); }
    private void saveCreatedProfile() { createError = configsPage.create(profileName.getValue().strip()); if (createError.isEmpty()) closeCreate(); }
    private void drawCreate(GuiGraphicsExtractor g, int mx, int my) {
        Draw.rounded(g, x, y, panelWidth, panelHeight, 16, 0x80000000);
        int dx = dialogX(), dy = dialogY();
        Draw.rounded(g, dx - 1, dy - 1, 242, 100, 9, ColorUtil.alpha(accentColor, 120));
        Draw.rounded(g, dx, dy, 240, 98, 8, ColorUtil.opaque(FigmaGuiRenderer.panelColor()));
        FigmaFont.drawMedium(g, "Создать конфиг", dx + 12, dy + 12, textColor, .95f);
        Draw.rounded(g, dx + 10, dy + 30, 220, 22, 5, fieldColor);
        FigmaFont.draw(g, FigmaFont.fit(createError, 216, .5f), dx + 12, dy + 57, 0xffffa0ad, .5f);
        Draw.rounded(g, dx + 12, dy + 71, 103, 19, 5, accentColor);
        Draw.rounded(g, dx + 124, dy + 71, 104, 19, 5, fieldColor);
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
        boolean moved = layoutX != x || layoutY != y || layoutWidth != columnWidth
            || layoutSidebar != sidebarWidth || layoutPanelHeight != panelHeight;
        if (moved) {
            categoryHits.clear();
            for (int c = 0; c < COLUMNS.length; c++)
                categoryHits.add(new CategoryHit(COLUMNS[c], x + 7, y + NAV_Y[c], sidebarWidth - 14, 16));
            categoryHits.add(new CategoryHit(Category.COSMETICS, x + 7, cosmeticsY(), sidebarWidth - 14, 16));
            categoryHits.add(new CategoryHit(Category.THEMES, x + 7, themeY(), sidebarWidth - 14, 16));
        }
        List<Module> modules = visible();
        boolean changed = modules != layoutModules;
        if (layoutHeights.length != modules.size()) { layoutHeights = new int[modules.size()]; changed = true; }
        for (int i = 0; i < modules.size(); i++) {
            int height = cardHeight(modules.get(i));
            if (layoutHeights[i] != height) { layoutHeights[i] = height; changed = true; }
        }
        if (changed) {
            layoutCards = ModuleGridLayout.pack(layoutHeights, GAP_Y);
            layoutModules = modules;
            contentHeight = ModuleGridLayout.contentHeight(layoutCards);
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - (contentBottom() - contentTop())));
        if (!changed && !moved && layoutScroll == scroll) return;
        layoutX = x; layoutY = y; layoutWidth = columnWidth; layoutSidebar = sidebarWidth;
        layoutPanelHeight = panelHeight; layoutScroll = scroll;
        moduleHits.clear();
        for (int i = 0; i < modules.size(); i++) {
            var card = layoutCards[i];
            moduleHits.add(new ModuleHit(modules.get(i), contentX() + card.column() * (columnWidth + columnGap), contentTop() + card.top() - scroll, columnWidth, card.height()));
        }
    }
    private void refreshColors() {
        textColor = HUD_TEXT;
        mutedColor = descColor = labelColor = HUD_MUTED;
        dimColor = 0xff98a59c;
        accentColor = accent2Color = FigmaGuiRenderer.accent();
        lineColor = HUD_RULE;
        cardColor = InterfaceTheme.alpha(0x0cffffff, optionsOpacity());
        cardHoverColor = InterfaceTheme.alpha(0x15ffffff, optionsOpacity());
        expandedColor = cardColor;
        fieldColor = 0x70151a17;
        fieldHoverColor = Draw.lerp(fieldColor, accentColor, .12f);
        popupBorderColor = ColorUtil.alpha(accentColor, 40);
        popupColor = 0xf019201c;
    }
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
    }
    public float[][] backdropSurfaces() { GuiTransform t = transform(); float[] main = {t.screenX(x), t.screenY(y), panelWidth * t.scale(), panelHeight * t.scale()};
        if (minecraft.gui.screen() instanceof ThemePickerScreen picker) return new float[][]{main, picker.popupSurface()};
        if (minecraft.gui.screen() instanceof HudEditorScreen editor) return new float[][]{main, editor.popupSurface()};
        return new float[][]{main}; }
    public float backdropRadius() { return 7 * transform().scale(); }
    private void navItem(GuiGraphicsExtractor g, int nx, int ny, int nw, int nh, String label, String icon,
                         Category cat, boolean selected, int mx, int my) {
        boolean hot = Draw.inside(mx, my, nx, ny, nw, nh);
        float hover = animation.animate01("nav:hover:" + label, hot ? 1 : 0, 22, 0);
        if (!selected && hover > .001f) Draw.roundedF(g, nx, ny, nw, nh, 4, ColorUtil.alpha(0x101412, Math.round(34 * hover)));
        int color = selected ? textColor : Draw.lerp(mutedColor, 0xffd9ddda, hover);
        String texture = cat == null ? (label.equals("Events") ? "activity" : "config") : switch (cat) {
            case COMBAT -> "combat"; case MOVEMENT -> "movement"; case RENDER -> "visuals";
            case PLAYER -> "player"; case MISC -> "misc"; case THEMES -> "theme"; case COSMETICS, BOTS -> "user";
        };
        InterfaceAssets.icon(g, texture, nx + 5.5f, ny + 4.5f, 7, selected ? accentColor : color);
        float labelY = ny + (nh - FigmaFont.mediumHeight(7f / 11)) / 2;
        if (selected) FigmaFont.drawMedium(g, label, nx + 18, labelY, color, 7f / 11);
        else FigmaFont.draw(g, label, nx + 18, labelY, color, 7f / 11);
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
    private static String plural(int n, String one, String few, String many) {
        int mod10 = n % 10, mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return one;
        return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many;
    }
    private String moduleLabel(Module module, int cardWidth) {
        String keyName = module == binding ? "..." : module.keyName();
        int tagWidth = keyName.isEmpty() ? 0 : (int)Math.ceil(pillWidth(keyName)) + 5;
        return FigmaFont.fitMedium(module.name(), cardWidth - 47 - tagWidth, 8.1f / 11);
    }
    private void moduleSwitch(GuiGraphicsExtractor g, ModuleHit hit, float enabled) {
        float nameCenter = hit.y + 7 + FigmaFont.mediumHeight(8.1f / 11) / 2;
        photoSwitch(g, hit.x + hit.w - 25, nameCenter - 5, enabled);
    }
    private void sidebarLogo(GuiGraphicsExtractor g) {
        float nameScale = 8.5f / 11, versionScale = 6.5f / 11;
        float nameWidth = FigmaFont.mediumWidth("Hustle 26.3", nameScale);
        float groupWidth = 20 + 6 + nameWidth + 4 + FigmaFont.width("26.3", versionScale);
        float left = x + (sidebarWidth - groupWidth) / 2, cy = y + 16;
        InterfaceAssets.icon(g, "brand", left, cy - 10, 20, accentColor);
        FigmaFont.drawMedium(g, "Hustle 26.3", left + 26, cy - FigmaFont.mediumHeight(nameScale) / 2,
            textColor, nameScale);
        FigmaFont.draw(g, "26.3", left + 30 + nameWidth, cy - FigmaFont.mediumHeight(versionScale) / 2,
            mutedColor, versionScale);
    }
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        advancePage(now);
        windowFrame = windowMotion.frame(now);
        if (windowFrame.finished()) { super.onClose(); return; }
        animation.beginFrame(minecraft.getFps());
        GuiTransform t = transform();
        g.pose().pushMatrix(); g.pose().translate(t.offsetX(), t.offsetY()); g.pose().scale(t.scale(), t.scale());
        float previousOpacity = GuiOpacity.push(visibility());
        try { drawMenu(g, (int) t.uiX(mouseX), (int) t.uiY(mouseY), delta); }
        finally { GuiOpacity.restore(previousOpacity); g.pose().popMatrix(); }
    }
    private void drawMenu(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        refreshColors();
        float opacity = optionsOpacity();
        drawSurface(g, x, y, panelWidth, panelHeight, 7);
        g.enableScissor(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1);
        Draw.roundedF(g, x + 1, y + 1, sidebarWidth - 1, panelHeight - 2, 6, InterfaceTheme.alpha(0x1f101412, opacity));
        Draw.fillF(g, x + sidebarWidth, y + 1, .5f, panelHeight - 2, lineColor);
        sidebarLogo(g);
        FigmaFont.draw(g, "MODULES", x + 11, y + 38.5f, ColorUtil.alpha(mutedColor, 150), 5f / 11);
        FigmaFont.draw(g, "CLIENT", x + 11, systemY() + .5f, ColorUtil.alpha(mutedColor, 150), 5f / 11);
        rebuildHitboxes();
        float selectedY = view == View.EVENTS ? eventsY() : configY();
        for (CategoryHit hit : categoryHits)
            if (hit.category == Category.THEMES ? view == View.THEMES : hit.category == Category.COSMETICS ? view == View.COSMETICS : view == View.MODULES && category == hit.category) selectedY = hit.y;
        float activeY = animation.animate("nav:selection:y", selectedY, 24, selectedY);
        Draw.roundedF(g, x + 7, activeY, sidebarWidth - 14, 16, 4, InterfaceTheme.alpha(0x43101412, opacity));
        for (CategoryHit hit : categoryHits) navItem(g, hit.x, hit.y, hit.w, hit.h,
            categoryTitle(hit.category), hit.category.icon, hit.category, hit.category == Category.THEMES ? view == View.THEMES : hit.category == Category.COSMETICS ? view == View.COSMETICS : view == View.MODULES && category == hit.category, mouseX, mouseY);
        navItem(g, x + 7, configY(), sidebarWidth - 14, 16, "Configs", Icons.UTILITY, null, view == View.CONFIGS, mouseX, mouseY);
        navItem(g, x + 7, eventsY(), sidebarWidth - 14, 16, "Events", Icons.UTILITY, null, view == View.EVENTS, mouseX, mouseY);
        navItem(g, x + 7, botsY(), sidebarWidth - 14, 16, Category.BOTS.label, Category.BOTS.icon, Category.BOTS, false, mouseX, mouseY);
        int userY = y + panelHeight - 29;
        Draw.fillF(g, x + 9, userY - 7, sidebarWidth - 18, .5f, lineColor);
        if (minecraft.player != null) {
            var skin = minecraft.player.getSkin().body().texturePath();
            g.pose().pushMatrix(); g.pose().translate(x + 11, userY); g.pose().scale(2, 2);
            for (int u : new int[]{8, 40}) im.yaHustler.ui.GuiTextures.blit(g, im.yaHustler.font.MsdfPipeline.head(), skin,
                0, 0, u, 8, 8, 8, 8, 8, 64, 64, GuiOpacity.color(0xffffffff));
            g.pose().popMatrix();
            FigmaFont.drawMedium(g, FigmaFont.fitMedium(minecraft.player.getName().getString(), sidebarWidth - 45, 7f / 11), x + 32, userY + 2, textColor, 7f / 11);
        }
        FigmaFont.draw(g, today, x + 32, userY + 11, ColorUtil.alpha(mutedColor, 190), 6f / 11);
        String title = view == View.COSMETICS ? "Cosmetics" : view == View.THEMES ? "Theme" : view == View.CONFIGS ? "Configs" : view == View.EVENTS ? "Events" : categoryTitle(category);
        String headerIcon = view == View.MODULES ? switch (category) { case COMBAT -> "combat"; case MOVEMENT -> "movement"; case PLAYER -> "player"; case RENDER -> "visuals"; default -> "misc"; } : view == View.COSMETICS ? "user" : view == View.THEMES ? "theme" : view == View.CONFIGS ? "config" : "activity";
        int headerX = contentX();
        float titleScale = 7.8f / 11;
        InterfaceAssets.centeredIcon(g, headerIcon, headerX + 5, y + 14, 9, accentColor);
        FigmaFont.drawMedium(g, title, headerX + 15, y + 14 - FigmaFont.mediumHeight(titleScale) / 2, textColor, titleScale);
        float searchFocus = animation.animate01("search:focus", search.isFocused() ? 1 : 0, 18, 0);
        Draw.searchSurface(g, searchBoxX(), search.getY(), searchBoxWidth(), search.getHeight(),
            InterfaceTheme.alpha(Draw.lerp(0x3e101412, ColorUtil.alpha(accentColor, 0x48), searchFocus), opacity));
        int searchIconColor = Draw.lerp(mutedColor, accentColor, searchFocus);
        FigmaGuiRenderer.searchIcon(g, searchBoxX() + 4, search.getY() + (search.getHeight() - 9) / 2f, 9, searchIconColor);
        Draw.fillF(g, x + sidebarWidth + 1, y + 28, panelWidth - sidebarWidth - 2, .5f, lineColor);
        g.disableScissor();
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
                boolean hover = contentHovered && Draw.inside(pageMouseX, pageMouseY, hit.x, hit.y, hit.w, hit.h);
                float open = expandProgress(hit.module);
                boolean headerHover = hover && pageMouseY < hit.y + CARD_H;
                float targetEnabled = hit.module.enabled() ? 1f : 0f;
                float enabled = toggleAnim.getOrDefault(hit.module, targetEnabled);
                enabled = animation.animate01("module:enabled:" + hit.module.id(), targetEnabled, 22, targetEnabled);
                toggleAnim.put(hit.module, enabled);
                float cardHover = animation.animate01("module:hover:" + hit.module.id(), headerHover ? 1 : 0, 20, 0);
                Draw.rounded(g, hit.x, hit.y, hit.w, hit.h, moduleRadius(), Draw.lerp(cardColor, cardHoverColor, cardHover));
                Draw.roundedOutline(g, hit.x, hit.y, hit.w, hit.h, moduleRadius(), 0.5f, 0x18ffffff);
                String keyName = hit.module == binding ? "..." : hit.module.keyName();
                float nameScale = 8.1f / 11;
                int tagW = keyName.isEmpty() ? 0 : (int) Math.ceil(FigmaFont.width(keyName, 6.5f / 11)) + 5;
                String moduleLabel = moduleLabel(hit.module, hit.w);
                FigmaFont.drawMedium(g, moduleLabel, hit.x + 7, hit.y + 7, Draw.lerp(0xffcdd2ce, textColor, Math.max(enabled, headerHover ? 1 : 0)), nameScale);
                float nameEnd = hit.x + 7 + FigmaFont.mediumWidth(moduleLabel, nameScale);
                if (tagW > 0) {
                    boolean listening = hit.module == binding;
                    hudPill(g, keyName, hit.x + hit.w - 30 - pillWidth(keyName), hit.y + 7 + FigmaFont.mediumHeight(nameScale) / 2,
                        listening ? ColorUtil.alpha(accentColor, 0x60) : HUD_PILL, listening ? 0xffffffff : accentColor);
                }
                FigmaFont.drawRegular(g, FigmaFont.fitRegular(hit.module.description(), hit.w - 18, 6.6f / 11), hit.x + 7, hit.y + 18, descColor, 6f / 11);
                moduleSwitch(g, hit, enabled);
                if (open > 0f) {
                    Draw.fillF(g, hit.x + 6, hit.y + CARD_H, hit.w - 12, .5f, ColorUtil.alpha(0xffffffff, Math.round(18 * open)));
                    float settingsAlpha = GuiOpacity.push(open);
                    try { drawSettings(g, hit, pageMouseX, pageMouseY); } finally { GuiOpacity.restore(settingsAlpha); }
                }
            }
        } finally { GuiOpacity.restore(pageOpacity); g.pose().popMatrix(); g.disableScissor(); }
        pageOpacity = GuiOpacity.push(pageFrame.opacity());
        try { drawScrollbar(g); } finally { GuiOpacity.restore(pageOpacity); }
        g.enableScissor(x + sidebarWidth + 1, y + 29, x + panelWidth - 1, y + panelHeight - 1);
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
        int top = Math.max(hit.y + settingsOffset, contentTop()), bottom = Math.min(hit.y + hit.h, contentBottom());
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
                        dropdownX = Math.min(bx, x + panelWidth - 4 - dropdownWidth); dropdownRows = Math.min(9, m.modes().size()); dropdownY = by + controlH + 2;
                        if (dropdownY + dropdownRows * 13 + 2 > y + panelHeight - 3) dropdownY = by - dropdownRows * 13 - 3;
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
        GuiTransform t = transform();
        var hud = registry.get(im.yaHustler.module.impl.render.Hud.class);
        if (hud != null && !Draw.inside(t.uiX(event.x()), t.uiY(event.y()), x, y, panelWidth, panelHeight)) {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && hud.openElementSettings(event.x(), event.y())) return true;
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && hud.beginDrag(event.x(), event.y())) return true;
        }
        if (windowMotion.closing()) return true;
        if(actionBinding!=null) { actionBinding.set(-100-event.button());actionBinding=null;return true; }
        return clickMenu(localEvent(event), doubleClick);
    }
    private boolean clickMenu(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (pendingView != null && Draw.inside(mx, my, contentX(), y + HEADER, contentWidth(), contentBottom() - y - HEADER)) return true;
        double pageMx = pageFrame.localX(mx), pageMy = pageFrame.localY(my);
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (openMode != null && left) {
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
        binding = null;
        if (!left && !right) return super.mouseClicked(event, doubleClick);
        rebuildHitboxes();
        boolean inContent = Draw.inside(mx, my, contentX(), contentTop(), contentWidth(), contentBottom() - contentTop());
        if (right) {
            if (inContent) {
                for (ModuleHit hit : moduleHits) {
                    if (Draw.inside(pageMx, pageMy, hit.x, hit.y, hit.w, CARD_H)) {
                        if (hasSettings(hit.module)) toggleExpanded(hit.module);
                        clearFocus(); return true;
                    }
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (Draw.inside(mx, my, x + 7, configY(), sidebarWidth - 14, 16)) {
            clearFocus(); openConfigs(); return true;
        }
        if (Draw.inside(mx, my, x + 7, eventsY(), sidebarWidth - 14, 16)) {
            clearFocus(); navigate(View.EVENTS, category); return true;
        }
        if (Draw.inside(mx, my, x + 7, botsY(), sidebarWidth - 14, 16)) {
            clearFocus();
            minecraft.gui.setScreen(new BotScreen(this));
            return true;
        }
        for (CategoryHit hit : categoryHits) {
            if (Draw.inside(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                clearFocus();
                if (hit.category == Category.THEMES) openThemes();
                else if (hit.category == Category.COSMETICS) navigate(View.COSMETICS, category);
                else {
                    navigate(View.MODULES, hit.category);
                }
                return true;
            }
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
                    else {
                        if (hit.module instanceof im.yaHustler.module.impl.render.ClickGui) toggleExpanded(hit.module);
                        else {
                            hit.module.toggle();
                            if (hasSettings(hit.module)) { if (hit.module.enabled()) expanded.add(hit.module); else expanded.remove(hit.module); }
                        }
                    }
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
    private void updateScrollbar(double mouseY) {
        float[] bar = scrollbar(); float travel = contentBottom() - contentTop() - bar[3];
        scroll = Math.round(Math.clamp(((float)mouseY - contentTop() - scrollGrab) / Math.max(1, travel), 0, 1) * Math.max(0, contentHeight - (contentBottom() - contentTop())));
        visibleScroll = scroll;
    }
    @Override
    public boolean mouseDragged(MouseButtonEvent raw, double dx, double dy) {
        if (windowMotion.closing() || pendingView != null) return true;
        var hud = registry.get(im.yaHustler.module.impl.render.Hud.class);
        if (hud != null && hud.drag(raw.x(), raw.y(), width, height)) return true;
        MouseButtonEvent event = localEvent(raw); dx /= transform().scale(); dy /= transform().scale();
        if(view==View.COSMETICS && cosmeticsPage.drag(pageFrame.localX(event.x()),pageFrame.localY(event.y()),dx,dy))return true;
        if (draggingScrollbar) { updateScrollbar(event.y()); return true; }
        if (draggingWindow) {
            x += (int) Math.round(dx); y += (int) Math.round(dy);
            windowDx = x - (width - panelWidth) / 2; windowDy = y - (height - panelHeight) / 2;
            search.setX(searchBoxX() + 16); search.setY(y + 6);
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
        var hud = registry.get(im.yaHustler.module.impl.render.Hud.class);
        if (hud != null && hud.endDrag(raw.x(), raw.y(), width, height)) return true;
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
    private record CategoryHit(Category category, int x, int y, int w, int h) {}
    private record SettingHit(Setting<?> setting, int x, int y, int w, int h, float trackX, float trackW) {}
    private record ModuleHit(Module module, int x, int y, int w, int h) {}
}
