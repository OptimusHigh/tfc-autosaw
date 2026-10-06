package autosawmill.common.block;

import autosawmill.common.blockentity.ModBlockEntities;
import autosawmill.common.blockentity.SawmillBlockEntity;
import net.dries007.tfc.common.blocks.EntityBlockExtension;
import net.dries007.tfc.common.blocks.ExtendedBlock;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.IForgeBlockExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Sawmill device block. Sits above the wood material block.
 * Connects to the TFC kinetic network at the rear (facing.getOpposite()).
 * Implements full 4-block multiblock structure so all parts (bed, upper frame, gearbox)
 * are interactable and targetable anywhere on the machine.
 */
public class SawmillBlock extends ExtendedBlock implements IForgeBlockExtension, EntityBlockExtension {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<SawmillPart> PART = EnumProperty.create("part", SawmillPart.class);

    private static final VoxelShape[] BED_SHAPES = net.dries007.tfc.util.Helpers.computeHorizontalShapes(dir -> net.minecraft.world.phys.shapes.Shapes.or(
        net.dries007.tfc.util.Helpers.rotateShape(dir, 0, 0, 2, 16, 6, 14),
        net.dries007.tfc.util.Helpers.rotateShape(dir, 0, 6, 5, 16, 16, 11)
    ));

    private static final VoxelShape[] TOP_SHAPES = net.dries007.tfc.util.Helpers.computeHorizontalShapes(dir ->
        net.dries007.tfc.util.Helpers.rotateShape(dir, 0, 0, 5, 16, 14, 11)
    );

    private static final VoxelShape[] GEARBOX_SHAPES = net.dries007.tfc.util.Helpers.computeHorizontalShapes(dir ->
        net.dries007.tfc.util.Helpers.rotateShape(dir, 0, 0, 2, 16, 16, 14)
    );

