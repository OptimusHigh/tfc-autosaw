package autosawmill.common.util;

import autosawmill.common.block.ModBlocks;
import autosawmill.common.block.SupportPileBlock;
import autosawmill.common.blockentity.SupportPileBlockEntity;
import autosawmill.common.compat.CompatManager;
import autosawmill.common.compat.SoftItemResolver;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.LogPileBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.devices.LogPileBlock;
import net.dries007.tfc.common.blocks.wood.Wood;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Helpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves cutting targets, determines wood types, generates drops according to soft-compat,
 * and handles input consumption.
 */
public final class WoodHelper {

    public enum TargetType {
        LOG,
        STRIPPED_LOG,
        LOG_PILE,
        SUPPORT_PILE
    }

    public record WoodTarget(@Nullable Wood wood, String woodName, boolean hasBark, TargetType type) {}

    public static final TagKey<Block> TFC_LOGS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(CompatManager.MOD_TFC, "logs"));
    public static final TagKey<Block> TFC_STRIPPED_LOGS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(CompatManager.MOD_TFC, "stripped_logs"));
    public static final TagKey<Item> SAW_BLADES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "saw_blades"));

    public static boolean isSawBlade(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(autosawmill.common.item.ModItems.SAW_BLADE.get()) || stack.is(SAW_BLADES);
    }

    private WoodHelper() {}

    /**
     * Checks if an item stack is valid input for sawing.
     */
    public static boolean isValidInput(ItemStack stack) {
        return resolveItemTarget(stack) != null;
    }

    /**
     * Resolves a WoodTarget directly from an ItemStack held or inserted into the sawmill.
     */
    @Nullable
    public static WoodTarget resolveItemTarget(ItemStack stack) {
        if (stack.isEmpty()) return null;
        final Item item = stack.getItem();

        // 1. TFC Support Item
        final Wood supportWood = findWoodFromSupport(item);
        if (supportWood != null) {
            return new WoodTarget(supportWood, supportWood.name().toLowerCase(Locale.ROOT), false, TargetType.SUPPORT_PILE);
        }

        // 2. TFC Log / Stripped Log Item
        final Wood logWood = findWoodFromLogItem(item);
        if (logWood != null) {
            final boolean hasBark = isUnstrippedLogItem(item);
            return new WoodTarget(logWood, logWood.name().toLowerCase(Locale.ROOT), hasBark, hasBark ? TargetType.LOG : TargetType.STRIPPED_LOG);
        }

        // 3. Tag-based detection (#minecraft:logs)
        if (stack.is(net.minecraft.tags.ItemTags.LOGS)) {
            final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            final String woodName = parseWoodName(id);
            final boolean hasBark = isUnstrippedLogItem(item);
            Wood wood = null;
            try {
                wood = Wood.valueOf(woodName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
            return new WoodTarget(wood, woodName, hasBark, hasBark ? TargetType.LOG : TargetType.STRIPPED_LOG);
        }

        return null;
    }

    /**
     * Inspects the block at targetPos and determines if it is a valid cuttable wood target.
     */
    @Nullable
    public static WoodTarget resolveTarget(Level level, BlockPos pos, BlockState state) {
        final Block block = state.getBlock();

        // 1. Support Pile (autosawmill:support_pile)
        if (block instanceof SupportPileBlock) {
            if (level.getBlockEntity(pos) instanceof SupportPileBlockEntity pile && !pile.isEmpty()) {
                final Item item = pile.getStoredItem();
                if (item != null) {
                    final Wood wood = findWoodFromSupport(item);
                    final String woodName = wood != null ? wood.name().toLowerCase(Locale.ROOT) : parseWoodName(BuiltInRegistries.ITEM.getKey(item));
                    return new WoodTarget(wood, woodName, false, TargetType.SUPPORT_PILE);
                }
            }
            return null;
        }

        // 2. Log Pile (tfc:wood/log_pile)
        if (block instanceof LogPileBlock) {
            if (level.getBlockEntity(pos) instanceof LogPileBlockEntity pile && !pile.isEmpty()) {
                for (int i = 0; i < LogPileBlockEntity.SLOTS; i++) {
                    final ItemStack stack = pile.getInventory().getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        final Item item = stack.getItem();
                        final Wood wood = findWoodFromLogItem(item);
                        final boolean hasBark = isUnstrippedLogItem(item);
                        final String woodName = wood != null ? wood.name().toLowerCase(Locale.ROOT) : parseWoodName(BuiltInRegistries.ITEM.getKey(item));
                        return new WoodTarget(wood, woodName, hasBark, TargetType.LOG_PILE);
                    }
                }
            }
            return null;
        }

        // 3. Single Block (TFC Log or Stripped Log)
        for (Wood wood : Wood.VALUES) {
            if (TFCBlocks.WOODS.containsKey(wood)) {
                final var blockMap = TFCBlocks.WOODS.get(wood);
                final Block logBlock = blockMap.get(Wood.BlockType.LOG) != null ? blockMap.get(Wood.BlockType.LOG).get() : null;
                final Block woodBlock = blockMap.get(Wood.BlockType.WOOD) != null ? blockMap.get(Wood.BlockType.WOOD).get() : null;
                if (block == logBlock || block == woodBlock) {
                    return new WoodTarget(wood, wood.name().toLowerCase(Locale.ROOT), true, TargetType.LOG);
                }

                final Block strippedLog = blockMap.get(Wood.BlockType.STRIPPED_LOG) != null ? blockMap.get(Wood.BlockType.STRIPPED_LOG).get() : null;
                final Block strippedWood = blockMap.get(Wood.BlockType.STRIPPED_WOOD) != null ? blockMap.get(Wood.BlockType.STRIPPED_WOOD).get() : null;
                if (block == strippedLog || block == strippedWood) {
                    return new WoodTarget(wood, wood.name().toLowerCase(Locale.ROOT), false, TargetType.STRIPPED_LOG);
                }
            }
        }

        // 4. Fallback: Tag-based detection (#tfc:logs, #tfc:stripped_logs, #minecraft:logs)
        final boolean isTfcLog = state.is(TFC_LOGS);
        final boolean isTfcStripped = state.is(TFC_STRIPPED_LOGS);
        if (isTfcLog || isTfcStripped || state.is(BlockTags.LOGS)) {
            final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            final String woodName = parseWoodName(id);
            final boolean hasBark = isTfcLog || (!isTfcStripped && !id.getPath().contains("stripped"));
            Wood wood = null;
            try {
                wood = Wood.valueOf(woodName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}

            return new WoodTarget(wood, woodName, hasBark, hasBark ? TargetType.LOG : TargetType.STRIPPED_LOG);
        }

        return null;
    }

    /**
     * Generates drops for the cut cycle.
     */
    public static List<ItemStack> generateDrops(RandomSource random, WoodTarget target) {
        final List<ItemStack> drops = new ArrayList<>();

        // 1. Planks / Lumber
        final ItemStack lumberStack = getLumber(target.wood(), target.woodName());
        if (!lumberStack.isEmpty()) {
            final int lumberCount = (target.type() == TargetType.SUPPORT_PILE) ? 1 : (6 + random.nextInt(3)); // 6-8 for logs, 1 for support
            lumberStack.setCount(lumberCount);
            drops.add(lumberStack);
        }

        // 2. Bark (tfc_debark compat): 50% chance, only if log had bark
        if (target.hasBark() && CompatManager.isDebarkLoaded()) {
            if (random.nextFloat() < 0.50f) {
                final ItemStack bark = SoftItemResolver.getBark(target.woodName(), 1);
                if (!bark.isEmpty()) {
                    drops.add(bark);
                }
            }
        }

        // 3. Sawdust (tfc_lumberjack compat)
        if (CompatManager.isLumberjackLoaded()) {
            final int dustCount = (target.type() == TargetType.SUPPORT_PILE) ? 1 : (1 + random.nextInt(2));
            final ItemStack sawdust = SoftItemResolver.getSawdust(dustCount);
            if (!sawdust.isEmpty()) {
                drops.add(sawdust);
            }
        }

        return drops;
    }

    /**
     * Consumes one unit of input material (removes single log, or decrements pile).
     */
    public static void consumeInput(Level level, BlockPos pos, BlockState state, WoodTarget target) {
        switch (target.type()) {
            case LOG, STRIPPED_LOG -> level.removeBlock(pos, false);
            case LOG_PILE -> {
                if (level.getBlockEntity(pos) instanceof LogPileBlockEntity pile) {
                    for (int i = 0; i < LogPileBlockEntity.SLOTS; i++) {
                        final ItemStack stack = pile.getInventory().getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            stack.shrink(1);
                            pile.setAndUpdateSlots(-1);
                            break;
                        }
                    }
                }
            }
            case SUPPORT_PILE -> {
                if (level.getBlockEntity(pos) instanceof SupportPileBlockEntity pile) {
                    for (int i = 0; i < SupportPileBlockEntity.SLOTS; i++) {
                        final ItemStack stack = pile.getInventory().getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            stack.shrink(1);
                            pile.setAndUpdateSlots(-1);
                            break;
                        }
                    }
                }
            }
        }
    }

    private static ItemStack getLumber(@Nullable Wood wood, String woodName) {
        if (wood != null && TFCItems.LUMBER.containsKey(wood)) {
            final Item item = TFCItems.LUMBER.get(wood).get();
            if (item != null) {
                return new ItemStack(item);
            }
        }
        return SoftItemResolver.getItemStack(CompatManager.MOD_TFC, "wood/lumber/" + woodName, 1);
    }

    @Nullable
    private static Wood findWoodFromSupport(Item item) {
        for (Wood wood : Wood.VALUES) {
            if (TFCItems.SUPPORTS.containsKey(wood) && TFCItems.SUPPORTS.get(wood).get() == item) {
                return wood;
            }
        }
        return null;
    }

    @Nullable
    private static Wood findWoodFromLogItem(Item item) {
        for (Wood wood : Wood.VALUES) {
            if (TFCBlocks.WOODS.containsKey(wood)) {
                final var blockMap = TFCBlocks.WOODS.get(wood);
                for (Wood.BlockType type : new Wood.BlockType[] {Wood.BlockType.LOG, Wood.BlockType.WOOD, Wood.BlockType.STRIPPED_LOG, Wood.BlockType.STRIPPED_WOOD}) {
                    final var holder = blockMap.get(type);
                    if (holder != null && holder.get().asItem() == item) {
                        return wood;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isUnstrippedLogItem(Item item) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return !id.getPath().contains("stripped");
    }

    private static String parseWoodName(ResourceLocation id) {
        final String path = id.getPath();
        final int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) {
            return path.substring(lastSlash + 1);
        }
        final int lastUnderscore = path.lastIndexOf('_');
        if (lastUnderscore >= 0) {
            return path.substring(0, lastUnderscore);
        }
        return path;
    }
}
