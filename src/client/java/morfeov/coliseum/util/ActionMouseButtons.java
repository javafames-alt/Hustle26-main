package morfeov.coliseum.util;

/** Mouse actions share Minecraft's SDL input events, including the two side buttons. */
public final class ActionMouseButtons {
    private static final boolean[] HELD=new boolean[9];
    private ActionMouseButtons(){}
    public static void update(int button,boolean down){if(button>=0&&button<HELD.length)HELD[button]=down;}
    public static boolean down(int button){return button>=0&&button<HELD.length&&HELD[button];}
    public static int number(int button) {
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return 1;
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) return 2;
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE) return 3;
        return button;
    }
}
