package com.chocodanette.heartwood;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue DRYING_DAYS;
    private static final ModConfigSpec.DoubleValue GROWTH_DAYS;
    private static final ModConfigSpec.BooleanValue AXE_REQUIRED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        DRYING_DAYS = b.comment("Average number of in-game days a placed green log takes to dry when sheltered (no sky above it).")
                .defineInRange("dryingDays", 1.0, 0.05, 100.0);
        GROWTH_DAYS = b.comment("Average number of in-game days between two growth steps of a young tree (each step varies by half this, more or less).")
                .defineInRange("growthIntervalDays", 0.5, 0.01, 10.0);
        AXE_REQUIRED = b.comment("When false (the default), trees can be notched, felled, limbed and bucked with anything in hand,",
                        "or bare-handed, the same way as with an axe. When true, these cuts need an axe.")
                .define("axeRequired", false);
        SPEC = b.build();
    }

    public static double dryingDays() {
        return SPEC.isLoaded() ? DRYING_DAYS.get() : DRYING_DAYS.getDefault();
    }

    public static double growthIntervalDays() {
        return SPEC.isLoaded() ? GROWTH_DAYS.get() : GROWTH_DAYS.getDefault();
    }

    public static boolean axeRequired() {
        return SPEC.isLoaded() ? AXE_REQUIRED.get() : AXE_REQUIRED.getDefault();
    }

    /** True when this tool makes the cuts an axe makes: an axe always, anything else unless axeRequired is on. */
    public static boolean cuts(net.minecraft.world.item.ItemStack tool) {
        return tool.is(net.minecraft.tags.ItemTags.AXES) || !axeRequired();
    }

    private Config() {}
}
