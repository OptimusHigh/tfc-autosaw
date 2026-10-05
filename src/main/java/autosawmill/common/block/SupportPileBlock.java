package autosawmill.common.block;

import autosawmill.common.blockentity.ModBlockEntities;
import autosawmill.common.blockentity.SupportPileBlockEntity;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.EntityBlockExtension;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.IForgeBlockExtension;
import net.dries007.tfc.common.blocks.TFCBlockStateProperties;
import net.dries007.tfc.common.blocks.devices.DeviceBlock;
import net.dries007.tfc.util.Helpers;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS;

/**
 * Block representing a stackable pile of support beams (up to 16).
 * Follows the canonical design of TFC's LogPileBlock.
 */
public class SupportPileBlock extends DeviceBlock implements IForgeBlockExtension, EntityBlockExtension {
    public static final EnumProperty<Direction.Axis> AXIS = HORIZONTAL_AXIS;
    public static final IntegerProperty COUNT = TFCBlockStateProperties.COUNT_1_16;

    private static final VoxelShape[][] SHAPES_BY_DIR_BY_COUNT = Util.make(new VoxelShape[2][16], shapes -> {
        double[][] box2ByCount = new double[16][6];
        double[][] box1ByCount = new double[16][6];
        int layer;
        int row;
        for (int i = 0; i < 16; i++) {
            layer = i / 4;
            row = i % 4 + 1;
            box2ByCount[i] = new double[] {0, 4 * layer, 0, 16, 4 * layer + 4, 4 * row};
            box1ByCount[i] = new double[] {0, 0, 0, 16, 4 * layer, 16};
        }

        for (int dir = 0; dir < 2; dir++) {
            Direction direction = (dir == 1) ? Direction.EAST : Direction.SOUTH;
            for (int count = 0; count < 16; count++) {
                VoxelShape box1 = Helpers.rotateShape(direction, box1ByCount[count][0], box1ByCount[count][1], box1ByCount[count][2], box1ByCount[count][3], box1ByCount[count][4], box1ByCount[count][5]);
                VoxelShape box2 = Helpers.rotateShape(direction, box2ByCount[count][0], box2ByCount[count][1], box2ByCount[count][2], box2ByCount[count][3], box2ByCount[count][4], box2ByCount[count][5]);
                shapes[dir][count] = Shapes.or(box1, box2);
            }
        }
    });

    private static final VoxelShape[][] BASED_SHAPES_BY_DIR_BY_COUNT = Util.make(new VoxelShape[2][3], shapes -> {
        final VoxelShape baseBox = Block.box(0.25, 0, 0.25, 15.75, 1, 15.75);
        for (int i = 0; i < 3; i++) {
            for (int dir = 0; dir < 2; dir++) {
                shapes[dir][i] = Shapes.or(baseBox, SHAPES_BY_DIR_BY_COUNT[dir][i]);
            }
        }
    });

