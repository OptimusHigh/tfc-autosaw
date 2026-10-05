package autosawmill.common.item;

import autosawmill.AutoSawmill;
import autosawmill.common.compat.CompatManager;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Handles registration of mod items and conditional fallbacks.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AutoSawmill.MOD_ID);

    // Saw blade item for crafting the sawmill and its cutting assembly
    public static final DeferredHolder<Item, Item> SAW_BLADE = ITEMS.register("saw_blade",
            () -> new Item(new Item.Properties()));

    // Sawmill BlockItem
    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> SAWMILL = ITEMS.register("sawmill",
            () -> new net.minecraft.world.item.BlockItem(autosawmill.common.block.ModBlocks.SAWMILL.get(), new Item.Properties()));

    // Fallback brass gear registered only if tfc_items is NOT loaded
    public static final @Nullable DeferredHolder<Item, Item> BRASS_GEAR;

    static {
        if (!CompatManager.isTfcItemsLoaded()) {
            BRASS_GEAR = ITEMS.register("brass_gear", () -> new Item(new Item.Properties()));
        } else {
            BRASS_GEAR = null;
        }
    }

    private ModItems() {}
}