    public SawmillBlock(ExtendedProperties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(PART, SawmillPart.BED));
    }

    public static BlockPos getBedPos(BlockPos pos, BlockState state) {
        if (!state.hasProperty(PART) || !state.hasProperty(FACING)) return pos;
        final Direction facing = state.getValue(FACING);
        final Direction side = facing.getCounterClockWise();
        return switch (state.getValue(PART)) {
            case BED -> pos;
            case FRAME_TOP -> pos.below();
            case GEARBOX_LOWER -> pos.relative(side.getOpposite(), 3);
            case GEARBOX_UPPER -> pos.relative(side.getOpposite(), 3).below();
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final Direction facing = context.getHorizontalDirection().getOpposite();
        final Direction side = facing.getCounterClockWise();

        final BlockPos topPos = pos.above();
        final BlockPos gbLowerPos = pos.relative(side, 3);
        final BlockPos gbUpperPos = gbLowerPos.above();

        if (!level.getBlockState(topPos).canBeReplaced(context) ||
            !level.getBlockState(gbLowerPos).canBeReplaced(context) ||
            !level.getBlockState(gbUpperPos).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, SawmillPart.BED);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack stack) {
        super.setPlacedBy(level, pos, state, entity, stack);
        if (!level.isClientSide() && state.getValue(PART) == SawmillPart.BED) {
            final Direction facing = state.getValue(FACING);
            final Direction side = facing.getCounterClockWise();
            final BlockPos topPos = pos.above();
            final BlockPos gbLowerPos = pos.relative(side, 3);
            final BlockPos gbUpperPos = gbLowerPos.above();

            level.setBlockAndUpdate(topPos, state.setValue(PART, SawmillPart.FRAME_TOP));
            level.setBlockAndUpdate(gbLowerPos, state.setValue(PART, SawmillPart.GEARBOX_LOWER));
            level.setBlockAndUpdate(gbUpperPos, state.setValue(PART, SawmillPart.GEARBOX_UPPER));
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            final BlockPos bedPos = getBedPos(pos, state);
            final BlockState bedState = level.getBlockState(bedPos);
            if (bedState.is(this) && bedState.getValue(PART) == SawmillPart.BED) {
                if (!player.isCreative()) {
                    dropResources(bedState, level, bedPos, level.getBlockEntity(bedPos), player, player.getMainHandItem());
                }
                final Direction facing = bedState.getValue(FACING);
                final Direction side = facing.getCounterClockWise();
                final BlockPos topPos = bedPos.above();
                final BlockPos gbLowerPos = bedPos.relative(side, 3);
                final BlockPos gbUpperPos = gbLowerPos.above();

                final BlockPos[] allParts = new BlockPos[] {bedPos, topPos, gbLowerPos, gbUpperPos};
                for (BlockPos p : allParts) {
                    if (!p.equals(pos) && level.getBlockState(p).is(this)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING, PART));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == SawmillPart.BED ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == SawmillPart.BED ? new SawmillBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != SawmillPart.BED || type != ModBlockEntities.SAWMILL.get()) {
            return null;
        }
        return level.isClientSide()
            ? (lvl, p, st, be) -> SawmillBlockEntity.clientTick(lvl, p, st, (SawmillBlockEntity) be)
            : (lvl, p, st, be) -> SawmillBlockEntity.serverTick(lvl, p, st, (SawmillBlockEntity) be);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        final BlockPos bedPos = getBedPos(pos, state);
        if (level.getBlockEntity(bedPos) instanceof SawmillBlockEntity sawmill) {
            // 1. Shift + Right Click with empty hand: extract item
            if (player.isShiftKeyDown() && stack.isEmpty()) {
                if (!level.isClientSide()) {
                    final ItemStack extracted = sawmill.extractItem();
                    if (!extracted.isEmpty()) {
                        if (!player.getInventory().add(extracted)) {
                            player.drop(extracted, false);
                        }
                        level.playSound(null, bedPos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
                    }
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }

            // 2. Right Click with valid wood item: insert into sawmill
            if (!stack.isEmpty() && autosawmill.common.util.WoodHelper.isValidInput(stack)) {
                if (!level.isClientSide()) {
                    final int countToInsert = player.isShiftKeyDown() ? stack.getCount() : 1;
                    final int inserted = sawmill.insertItem(stack, countToInsert);
                    if (inserted > 0) {
                        stack.shrink(inserted);
                        level.playSound(null, bedPos, net.minecraft.sounds.SoundEvents.WOOD_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f);
                    }
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }

            // 3. Right Click empty hand or other item: status info
            if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND) {
                if (sawmill.isJammed()) {
                    player.displayClientMessage(Component.translatable("autosawmill.status.jammed"), true);
                } else if (!sawmill.hasKineticPower()) {
                    player.displayClientMessage(Component.translatable("autosawmill.status.no_power"), true);
                } else if (sawmill.hasInputItem()) {
                    int percent = (int) (sawmill.getProgress() / sawmill.getMaxProgress() * 100f);
                    final ItemStack in = sawmill.getInputItem();
                    final String name = in.getCount() > 1 ? (in.getHoverName().getString() + " x" + in.getCount()) : in.getHoverName().getString();
                    player.displayClientMessage(Component.translatable("autosawmill.status.cutting_item", name, percent), true);
                } else if (sawmill.getProgress() > 0) {
                    int percent = (int) (sawmill.getProgress() / sawmill.getMaxProgress() * 100f);
                    player.displayClientMessage(Component.translatable("autosawmill.status.cutting", percent), true);
                } else {
                    player.displayClientMessage(Component.translatable("autosawmill.status.idle"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (state.getValue(PART) == SawmillPart.BED) {
                if (level.getBlockEntity(pos) instanceof SawmillBlockEntity sawmill) {
                    final ItemStack stack = sawmill.extractItem();
                    if (!stack.isEmpty()) {
                        net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                    }
                }
                final Direction facing = state.getValue(FACING);
                final Direction side = facing.getCounterClockWise();
                final BlockPos[] allParts = new BlockPos[] {
                    pos.above(),
                    pos.relative(side, 3),
                    pos.relative(side, 3).above()
                };
                for (BlockPos p : allParts) {
                    if (level.getBlockState(p).is(this)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int idx = state.getValue(FACING).get2DDataValue();
        return switch (state.getValue(PART)) {
            case BED -> BED_SHAPES[idx];
            case FRAME_TOP -> TOP_SHAPES[idx];
            case GEARBOX_LOWER, GEARBOX_UPPER -> GEARBOX_SHAPES[idx];
        };
    }
}
