package com.chocodanette.heartwood.species;

import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

import java.util.function.Supplier;

/**
 * Boat types added to {@link Boat.Type} through NeoForge enum extension
 * (see META-INF/enumextensions.json). Everything is resolved lazily, because
 * this class is loaded while the enum is being initialised.
 */
public final class HeartwoodBoats {
    public static final EnumProxy<Boat.Type> ASH = proxy("ash");
    public static final EnumProxy<Boat.Type> SCOTS_PINE = proxy("scots_pine");
    public static final EnumProxy<Boat.Type> CHESTNUT = proxy("chestnut");
    public static final EnumProxy<Boat.Type> MAPLE = proxy("maple");

    public static Boat.Type type(String name) {
        return switch (name) {
            case "ash" -> ASH.getValue();
            case "scots_pine" -> SCOTS_PINE.getValue();
            case "chestnut" -> CHESTNUT.getValue();
            case "maple" -> MAPLE.getValue();
            default -> throw new IllegalArgumentException("No boat type for " + name);
        };
    }

    private static EnumProxy<Boat.Type> proxy(String name) {
        Supplier<Block> planks = () -> Species.byName(name).planks().get();
        Supplier<Item> boat = () -> Species.byName(name).boat().get();
        Supplier<Item> chestBoat = () -> Species.byName(name).chestBoat().get();
        Supplier<Item> stick = () -> Items.STICK;
        return new EnumProxy<>(Boat.Type.class, planks, "heartwood:" + name, boat, chestBoat, stick, false);
    }

    private HeartwoodBoats() {}
}
