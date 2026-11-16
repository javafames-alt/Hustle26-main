package im.yaHustler.module.impl.render;

import im.yaHustler.Hustle26;
import im.yaHustler.hud.HotKeys;
import im.yaHustler.hud.Cooldowns;
import im.yaHustler.hud.HudElement;
import im.yaHustler.hud.Potions;
import im.yaHustler.hud.TargetHud;
import im.yaHustler.hud.Watermark;
import im.yaHustler.hud.*;
import im.yaHustler.setting.ModeSetting;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.NumberSetting;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

@ModuleInfo(id = "hud", name = "Interface", category = Category.RENDER,
    description = "Элементы интерфейса поверх экрана")
public final class Hud extends Module {
    private static final String ELEMENTS = "Элементы";
    public final BooleanSetting watermark = add(new BooleanSetting("Watermark", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting potions = add(new BooleanSetting("Potions", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting hotkeys = add(new BooleanSetting("KeyBinds", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting targetHud = add(new BooleanSetting("TargetHUD", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting cooldowns = add(new BooleanSetting("Cooldowns", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting shadows = add(new BooleanSetting("HUD Shadow", false));
    public final NumberSetting shadowStrength = add(new NumberSetting("HUD Shadow Strength", 65, 0, 100, 1).visibleWhen(shadows::enabled));
    public final NumberSetting shadowSize = add(new NumberSetting("HUD Shadow Size", 6, 2, 20, .5).visibleWhen(shadows::enabled));
    public final BooleanSetting blur = add(new BooleanSetting("HUD Blur", true));
    public final BooleanSetting themeBlur = add(new BooleanSetting("Theme Blur", true).visibleWhen(blur::enabled));
    public final NumberSetting blurRadius = add(new NumberSetting("Blur Radius", 12, 1, 30, 1).visibleWhen(blur::enabled));
    public final NumberSetting opacity = add(new NumberSetting("Background Opacity", 85, 0, 100, 1));
    public final BooleanSetting watermarkLogo = add(new BooleanSetting("Watermark Logo", true).visibleWhen(watermark::enabled));
    public final BooleanSetting watermarkFps = add(new BooleanSetting("Watermark FPS", true).visibleWhen(watermark::enabled));
    public final BooleanSetting watermarkPing = add(new BooleanSetting("Watermark PING", true).visibleWhen(watermark::enabled));
    public final BooleanSetting listIcons = add(new BooleanSetting("HUD List Icons", true));
    public final BooleanSetting emptyLists = add(new BooleanSetting("Show Empty HUD Lists", false));
    public final BooleanSetting potionTime = add(new BooleanSetting("Potion Duration", true).visibleWhen(potions::enabled));
    public final BooleanSetting keybindKeys = add(new BooleanSetting("Keybind Keys", true).visibleWhen(hotkeys::enabled));
    public final BooleanSetting cooldownTime = add(new BooleanSetting("Cooldown Time", true).visibleWhen(cooldowns::enabled));
    public final BooleanSetting targetEquipment = add(new BooleanSetting("Target Equipment", true).visibleWhen(targetHud::enabled));
    public final BooleanSetting targetHealth = add(new BooleanSetting("Target Health", true).visibleWhen(targetHud::enabled));
    public final BooleanSetting dragTilt = add(new BooleanSetting("HUD Drag Tilt", true));
    public final BooleanSetting dragGuides = add(new BooleanSetting("HUD Drag Guides", true));
    public final BooleanSetting dragSnap = add(new BooleanSetting("HUD Snap", true));
    public final BooleanSetting armor = add(new BooleanSetting("Armor", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting staff = add(new BooleanSetting("Staff", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting notifications = add(new BooleanSetting("Notifications", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting totem = add(new BooleanSetting("Totem Indicator", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting coordinates = add(new BooleanSetting("Coordinates", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting customHotbar = add(new BooleanSetting("Custom Hotbar", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting music = add(new BooleanSetting("Music", true).inMenuGroup(ELEMENTS));
    public final BooleanSetting arrayList = add(new BooleanSetting("ArrayList", true).inMenuGroup(ELEMENTS));
    public final ModeSetting armorOrientation = add(new ModeSetting("Armor Orientation", "Horizontal", "Horizontal", "Vertical").visibleWhen(armor::enabled));
    public final BooleanSetting armorReverse = add(new BooleanSetting("Reverse Armor Order", false).visibleWhen(armor::enabled));
    public final BooleanSetting armorPercentage = add(new BooleanSetting("Armor Percentage", true).visibleWhen(armor::enabled));
    public final BooleanSetting potionWarnings = add(new BooleanSetting("Potion Ending Warnings", true).visibleWhen(notifications::enabled));
    public final BooleanSetting totemWarnings = add(new BooleanSetting("Totem Pop Notifications", true).visibleWhen(notifications::enabled));
    public final BooleanSetting musicLyrics = add(new BooleanSetting("Music Lyrics", true).visibleWhen(music::enabled));
    public final BooleanSetting musicCover = add(new BooleanSetting("Music Cover", true).visibleWhen(music::enabled));
    public final BooleanSetting musicSource = add(new BooleanSetting("Music Source", true).visibleWhen(music::enabled));
    public final ModeSetting arrayListSide = add(new ModeSetting("ArrayList Side", "Left", "Right", "Left").visibleWhen(arrayList::enabled));
    public final NumberSetting arrayListFont = add(new NumberSetting("ArrayList Font Size", 8, 7, 9, .1).visibleWhen(arrayList::enabled));
    public final NumberSetting arrayListSpacing = add(new NumberSetting("ArrayList Row Height", 10, 10, 13, .5).visibleWhen(arrayList::enabled));
    public final NumberSetting arrayListGradient = add(new NumberSetting("ArrayList Gradient Offset", 10, 5, 50, 1).visibleWhen(arrayList::enabled));
    public final NumberSetting arrayListSpeed = add(new NumberSetting("ArrayList Speed", 60, 5, 100, 1).visibleWhen(arrayList::enabled));
    public final BooleanSetting hideRenderList = add(new BooleanSetting("ArrayList Hide Render", true).visibleWhen(arrayList::enabled));
    public final BooleanSetting arrayListCombat = add(new BooleanSetting("ArrayList Combat", true).visibleWhen(arrayList::enabled));
    public final BooleanSetting arrayListMovement = add(new BooleanSetting("ArrayList Movement", true).visibleWhen(arrayList::enabled));
    public final BooleanSetting arrayListPlayer = add(new BooleanSetting("ArrayList Player", true).visibleWhen(arrayList::enabled));
    public final BooleanSetting arrayListMisc = add(new BooleanSetting("ArrayList Misc", false).visibleWhen(arrayList::enabled));
    private final Watermark watermarkElement = element(new Watermark(position("watermark.x", 6), position("watermark.y", 6)), "watermarkElement");
    private final Potions potionsElement = element(new Potions(position("potions.x", 6), position("potions.y", 27)), "potionsElement");
    private final TargetHud targetElement = element(new TargetHud(position("target.x", 200), position("target.y", 150)), "targetElement");
    private final HotKeys hotkeysElement = element(new HotKeys(Hustle26.MODULES, position("hotkeys.x", 6), position("hotkeys.y", 100)), "hotkeysElement");
    private final Cooldowns cooldownsElement = element(new Cooldowns(position("cooldowns.x", 6), position("cooldowns.y", 160)), "cooldownsElement");
    private final Armor armorElement = element(new Armor(position("armor.x", 150), position("armor.y", 210)), "Armor");
    private final Staff staffElement = element(new Staff(position("staff.x", 6), position("staff.y", 205)), "Staff");
    private final Notifications notificationsElement = element(new Notifications(position("notifications.x", 250), position("notifications.y", 60)), "Notifications");
    private final TotemIndicator totemElement = element(new TotemIndicator(position("totem.x", 200), position("totem.y", 180)), "Totem Indicator");
    private final Coordinates coordinatesElement = element(new Coordinates(position("coordinates.x", 6), position("coordinates.y", 240)), "Coordinates");
    private final Music musicElement = element(new Music(position("music.x", 235), position("music.y", 6)), "Music");
    private final ArrayListHud arrayListElement = element(new ArrayListHud(Hustle26.MODULES, position("arraylist.x", 6), position("arraylist.y", 39)), "ArrayList");
    private final List<HudElement> active = new java.util.ArrayList<>(12);
    private HudElement dragged;
    private double dragOffsetX, dragOffsetY;

    public Hud() { setEnabled(true); }

    private NumberSetting position(String name, double value) {
        return add(new NumberSetting(name, value, 0, 8192, 1).visibleWhen(() -> false));
    }
    private <T extends HudElement> T element(T element, String name) {
        element.scaleSetting(add(new NumberSetting(name + ".scale", 1, .4, 2.5, .05).visibleWhen(() -> false)));
        return element;
    }
    @Override public void tick(Minecraft client) { notificationsElement.tick(client); }
    public boolean replacesVanillaHotbar() { return enabled() && customHotbar.enabled(); }
    public void extractHotbar(GuiGraphicsExtractor g, DeltaTracker delta) { if(!NoRender.noHotbar() && !im.yaHustler.ui.GuiVisibility.hidesHud()) CustomHotbar.draw(g, Minecraft.getInstance()); }
    public boolean replacesVanillaEffects() { return enabled() && potions.enabled() && !WYVERN_UI; }
    /** The ported wyvern HUD renders Watermark/ArrayList/TargetHUD/KeyBinds/Cooldowns/Staff/Potions/Notifications itself;
     *  the old elements below stay available for Armor, Totem, Coordinates, Music and the custom hotbar. */
    private static final boolean WYVERN_UI = true;
    private List<HudElement> active() {
        active.clear();
        if (!enabled()) return active;
        if (armor.enabled()) active.add(armorElement);
        if (!WYVERN_UI && staff.enabled()) active.add(staffElement);
        if (!WYVERN_UI && notifications.enabled()) active.add(notificationsElement);
        if (totem.enabled()) active.add(totemElement);
        if (coordinates.enabled()) active.add(coordinatesElement);
        if (music.enabled()) active.add(musicElement);
        if (!WYVERN_UI && targetHud.enabled()) active.add(targetElement);
        if (!WYVERN_UI && hotkeys.enabled()) active.add(hotkeysElement);
        if (!WYVERN_UI && cooldowns.enabled()) active.add(cooldownsElement);
        if (!WYVERN_UI && potions.enabled() && !NoRender.noEffectIcons()) active.add(potionsElement);
        if (!WYVERN_UI && watermark.enabled()) active.add(watermarkElement);
        if (!WYVERN_UI && arrayList.enabled()) active.add(arrayListElement);
        return active;
    }
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if(im.yaHustler.ui.GuiVisibility.hidesHud()) {
            if(dragged!=null) { dragged.savePosition(); dragged=null; }
            return;
        }
        if (client.player == null) return;
        boolean editing = client.gui.screen() instanceof ChatScreen || client.gui.screen() instanceof im.yaHustler.ui.ClickGuiScreen || client.gui.screen() instanceof im.yaHustler.ui.HudEditorScreen;
        if (!editing && dragged != null) { dragged.savePosition(); dragged = null; }
        List<HudElement> elements = active();
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement element = elements.get(i);
            if (element != dragged) element.extractRenderState(g, client, editing, false, shadows.enabled());
        }
        if (dragged != null && elements.contains(dragged)) dragged.extractRenderState(g, client, editing, true, shadows.enabled());
    }
    public boolean openElementSettings(double mx, double my) {
        if(im.yaHustler.ui.GuiVisibility.hidesHud()) return false;
        for (HudElement element : active()) {
            if (!element.contains(mx, my)) continue;
            List<im.yaHustler.setting.Setting<?>> settings = new java.util.ArrayList<>(); settings.add(element.scaleSetting());
            settings.addAll(List.of(opacity, shadows, shadowStrength, shadowSize));
            if (element == armorElement) settings.addAll(List.of(armorOrientation, armorReverse, armorPercentage));
            if (element == notificationsElement) settings.addAll(List.of(potionWarnings, totemWarnings));
            if (element == musicElement) settings.addAll(List.of(musicLyrics, musicCover, musicSource));
            if (element == watermarkElement) settings.addAll(List.of(watermarkLogo, watermarkFps, watermarkPing));
            if (element == potionsElement) settings.addAll(List.of(listIcons, emptyLists, potionTime));
            if (element == hotkeysElement) settings.addAll(List.of(listIcons, emptyLists, keybindKeys));
            if (element == cooldownsElement) settings.addAll(List.of(listIcons, emptyLists, cooldownTime));
            if (element == targetElement) settings.addAll(List.of(targetEquipment, targetHealth));
            if (element == arrayListElement) settings.addAll(List.of(arrayListSide, arrayListFont, arrayListSpacing,
                arrayListGradient, arrayListSpeed, hideRenderList, arrayListCombat, arrayListMovement, arrayListPlayer, arrayListMisc));
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(new im.yaHustler.ui.HudEditorScreen(mc.gui.screen(), element.getClass().getSimpleName(), settings, (int)mx, (int)my));
            return true;
        }
        return false;
    }
    public boolean beginDrag(double mx, double my) {
        if(im.yaHustler.ui.GuiVisibility.hidesHud()) return false;
        for (HudElement element : active()) {
            if (!element.contains(mx, my)) continue;
            dragged = element;
            dragged.beginDrag();
            dragOffsetX = mx - element.x();
            dragOffsetY = my - element.y();
            return true;
        }
        return false;
    }
    public boolean drag(double mx, double my, int screenWidth, int screenHeight) {
        if (dragged == null) return false;
        int nx = (int)Math.round(mx - dragOffsetX), ny = (int)Math.round(my - dragOffsetY);
        if (dragSnap.enabled()) {
            nx = snap(nx, dragged.width(), screenWidth);
            ny = snap(ny, dragged.height(), screenHeight);
        }
        dragged.dragTo(nx, ny, screenWidth, screenHeight, false);
        return true;
    }
    public boolean endDrag(double mx, double my, int screenWidth, int screenHeight) {
        if (dragged == null) return false;
        drag(mx, my, screenWidth, screenHeight);
        dragged.savePosition();
        dragged = null;
        return true;
    }
    private static int snap(int position, int size, int screen) {
        if (Math.abs(position + size / 2f - screen / 2f) < 4) return Math.round((screen - size) / 2f);
        if (Math.abs(position - 6) < 4) return 6;
        if (Math.abs(position + size - screen + 6) < 4) return screen - size - 6;
        return position;
    }
}

