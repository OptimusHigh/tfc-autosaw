package autosawmill.common.event;

import autosawmill.AutoSawmill;
import autosawmill.common.block.ModBlocks;
import autosawmill.common.block.SupportPileBlock;
import autosawmill.common.blockentity.SupportPileBlockEntity;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.util.Helpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Handles world interaction events for support pile placement and extraction.
 */
@EventBusSubscriber(modid = AutoSawmill.MOD_ID)
public final class ModCommonEvents {

    @SubscribeEvent
    public static void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        final Level level = event.getLevel();
        final BlockPos pos = event.getPos();
        final BlockState state = level.getBlockState(pos);
        final Player player = event.getEntity();
        final ItemStack stack = event.getItemStack();
        final InteractionHand hand = event.getHand();

        if (hand != InteractionHand.MAIN_HAND) {
            return;
        }

        // 1. Shift + Right Click with EMPTY hand on existing SupportPileBlock -> extract all supports
        if (player.isShiftKeyDown() && stack.isEmpty() && state.getBlock() instanceof SupportPileBlock) {
            if (!level.isClientSide()) {
                SupportPileBlock.extractFromTop(level, pos, player, true);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
            return;
        }

        // 2. Holding a support beam item:
        if (Helpers.isItem(stack.getItem(), TFCTags.Items.SUPPORT_BEAMS)) {
            // 2a. Targeting an existing SupportPileBlock -> insert support(s)
            if (state.getBlock() instanceof SupportPileBlock && level.getBlockEntity(pos) instanceof SupportPileBlockEntity pile) {
                if (pile.isItemValid(0, stack)) {
                    if (!level.isClientSide()) {
                        final boolean all = player.isShiftKeyDown();
                        SupportPileBlock.insertAndPushUp(stack, state, level, pos, pile, all);
                    }
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
                    return;
                }
            }
            // 2b. Shift + Right Click with support on another surface -> place new SupportPileBlock
            else if (player.isShiftKeyDown() && player.mayBuild()) {
                final Direction face = event.getFace();
                if (face == null) {
                    return;
                }

                final BlockPlaceContext placeContext = new BlockPlaceContext(new UseOnContext(player, hand, event.getHitVec()));
                final BlockPos targetPos = placeContext.getClickedPos();
                final BlockPos belowPos = targetPos.below();
                final BlockState belowState = level.getBlockState(belowPos);

                final boolean canSurvive = Block.isFaceFull(belowState.getCollisionShape(level, belowPos), Direction.UP)
                        || belowState.getBlock() instanceof SupportPileBlock;

                if (canSurvive && placeContext.canPlace()) {
                    if (!level.isClientSide()) {
                        final BlockState placeState = ModBlocks.SUPPORT_PILE.get().getStateForPlacement(placeContext);
                        if (placeState != null && level.setBlock(targetPos, placeState, Block.UPDATE_ALL)) {
                            if (level.getBlockEntity(targetPos) instanceof SupportPileBlockEntity newPile) {
                                newPile.getInventory().setStackInSlot(0, stack.split(1));
                                newPile.setAndUpdateSlots(0);
                                Helpers.playPlaceSound(player, level, targetPos, placeState);
                            }
                        }
                    }
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
                }
            }
        }
    }

    private ModCommonEvents() {}
}
