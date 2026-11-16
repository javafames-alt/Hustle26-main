package im.yaHustler.module;

import im.yaHustler.font.Icons;

public enum Category {
    COMBAT("Combat", Icons.COMBAT, "ГЛАВНОЕ"),
    MOVEMENT("Movement", Icons.MOVEMENT, "ГЛАВНОЕ"),
    RENDER("Render", Icons.RENDER, "ОБЩЕЕ"),
    PLAYER("Player", Icons.USER, "ОБЩЕЕ"),
    MISC("Misc", Icons.MISC, "ОБЩЕЕ"),
    BOTS("Bots", Icons.USER, "КЛИЕНТ"),
    COSMETICS("Cosmetics", Icons.COSMETICS, "ПРОЧЕЕ"),
    THEMES("Themes", Icons.THEMES, "ПРОЧЕЕ");

    public final String label;
    public final String icon;
    public final String group;

    Category(String label, String icon, String group) {
        this.label = label;
        this.icon = icon;
        this.group = group;
    }
}
