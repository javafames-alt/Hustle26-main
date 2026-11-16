package im.yaHustler.setting;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/** Action binding. Mouse buttons use -100-button; -1 means unbound. */
public class KeySetting extends Setting<Integer> {
    private boolean held;
    public KeySetting(String name) { super(name, -1); }
    public boolean pressed(Minecraft client) {
        boolean down = client.player != null && down();
        boolean pressed = down && !held && client.gui.screen() == null;
        held = down;
        return pressed;
    }
    public boolean down() {
        if (value <= -100) return im.yaHustler.util.ActionMouseButtons.down(-100-value);
        return value >= 0 && InputConstants.isKeyDown(value);
    }
    public String label() {
        if (value == -1) return "None";
        if (value <= -100) return "Mouse " + (-99-value);
        return InputConstants.Type.KEYBOARD.getOrCreate(value).getDisplayName().getString();
    }
    @Override public void setSilently(Integer next) { value = (next >= 0 && next <= 348 || next >= -107 && next <= -100) ? next : -1; held = false; }
}
