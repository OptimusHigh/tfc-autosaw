package autosawmill.common.compat;

import autosawmill.common.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Provides safe lookup of items and item stacks from optional mods without hard class references.
 * Prevents NoClassDefFoundError when companion mods are not installed.
 */
public final class SoftItemResolver {

    private SoftItemResolver() {}

    /**
     * Resolves an Item by its ResourceLocation safely from BuiltInRegistries.ITEM.
     */
    public static Optional<Item> getItem(ResourceLocation location) {
        if (BuiltInRegistries.ITEM.containsKey(location)) {
            final Item item = BuiltInRegistries.ITEM.get(location);
            if (item != null && item != Items.AIR) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves an Item by namespace and path.
     */
    public static Optional<Item> getItem(String modId, String path) {
        return getItem(ResourceLocation.fromNamespaceAndPath(modId, path));
    }

    /**
     * Safely constructs an ItemStack if the item is present, or returns ItemStack.EMPTY.
     */
    public static ItemStack getItemStack(ResourceLocation location, int count) {
        return getItem(location)
                .map(item -> new ItemStack(item, count))
                .orElse(ItemStack.EMPTY);
    }

    /**
     * Safely constructs an ItemStack by modId and path.
     */
    public static ItemStack getItemStack(String modId, String path, int count) {
        return getItem(modId, path)
                .map(item -> new ItemStack(item, count))
                .orElse(ItemStack.EMPTY);
    }

    /**
     * Checks if an item exists in the registry and is not AIR.
     */
    public static boolean hasItem(ResourceLocation location) {
        return getItem(location).isPresent();
    }

    /**
     * Checks if an item exists by modId and path.
     */
    public static boolean hasItem(String modId, String path) {
        return getItem(modId, path).isPresent();
    }

    /**
     * Resolves wood bark from tfc_debark, e.g. tfc_debark:birch_bark.
     */
    public static ItemStack getBark(String woodName, int count) {
        if (!CompatManager.isDebarkLoaded()) {
            return ItemStack.EMPTY;
        }
        return getItemStack(CompatManager.MOD_TFC_DEBARK, woodName + "_bark", count);
    }

    /**
     * Resolves sawdust from tfc_lumberjack:sawdust.
     */
    public static ItemStack getSawdust(int count) {
        if (!CompatManager.isLumberjackLoaded()) {
            return ItemStack.EMPTY;
        }
        return getItemStack(CompatManager.MOD_TFC_LUMBERJACK, "sawdust", count);
    }

    /**
     * Resolves a brass gear: either from tfc_items:brass_gear or fallback autosawmill:brass_gear.
     */
    public static ItemStack getBrassGear(int count) {
        if (CompatManager.isTfcItemsLoaded()) {
            ItemStack stack = getItemStack(CompatManager.MOD_TFC_ITEMS, "brass_gear", count);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        if (ModItems.BRASS_GEAR != null) {
            return new ItemStack(ModItems.BRASS_GEAR.get(), count);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Resolves the Item representing brass gear (for recipe/ingredient comparisons).
     */
    public static Optional<Item> getBrassGearItem() {
        if (CompatManager.isTfcItemsLoaded()) {
            Optional<Item> item = getItem(CompatManager.MOD_TFC_ITEMS, "brass_gear");
            if (item.isPresent()) {
                return item;
            }
        }
        if (ModItems.BRASS_GEAR != null) {
            return Optional.of(ModItems.BRASS_GEAR.get());
        }
        return Optional.empty();
    }
}