    public SupportPileBlock(ExtendedProperties properties) {
        super(properties, InventoryRemoveBehavior.DROP);
        registerDefaultState(getStateDefinition().any().setValue(AXIS, Direction.Axis.X).setValue(COUNT, 1));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis()).setValue(COUNT, 1);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(AXIS).add(COUNT));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor levelAccess, BlockPos currentPos, BlockPos facingPos) {
        if (!levelAccess.isClientSide() && levelAccess instanceof Level) {
            if (facing == Direction.DOWN && !facingState.isFaceSturdy(levelAccess, facingPos, Direction.UP) && !(facingState.getBlock() instanceof SupportPileBlock)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, facing, facingState, levelAccess, currentPos, facingPos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof SupportPileBlockEntity pile) {
            // 1. Holding a valid support beam item: insert into pile
            if (!stack.isEmpty() && pile.isItemValid(0, stack)) {
                if (pile.supportCount() < SupportPileBlockEntity.SLOTS) {
                    if (!level.isClientSide()) {
                        insertAndPushUp(stack, state, level, pos, pile, player.isShiftKeyDown());
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
            }

            // 2. Empty hand: extract
            if (stack.isEmpty()) {
                if (!level.isClientSide()) {
                    extractFromTop(level, pos, player, player.isShiftKeyDown());
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Extracts supports from the top-most pile downwards.
     *
     * @param all If true, extracts all supports; if false, extracts exactly 1 support.
     */
    public static void extractFromTop(Level level, BlockPos pos, Player player, boolean all) {
        final BlockPos abovePos = pos.above();
        if (level.getBlockState(abovePos).getBlock() instanceof SupportPileBlock) {
            extractFromTop(level, abovePos, player, all);
        } else if (level.getBlockEntity(pos) instanceof SupportPileBlockEntity pile) {
            for (int i = 0; i < SupportPileBlockEntity.SLOTS; i++) {
                ItemStack slotStack = pile.getInventory().getStackInSlot(i);
                if (!slotStack.isEmpty()) {
                    ItemHandlerHelper.giveItemToPlayer(player, slotStack.split(1));
                    pile.setAndUpdateSlots(-1);
                    if (!all) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Inserts supports into the pile, pushing to a new pile above if full.
     */
    public static void insertAndPushUp(ItemStack stack, BlockState state, Level level, BlockPos pos, SupportPileBlockEntity pile, boolean all) {
        if (dumbInsert(stack, state, level, pos, pile, all) && !all) {
            return;
        }

        final BlockPos abovePos = pos.above();
        if (level.getBlockState(abovePos).isAir() && pile.supportCount() == SupportPileBlockEntity.SLOTS && !stack.isEmpty()) {
            level.setBlockAndUpdate(abovePos, ModBlocks.SUPPORT_PILE.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS)));
            if (level.getBlockEntity(abovePos) instanceof SupportPileBlockEntity pileAbove) {
                BlockState stateAbove = level.getBlockState(abovePos);
                if (dumbInsert(stack, stateAbove, level, abovePos, pileAbove, all)) {
                    return;
                } else {
                    level.removeBlock(abovePos, false);
                }
            }
        }

        if (level.getBlockState(abovePos).getBlock() instanceof SupportPileBlock && pile.supportCount() == SupportPileBlockEntity.SLOTS && level.getBlockEntity(abovePos) instanceof SupportPileBlockEntity pileAbove) {
            BlockState stateAbove = level.getBlockState(abovePos);
            SupportPileBlock.insertAndPushUp(stack, stateAbove, level, abovePos, pileAbove, all);
        }
    }

    private static boolean dumbInsert(ItemStack stack, BlockState state, Level level, BlockPos pos, SupportPileBlockEntity pile, boolean all) {
        if (!pile.isItemValid(0, stack)) {
            return false;
        }

        if (all) {
            ItemStack insertStack = stack.copy();
            insertStack = Helpers.insertAllSlots(pile.getInventory(), insertStack);
            if (insertStack.getCount() < stack.getCount()) {
                Helpers.playPlaceSound(null, level, pos, SoundType.WOOD);
                stack.setCount(insertStack.getCount());
                pile.setAndUpdateSlots(-1);
                return true;
            }
        } else if (Helpers.insertOne(pile, stack)) {
            Helpers.playPlaceSound(null, level, pos, state);
            stack.shrink(1);
            pile.setAndUpdateSlots(-1);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos, ModBlockEntities.SUPPORT_PILE.get())
                .map(SupportPileBlockEntity::getStoredStack)
                .orElse(ItemStack.EMPTY);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return Block.isFaceFull(below.getCollisionShape(level, pos.below()), Direction.UP) || below.getBlock() instanceof SupportPileBlock;
    }

    public static VoxelShape getShapeByDirByCount(Direction.Axis axis, int count, boolean based) {
        final int dir = (axis == Direction.Axis.X) ? 0 : 1;
        final int i = Math.max(0, Math.min(15, count - 1));
        if (based && count < 4) {
            return BASED_SHAPES_BY_DIR_BY_COUNT[dir][i];
        }
        return SHAPES_BY_DIR_BY_COUNT[dir][i];
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeByDirByCount(state.getValue(AXIS), state.getValue(COUNT), true);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeByDirByCount(state.getValue(AXIS), state.getValue(COUNT), false);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeByDirByCount(state.getValue(AXIS), state.getValue(COUNT), false);
    }
}
