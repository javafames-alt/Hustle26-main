package im.yaHustler.ui;

import java.util.List;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.setting.*;

/** Shared measured geometry for card height, rendering and pointer hit areas. */
public record SettingRowLayout(List<String> labels, List<String> help, List<String> values,
        float labelY, float labelCenter, float controlY, float controlHeight,
        float helpY, float sliderY, int height) {
    public static final float LABEL_STEP = 9, HELP_STEP = 8.5f, VALUE_STEP = 8.5f;

    public static SettingRowLayout measure(Setting<?> setting, float width) {
        var info = SettingPresentation.of(setting);
        String value = switch (setting) {
            case ModeSetting m -> m.get();
            case KeySetting k -> k.label();
            case ItemSetting item -> item.label();
            case TextSetting text -> text.get();
            case NumberSetting n -> n.display() + info.unit();
            default -> "";
        };
        boolean field = setting instanceof ModeSetting || setting instanceof KeySetting || setting instanceof ItemSetting || setting instanceof TextSetting;
        float reserved = field ? 74 : setting instanceof NumberSetting ? Math.max(36, FigmaFont.width(value, 6.6f / 11) + 6) : 25;
        var labels = TextWrap.lines(info.label(), Math.max(12, width - 11 - reserved), s -> FigmaFont.width(s, 7.2f / 11));
        var help = TextWrap.lines(info.help(), Math.max(12, width), s -> FigmaFont.width(s, 6.6f / 11));
        var values = field ? TextWrap.lines(value, 53, s -> FigmaFont.width(s, 6.5f / 11)) : List.<String>of();
        float labelHeight = FigmaFont.mediumHeight(7.2f / 11) + Math.max(0, labels.size() - 1) * LABEL_STEP;
        float controlHeight = field ? Math.max(13, 8 + FigmaFont.mediumHeight(6.5f / 11) + Math.max(0, values.size() - 1) * VALUE_STEP)
            : setting instanceof BooleanSetting ? 8 : setting instanceof ColorSetting ? 11 : FigmaFont.mediumHeight(6.6f / 11);
        float header = Math.max(labelHeight, controlHeight);
        float labelY = 2 + (header - labelHeight) / 2;
        float helpY = 2 + header + 4;
        float bodyBottom = help.isEmpty() ? 2 + header : helpY + FigmaFont.mediumHeight(6.6f / 11) + (help.size() - 1) * HELP_STEP;
        float sliderY = Math.max(23, bodyBottom + 7);
        int minimum = setting instanceof NumberSetting ? 32 : setting instanceof ModeSetting ? 27 : 26;
        int height = (int)Math.ceil(Math.max(minimum, setting instanceof NumberSetting ? sliderY + 9 : bodyBottom + 7));
        return new SettingRowLayout(labels, help, values, labelY, 2 + header / 2, 2 + (header - controlHeight) / 2,
            controlHeight, helpY, sliderY, height);
    }
}
