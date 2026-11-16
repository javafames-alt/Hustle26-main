package im.yaHustler.cosmetics.hustle263;

import com.google.gson.JsonObject;
import java.util.Locale;

/** Chooses the same movement-dependent Bedrock tracks as the Hustle 26.3 client. */
public final class HustleAnimationSelector {
    private HustleAnimationSelector() {}

    public static String select(JsonObject animations, boolean gliding, boolean swimming, boolean sneaking, boolean moving) {
        if (animations == null || animations.isEmpty()) return null;
        String selected;
        if (gliding && (selected = findAny(animations, "elytra", "flying", "fly")) != null) return selected;
        if (swimming) {
            if (moving && (selected = findAll(animations, "moving", "water")) != null) return selected;
            if ((selected = findAny(animations, "swimming", "swim")) != null) return selected;
            if ((selected = findAll(animations, "idle", "water")) != null) return selected;
            if ((selected = findAll(animations, "main", "water")) != null) return selected;
        }
        if (sneaking) {
            if (moving && (selected = findAll(animations, "moving", "sneak")) != null) return selected;
            if ((selected = findAll(animations, "idle", "sneak")) != null) return selected;
            if ((selected = findAll(animations, "main", "sneak")) != null) return selected;
            if ((selected = findAny(animations, "sneak", "crouch")) != null) return selected;
        }
        if (moving) {
            selected = findAny(animations, "walking", "walk", "moving", "move", "run");
            if (isBaseTrack(selected)) return selected;
        }
        for (String idle : new String[]{"idle", "main"}) {
            for (String name : animations.keySet()) {
                if (name.equalsIgnoreCase(idle)) return name;
            }
            selected = findAny(animations, idle);
            if (isBaseTrack(selected)) return selected;
        }
        for (String name : animations.keySet()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.contains("gui") && !lower.contains("preview")) return name;
        }
        return animations.keySet().iterator().next();
    }

    private static boolean isBaseTrack(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return !lower.contains("water") && !lower.contains("sneak");
    }

    private static String findAny(JsonObject animations, String... words) {
        for (String name : animations.keySet()) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String word : words) if (lower.contains(word)) return name;
        }
        return null;
    }

    private static String findAll(JsonObject animations, String... words) {
        for (String name : animations.keySet()) {
            String lower = name.toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String word : words) matches &= lower.contains(word);
            if (matches) return name;
        }
        return null;
    }
}
