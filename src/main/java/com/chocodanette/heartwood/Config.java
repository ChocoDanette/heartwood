package com.chocodanette.heartwood;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue DRYING_DAYS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        DRYING_DAYS = b.comment("Average number of in-game days a placed green log takes to dry when sheltered (no sky above it).")
                .defineInRange("dryingDays", 2.0, 0.05, 100.0);
        SPEC = b.build();
    }

    public static double dryingDays() {
        return SPEC.isLoaded() ? DRYING_DAYS.get() : DRYING_DAYS.getDefault();
    }

    private Config() {}
}
