package im.yaHustler.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Original donor textures. Dimensions are kept to sample the complete image. */
public final class InterfaceAssets {
    private InterfaceAssets() {}
    private record Region(int x, int y, int width, int height) {}
    private static final java.util.Map<String, Region> ICON_REGIONS = new java.util.HashMap<>();
    private record Texture(String path, int width, int height, Identifier id) {
        Texture(String path, int width, int height) {
            this(path, width, height, Identifier.fromNamespaceAndPath("hustle263", path));
        }
    }
    private static final java.util.Map<String, Texture> TEXTURES = new java.util.HashMap<>();
    private static Texture texture(String name) { return TEXTURES.computeIfAbsent(name, InterfaceAssets::createTexture); }
    private static Texture createTexture(String name) { return switch (name) {
        case "activity" -> new Texture("textures/gui/icons/activity.png", 64, 64);
        case "arrow" -> new Texture("textures/gui/icons/arrow.png", 16, 16);
        case "arrow_up" -> new Texture("textures/gui/icons/arrow_up.png", 16, 16);
        case "bps" -> new Texture("textures/gui/icons/bps.png", 96, 96);
        case "checkmark" -> new Texture("textures/gui/icons/checkmark.png", 16, 16);
        case "clock" -> new Texture("textures/gui/icons/clock.png", 64, 64);
        case "combat" -> new Texture("textures/gui/icons/combat.png", 32, 32);
        case "config" -> new Texture("textures/gui/icons/config.png", 128, 128);
        case "coords" -> new Texture("textures/gui/icons/coords.png", 64, 64);
        case "copy" -> new Texture("textures/gui/icons/copy.png", 16, 16);
        case "create" -> new Texture("textures/gui/icons/create.png", 16, 16);
        case "delete" -> new Texture("textures/gui/icons/delete.png", 16, 16);
        case "effects" -> new Texture("textures/gui/icons/effects.png", 64, 64);
        case "fps" -> new Texture("textures/gui/icons/fps.png", 96, 96);
        case "frame" -> new Texture("textures/gui/icons/frame.png", 16, 16);
        case "gps_arrow" -> new Texture("textures/gui/icons/gps_arrow.png", 256, 256);
        case "gps_pin" -> new Texture("textures/gui/icons/gps_pin.png", 256, 256);
        case "info" -> new Texture("textures/gui/icons/info.png", 16, 16);
        case "keybinds" -> new Texture("textures/gui/icons/keybinds.png", 64, 64);
        case "keybind_icon" -> new Texture("textures/gui/icons/keybind_icon.png", 19, 19);
        case "keyboard" -> new Texture("textures/gui/icons/keyboard.png", 64, 64);
        case "like" -> new Texture("textures/gui/icons/like.png", 16, 16);
        case "load" -> new Texture("textures/gui/icons/load.png", 16, 16);
        case "logo" -> new Texture("textures/gui/icons/logo.png", 1024, 1024);
        case "mc" -> new Texture("textures/gui/icons/mc.png", 64, 64);
        case "misc" -> new Texture("textures/gui/icons/misc.png", 32, 32);
        case "monitor" -> new Texture("textures/gui/icons/monitor.png", 64, 64);
        case "movement" -> new Texture("textures/gui/icons/movement.png", 32, 32);
        case "nurik_logo" -> new Texture("textures/gui/icons/nurik_logo.png", 861, 663);
        case "nurik_logo_clean" -> new Texture("textures/gui/icons/nurik_logo_clean.png", 861, 663);
        case "player" -> new Texture("textures/gui/icons/player.png", 32, 32);
        case "potion" -> new Texture("textures/gui/icons/potion.png", 64, 64);
        case "property" -> new Texture("textures/gui/icons/property.png", 16, 16);
        case "reload" -> new Texture("textures/gui/icons/reload.png", 16, 16);
        case "rocket" -> new Texture("textures/gui/icons/rocket.png", 64, 64);
        case "save" -> new Texture("textures/gui/icons/save.png", 16, 16);
        case "search" -> new Texture("textures/gui/icons/search.png", 24, 24);
        case "settings" -> new Texture("textures/gui/icons/settings.png", 24, 24);
        case "staff" -> new Texture("textures/gui/icons/staff.png", 64, 64);
        case "theme" -> new Texture("textures/gui/icons/theme.png", 128, 128);
        case "tps" -> new Texture("textures/gui/icons/tps.png", 64, 64);
        case "user" -> new Texture("textures/gui/icons/user.png", 64, 64);
        case "visuals" -> new Texture("textures/gui/icons/visuals.png", 32, 32);
        case "wifi" -> new Texture("textures/gui/icons/wifi.png", 16, 16);
        case "x" -> new Texture("textures/gui/icons/x.png", 10, 10);
        case "hud/activity" -> new Texture("textures/figma/icons/activity.png", 64, 64);
        case "hud/bps" -> new Texture("textures/figma/icons/bps.png", 96, 96);
        case "hud/clock" -> new Texture("textures/figma/icons/clock.png", 64, 64);
        case "hud/hustle263_logo" -> new Texture("textures/figma/icons/hustle263_logo.png", 64, 64);
        case "hud/config" -> new Texture("textures/figma/icons/config.png", 128, 128);
        case "hud/coords" -> new Texture("textures/figma/icons/coords.png", 64, 64);
        case "hud/effects" -> new Texture("textures/figma/icons/effects.png", 64, 64);
        case "hud/fps" -> new Texture("textures/figma/icons/fps.png", 96, 96);
        case "hud/watermark_fps" -> new Texture("textures/figma/icons/watermark_fps.png", 96, 96);
        case "hud/watermark_ping" -> new Texture("textures/figma/icons/watermark_ping.png", 64, 64);
        case "hud/home" -> new Texture("textures/figma/icons/home.png", 64, 64);
        case "hud/info" -> new Texture("textures/figma/icons/info.png", 64, 64);
        case "hud/keybinds" -> new Texture("textures/figma/icons/keybinds.png", 64, 64);
        case "hud/keybind_icon" -> new Texture("textures/figma/icons/keybind_icon.png", 19, 19);
        case "hud/keyboard" -> new Texture("textures/figma/icons/keyboard.png", 64, 64);
        case "hud/logo" -> new Texture("textures/figma/icons/logo.png", 1024, 1024);
        case "hud/mc" -> new Texture("textures/figma/icons/mc.png", 64, 64);
        case "hud/monitor" -> new Texture("textures/figma/icons/monitor.png", 64, 64);
        case "hud/nurik_logo" -> new Texture("textures/figma/icons/nurik_logo.png", 861, 663);
        case "hud/plus" -> new Texture("textures/figma/icons/plus.png", 64, 64);
        case "hud/potion" -> new Texture("textures/figma/icons/potion.png", 64, 64);
        case "hud/rocket" -> new Texture("textures/figma/icons/rocket.png", 64, 64);
        case "hud/settings" -> new Texture("textures/figma/icons/settings.png", 64, 64);
        case "hud/staff" -> new Texture("textures/figma/icons/staff.png", 64, 64);
        case "hud/target_x" -> new Texture("textures/figma/icons/target_x.png", 64, 64);
        case "hud/theme" -> new Texture("textures/figma/icons/theme.png", 128, 128);
        case "hud/tps" -> new Texture("textures/figma/icons/tps.png", 64, 64);
        case "hud/user" -> new Texture("textures/figma/icons/user.png", 64, 64);
        case "hud/wifi" -> new Texture("textures/figma/icons/wifi.png", 64, 64);
        case "hud/x" -> new Texture("textures/figma/icons/x.png", 64, 64);
        case "brand" -> new Texture("textures/hustle263/hustle263_c_sdf.png", 256, 256);
        default -> throw new IllegalArgumentException("Unknown interface texture: " + name);
    }; }
    public static void icon(GuiGraphicsExtractor g, String name, float x, float y, float size, int color) {
        color = GuiOpacity.color(color);
        Texture t = texture(name);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 32, size / 32);
        // The C uses a distance field so its contour stays sharp at HUD and GUI scales.
        im.yaHustler.ui.GuiTextures.blit(g, name.equals("brand") ? im.yaHustler.font.MsdfPipeline.crisp() : RenderPipelines.GUI_TEXTURED,
            t.id,
            0, 0, 0, 0, 32, 32, t.width, t.height, t.width, t.height, color);
        g.pose().popMatrix();
    }

    /** Fit the visible shape, preserving aspect ratio, into a shared centered icon slot. */
    public static void centeredIcon(GuiGraphicsExtractor g, String name, float cx, float cy, float size, int color) {
        Texture t = texture(name);
        Region ink = ICON_REGIONS.computeIfAbsent(t.path, path -> {
            try (var stream = InterfaceAssets.class.getResourceAsStream("/assets/hustle263/" + path)) {
                if (stream == null) return new Region(0, 0, t.width, t.height);
                var image = javax.imageio.ImageIO.read(stream);
                int left=image.getWidth(), top=image.getHeight(), right=-1, bottom=-1;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++)
                    if((image.getRGB(x,y)>>>24)>=16) {
                        left=Math.min(left,x); top=Math.min(top,y); right=Math.max(right,x); bottom=Math.max(bottom,y);
                    }
                return right<left?new Region(0,0,t.width,t.height):new Region(left,top,right-left+1,bottom-top+1);
            } catch (java.io.IOException e) { return new Region(0,0,t.width,t.height); }
        });
        float scale=size/Math.max(ink.width,ink.height), w=ink.width*scale, h=ink.height*scale;
        g.pose().pushMatrix();
        g.pose().translate(cx-w/2,cy-h/2);
        g.pose().scale(w/32,h/32);
        im.yaHustler.ui.GuiTextures.blit(g, RenderPipelines.GUI_TEXTURED,t.id,
                0,0,ink.x,ink.y,32,32,ink.width,ink.height,t.width,t.height,GuiOpacity.color(color));
        g.pose().popMatrix();
    }
}
