package morfeov.coliseum.module.impl.combat.aura;

import java.util.List;

public enum Rotation {
    ReallyWorld("ReallyWorld"),
    SpookyTime("SpookyTime"),
    DataSet("DataSet"),
    HvH("HvH");

    private final String name;

    Rotation(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    public static Rotation fromString(String mode) {
        if (mode == null) return ReallyWorld;
        for (Rotation m : values()) {
            if (m.name.equalsIgnoreCase(mode) || m.name().equalsIgnoreCase(mode)) {
                return m;
            }
        }
        if (mode.equalsIgnoreCase("ReallyWorld") || mode.equalsIgnoreCase("RW")) return ReallyWorld;
        if (mode.equalsIgnoreCase("SpookyTime") || mode.equalsIgnoreCase("Spooky_Time") || mode.equalsIgnoreCase("Spooky")) return SpookyTime;
        // Read profiles saved before the mode was renamed.
        if (mode.equalsIgnoreCase("Neuro") || mode.equalsIgnoreCase("NeuroTest")) return DataSet;
        return ReallyWorld;
    }

    public static List<String> getModeNames() {
        return List.of(
                "ReallyWorld",
                "SpookyTime",
                "DataSet",
                "HvH"
        );
    }
}
