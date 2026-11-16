package morfeov.coliseum.render.coliseimvisual;
import morfeov.coliseum.module.impl.themes.Theme;
import morfeov.coliseum.module.impl.themes.ThemeColor;
public final class VisualTheme {
    public static int themeColor(VisualThemeColor color) { return Theme.color(color == VisualThemeColor.MAIN_TWO ? ThemeColor.ACCENT_2 : ThemeColor.ACCENT); }
    public static int themeColorFor(VisualThemeColor color) { return themeColor(color); }
    public static boolean rainbowEnabled() { return false; }
}
