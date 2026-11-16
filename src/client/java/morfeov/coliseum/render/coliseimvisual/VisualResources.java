package morfeov.coliseum.render.coliseimvisual;

import net.minecraft.resources.Identifier;

/** Resource locations shared by the world visual effects. */
public final class VisualResources {
    private static final String NAMESPACE = "coliseum";


    private VisualResources() {}

    public static Identifier font(String name) {
        String path = name.startsWith("fonts/") ? name : "fonts/" + name;
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static Identifier image(String name) {
        String relative = name.startsWith("images/") ? name.substring("images/".length()) : name;
        String path = "images/coliseimvisual/" + relative;
        if (!path.endsWith(".png")) path += ".png";
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static Identifier shader(String name) {
        String relative = name.startsWith("core/") ? name.substring("core/".length()) : name;
        return Identifier.fromNamespaceAndPath(NAMESPACE, "core/coliseimvisual/" + relative);
    }
}
