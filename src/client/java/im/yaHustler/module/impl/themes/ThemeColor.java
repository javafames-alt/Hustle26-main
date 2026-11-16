package im.yaHustler.module.impl.themes;

public enum ThemeColor {
    ACCENT("1 Цвет темы", 0xffe8b1ce),
    ACCENT_2("2 Цвет темы", 0xfff0d0e2),
    OUTLINE("Цвет обводки", 0x50465570),
    BACKGROUND("Цвет заднего фона", 0xeb172034),
    GLOW("Цвет свечения", 0x14a0b4ff),
    DIVIDER("Цвет разделителей", 0x14ffffff),
    SCROLL("Цвет линии скролла", 0x40ffffff),
    TOGGLE_ON("Активный переключатель", 0xff3d6cf2),
    TOGGLE_OFF("Не активный переключатель", 0xff121826),
    SLIDER("Цвет слайдерного круга", 0xff3d6cf2),
    SELECTED("Цвет выбранного элемента", 0xb00f1628),
    TEXT("Активный текст", 0xffe4e8f0),
    TEXT_OFF("Не активный текст", 0xff7d8698),
    GUI_ACCENT("Акцент", 0xffe8b1ce),
    GUI_PANEL("Панели", 0xcf0a0c12),
    GUI_HEADER("Боковая панель", 0xcf0a0c12),
    GUI_FIELD("Поля и кнопки", 0xeb12131a),
    GUI_TEXT("Текст", 0xfff7f7f7),
    GUI_MUTED("Вторичный текст", 0xff919a94);

    public final String label;
    public final int defaultValue;

    ThemeColor(String label, int defaultValue) {
        this.label = label;
        this.defaultValue = defaultValue;
    }
}
