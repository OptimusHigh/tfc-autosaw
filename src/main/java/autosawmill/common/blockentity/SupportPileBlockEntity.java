package autosawmill.common.blockentity;

import autosawmill.AutoSawmill;
import autosawmill.common.block.SupportPileBlock;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.InventoryBlockEntity;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity for the support pile.
 * Stores up to 16 supports of the same wood type in individual single-stack slots,
 * mirroring the canonical LogPileBlockEntity mechanics of TFC.
 */
public class SupportPileBlockEntity extends InventoryBlockEntity<ItemStackHandler> {
    public static final int SLOTS = 16;
    private static final int DOUBLE_CLICK_TICKS = 10;
    private static final long NO_CLICK = Long.MIN_VALUE / 2;

    private long lastClickTick;
    private boolean isLastClickPlacement;

    public SupportPileBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPPORT_PILE.get(), pos, state, defaultInventory(SLOTS), AutoSawmill.MOD_ID);
        this.lastClickTick = Calendars.get().getTicks();
        this.isLastClickPlacement = true;
    }

    @Override
    public void setAndUpdateSlots(int slot) {
        super.setAndUpdateSlots(slot);
        if (level != null && !level.isClientSide()) {
            suckSupportsFromAbove();
            if (isEmpty()) {
                level.setBlockAndUpdate(worldPosition, Blocks.AIR.defaultBlockState());
            } else {
                level.setBlockAndUpdate(worldPosition, getBlockState().setValue(SupportPileBlock.COUNT, supportCount()));
            }
        }
    }

    /**
     * @return true if every slot in this pile is empty
     */
    public boolean isEmpty() {
        for (ItemStack stack : Helpers.iterate(inventory)) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return total number of supports in this pile (0..16)
     */
    public int supportCount() {
        int count = 0;
        for (ItemStack stack : Helpers.iterate(inventory)) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the type of support currently stored in this pile, or null if empty.
     */
    @Nullable
    public Item getStoredItem() {
        for (ItemStack stack : Helpers.iterate(inventory)) {
            if (!stack.isEmpty()) {
                return stack.getItem();
            }
        }
        return null;
    }

    /**
     * Returns a copy of the first non-empty support item stack in the pile.
     */
    public ItemStack getStoredStack() {
        for (ItemStack stack : Helpers.iterate(inventory)) {
            if (!stack.isEmpty()) {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Sucks supports from an adjacent support pile above, if one exists and has supports.
     */
    private void suckSupportsFromAbove() {
        if (level != null && !level.isClientSide()) {
            if (level.getBlockEntity(worldPosition.above()) instanceof SupportPileBlockEntity pileAbove && !pileAbove.isEmpty()) {
                for (int i = 0; i < SLOTS; i++) {
                    ItemStack stack = pileAbove.inventory.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        for (int j = 0; j < SLOTS; j++) {
                            ItemStack target = inventory.getStackInSlot(j);
                            if (target.isEmpty()) {
                                inventory.setStackInSlot(j, stack.split(1));
                                break;
                            }
                        }
                    }
                }
                pileAbove.setAndUpdateSlots(-1);
            }
        }
    }

    public boolean checkDoubleClick(long gameTime) {
        final boolean doubleClick = gameTime - lastClickTick <= DOUBLE_CLICK_TICKS;
        if (doubleClick) {
            lastClickTick = NO_CLICK;
        }
        return doubleClick;
    }

    public void setLastClickTick(long tick) {
        this.lastClickTick = tick;
        setChanged();
    }

    public long getLastClickTick() {
        return this.lastClickTick;
    }

    public boolean isLastClickPlacement() {
        return isLastClickPlacement;
    }

    public void setLastClickPlacement(boolean lastInteractionPlacement) {
        this.isLastClickPlacement = lastInteractionPlacement;
        setChanged();
    }

    @Override
    public int getSlotStackLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!Helpers.isItem(stack.getItem(), TFCTags.Items.SUPPORT_BEAMS)) {
            return false;
        }
        final Item stored = getStoredItem();
        return stored == null || stored == stack.getItem();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("placement", isLastClickPlacement);
        tag.putLong("tick", lastClickTick);
        super.saveAdditional(tag, provider);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        isLastClickPlacement = tag.getBoolean("placement");
        lastClickTick = tag.getLong("tick");
        super.loadAdditional(tag, provider);
    }
}
