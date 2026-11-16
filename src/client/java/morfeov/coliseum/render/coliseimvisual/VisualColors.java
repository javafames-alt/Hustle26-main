package morfeov.coliseum.render.coliseimvisual;

import java.awt.Color;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.joml.Vector4f;

public final class VisualColors {
    private static final String THIS_IS_A_UTILITY = "This is a utility class and cannot be instantiated";


    public static int alpha(int color) {
        return color >> 24 & 0xFF;
    }

    public static Vector4f toRgbaVector(int color) {
        float alpha = (color >> 24 & 0xFF) / 255.0F;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        return new Vector4f(red, green, blue, alpha);
    }

    public static int green(int color) {
        return color >> 8 & 0xFF;
    }

    public static int animatedSaturatedGradient(int firstColor, int secondColor, int phase, int speed) {
        int cycleAngle = (int)((System.currentTimeMillis() / speed + phase) % 360L);
        cycleAngle = (cycleAngle > 180 ? 360 - cycleAngle : cycleAngle) + 180;
        int color = lerpColor(firstColor, secondColor, Mth.clamp(cycleAngle / 180.0F - 1.0F, 0.0F, 1.0F));
        float[] rgba = toRgbaArray(color);
        float[] hsb = Color.RGBtoHSB((int)(rgba[0] * 255.0F), (int)(rgba[1] * 255.0F), (int)(rgba[2] * 255.0F), null);
        hsb[1] *= 1.5F;
        hsb[1] = Math.min(hsb[1], 1.0F);
        return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
    }

    public static float[] toRgbaArray(int color) {
        return new float[]{(color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, (color >> 24 & 0xFF) / 255.0F};
    }

    public static int lerpVectorColor(int firstColor, int secondColor, float amount) {
        Vector4f first = toRgbaVector(firstColor).mul(255.0F);
        Vector4f second = toRgbaVector(secondColor).mul(255.0F);
        return argb(
            (int)VisualMath.lerp(first.x, second.x, amount),
            (int)VisualMath.lerp(first.y, second.y, amount),
            (int)VisualMath.lerp(first.z, second.z, amount),
            VisualMath.lerp(first.w, second.w, amount)
        );
    }

    private VisualColors() {
        throw new UnsupportedOperationException(THIS_IS_A_UTILITY);
    }

    public static int argb(int red, int green, int blue, double alpha) {
        return (int)alpha << 24 | red << 16 | green << 8 | blue;
    }

    public static int animatedThemeColor(int index) {
        if (VisualTheme.rainbowEnabled()) {
            float hue = ((float)System.currentTimeMillis() / 8.0F + index * 16) % 360.0F / 360.0F;
            if (hue < 0.0F) {
                hue++;
            }

            return Color.HSBtoRGB(hue, 0.85F, 1.0F) | -16777216;
        } else {
            return animatedGradient(
                VisualTheme.themeColorFor(VisualThemeColor.MAIN),
                VisualTheme.themeColorFor(VisualThemeColor.MAIN_TWO),
                index * 16.0F / 360.0F,
                255
            );
        }
    }

    public static int parseHex(String hex) {
        String digits = hex.trim();
        if (digits.startsWith("#")) {
            digits = digits.substring(1);
        }

        if (digits.startsWith("0x") || digits.startsWith("0X")) {
            digits = digits.substring(2);
        }

        if (digits.length() == 3 || digits.length() == 4) {
            char redDigit = digits.charAt(0);
            char greenDigit = digits.charAt(1);
            char blueDigit = digits.charAt(2);
            char alphaDigit = digits.length() == 4 ? digits.charAt(3) : 'F';
            digits = "" + redDigit + redDigit + greenDigit + greenDigit + blueDigit + blueDigit + alphaDigit + alphaDigit;
        }

        if (digits.length() == 6) {
            int red = Integer.parseInt(digits.substring(0, 2), 16);
            int green = Integer.parseInt(digits.substring(2, 4), 16);
            int blue = Integer.parseInt(digits.substring(4, 6), 16);
            return argb(red, green, blue, 255.0);
        } else if (digits.length() == 8) {
            int red = Integer.parseInt(digits.substring(0, 2), 16);
            int green = Integer.parseInt(digits.substring(2, 4), 16);
            int blue = Integer.parseInt(digits.substring(4, 6), 16);
            int alpha = Integer.parseInt(digits.substring(6, 8), 16);
            return argb(red, green, blue, alpha);
        } else {
            throw new IllegalArgumentException(hex + "");
        }
    }

    public static int withAlpha(int color, float opacity) {
        int alpha = Math.min(255, Math.max(0, (int)(opacity * 255.0F)));
        return argb(red(color), green(color), blue(color), alpha);
    }

    public static int animatedGradient(int firstColor, int secondColor, float phase, int alpha) {
        if ((firstColor & 16777215) == (secondColor & 16777215)) {
            return argb(red(firstColor), green(firstColor), blue(firstColor), alpha);
        }

        float cycle = ((float)(System.currentTimeMillis() % 10000L) / 10000.0F + phase) % 1.0F;
        if (cycle < 0.0F) {
            cycle++;
        }

        float blend = 0.5F - 0.5F * (float)Math.cos(cycle * Math.PI * 2.0);
        int color = lerpColor(firstColor, secondColor, blend);
        return argb(red(color), green(color), blue(color), alpha);
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    public static int red(int color) {
        return color >> 16 & 0xFF;
    }

    public static int rgb(int red, int green, int blue) {
        return -16777216 | red << 16 | green << 8 | blue;
    }

    public static Component gradientText(String text) {
        MutableComponent result = Component.empty();

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            MutableComponent letter = Component.literal(String.valueOf(character)).setStyle(Style.EMPTY.withColor(new Color(animatedThemeColor(index)).getRGB()));
            result.append(letter);
        }

        return result;
    }

    public static int lerpColor(int firstColor, int secondColor, float amount) {
        float[] first = toRgbaArray(firstColor);
        float[] second = toRgbaArray(secondColor);
        return argb(
            (int)VisualMath.lerp(first[0] * 255.0F, second[0] * 255.0F, amount),
            (int)VisualMath.lerp(first[1] * 255.0F, second[1] * 255.0F, amount),
            (int)VisualMath.lerp(first[2] * 255.0F, second[2] * 255.0F, amount),
            (int)VisualMath.lerp(first[3] * 255.0F, second[3] * 255.0F, amount)
        );
    }

    public static int multiplyChannels(int color, float factor) {
        int red = (int)(red(color) * factor);
        int green = (int)(green(color) * factor);
        int blue = (int)(blue(color) * factor);
        int alpha = (int)(alpha(color) * factor);
        return argb(red, green, blue, alpha);
    }
}
