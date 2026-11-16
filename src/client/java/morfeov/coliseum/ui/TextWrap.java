package morfeov.coliseum.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Word wrapping without dropping text or splitting a Unicode code point. */
public final class TextWrap {
    private TextWrap() {}
    public static List<String> lines(String text, float width, ToDoubleFunction<String> measure) {
        List<String> result = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            String remaining = paragraph.strip().replaceAll("\\s+", " ");
            if (remaining.isEmpty()) { if (!text.isEmpty()) result.add(""); continue; }
            while (!remaining.isEmpty()) {
                int end = 0, space = -1;
                while (end < remaining.length()) {
                    int next = end + Character.charCount(remaining.codePointAt(end));
                    if (end > 0 && measure.applyAsDouble(remaining.substring(0, next)) > width) break;
                    if (remaining.charAt(end) == ' ') space = end;
                    end = next;
                }
                if (end < remaining.length() && space > 0) end = space;
                result.add(remaining.substring(0, end).stripTrailing());
                remaining = remaining.substring(end).stripLeading();
            }
        }
        return List.copyOf(result);
    }
}
