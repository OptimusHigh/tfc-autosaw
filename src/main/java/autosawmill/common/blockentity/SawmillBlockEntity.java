package autosawmill.common.blockentity;

import autosawmill.common.block.SawmillBlock;
import autosawmill.common.util.OutputRouter;
import autosawmill.common.util.WoodHelper;
import net.dries007.tfc.common.blockentities.TFCBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.RotationSinkBlockEntity;
import net.dries007.tfc.util.rotation.NetworkAction;
import net.dries007.tfc.util.rotation.Node;
import net.dries007.tfc.util.rotation.Rotation;
import net.dries007.tfc.util.rotation.SinkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * BlockEntity for the Sawmill.
 * Integrates into TFC's mechanical rotation network as a RotationSinkBlockEntity.
 */
public class SawmillBlockEntity extends TFCBlockEntity implements RotationSinkBlockEntity {
    public static final float PROGRESS_MODIFIER = 10.0f;
    public static final float DEFAULT_MAX_PROGRESS = 8.0f * (float) Math.PI * PROGRESS_MODIFIER;

    private final Node node;
    private ItemStack inputStack = ItemStack.EMPTY;
    private float progress = 0.0f;
    private float maxProgress = DEFAULT_MAX_PROGRESS;
    private boolean isJammed = false;
    private boolean alternation = false;

    public SawmillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAWMILL.get(), pos, state);

        final Direction facing = state.getValue(SawmillBlock.FACING);
        final Direction connection = facing.getOpposite();

        this.node = new SinkNode(pos, connection) {
            @Override
            public String toString() {
                return "Sawmill[pos=%s]".formatted(pos());
            }
        };
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SawmillBlockEntity sawmill) {
        sawmill.tick(level, pos, state);
    }

    public boolean hasInputItem() {
        return !inputStack.isEmpty();
    }

    public ItemStack getInputItem() {
        return inputStack;
    }

    public int insertItem(ItemStack stack, int maxCount) {
        if (stack.isEmpty() || !WoodHelper.isValidInput(stack)) {
            return 0;
        }
        if (inputStack.isEmpty()) {
            int toInsert = Math.min(maxCount, stack.getCount());
            inputStack = stack.copyWithCount(toInsert);
            progress = 0.0f;
            markForSync();
            return toInsert;
        } else if (ItemStack.isSameItemSameComponents(inputStack, stack)) {
            int space = inputStack.getMaxStackSize() - inputStack.getCount();
            int toInsert = Math.min(Math.min(maxCount, stack.getCount()), space);
            if (toInsert > 0) {
                inputStack.grow(toInsert);
                markForSync();
                return toInsert;
            }
        }
        return 0;
    }

    public ItemStack extractItem() {
        if (inputStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = inputStack.copy();
        inputStack = ItemStack.EMPTY;
        progress = 0.0f;
        markForSync();
        return extracted;
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        final Rotation rotation = getActiveRotation();
        if (rotation == null) {
            if (progress > 0) {
                progress = Math.max(0, progress - 0.5f);
                markForSync();
            }
            return;
        }

        final float speed = Math.abs(rotation.speed());
        if (speed <= 0.001f) {
            return;
        }

        final Direction facing = state.getValue(SawmillBlock.FACING);
        WoodHelper.WoodTarget target = null;
        BlockPos originPos = pos;
        boolean isItemSawing = false;

        // 1. Direct Item Input has priority
        if (!inputStack.isEmpty()) {
            target = WoodHelper.resolveItemTarget(inputStack);
            originPos = pos;
            isItemSawing = true;
        }

        // 2. In-world target below the sawmill bed as fallback
        if (target == null) {
            final BlockPos targetPos = pos.below();
            final BlockState targetState = level.getBlockState(targetPos);
            target = WoodHelper.resolveTarget(level, targetPos, targetState);
            originPos = targetPos;
            isItemSawing = false;
        }

        if (target == null) {
            if (progress > 0) {
                progress = 0;
                markForSync();
            }
            isJammed = false;
            return;
        }

        if (!OutputRouter.canEject(level, originPos, facing)) {
            if (!isJammed) {
                isJammed = true;
                markForSync();
            }
            return;
        }

        if (isJammed) {
            isJammed = false;
            markForSync();
        }

        // Progress scaling: speed in radians/tick. At 0.1 rad/tick (~1 rad/sec), speed * PROGRESS_MODIFIER = 1 per tick.
        progress += speed * PROGRESS_MODIFIER;

        // Periodic saw cutting effects
        if (level.getGameTime() % 12 == 0) {
            level.playSound(null, originPos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 0.7f, 1.2f);
            if (level instanceof ServerLevel serverLevel) {
                double px = originPos.getX() + 0.5;
                double py = isItemSawing ? (originPos.getY() + 0.4) : (originPos.getY() + 0.8);
                double pz = originPos.getZ() + 0.5;
                serverLevel.sendParticles(ParticleTypes.CRIT, px, py, pz, 4, 0.2, 0.1, 0.2, 0.05);
            }
        }

        // Cycle completed: produce output
        if (progress >= maxProgress) {
            progress = 0.0f;

            final List<ItemStack> drops = WoodHelper.generateDrops(level.random, target);
            if (isItemSawing) {
                inputStack.shrink(1);
                if (inputStack.isEmpty()) {
                    inputStack = ItemStack.EMPTY;
                }
            } else {
                final BlockState targetState = level.getBlockState(originPos);
                WoodHelper.consumeInput(level, originPos, targetState, target);
            }

            OutputRouter.ejectDrops(level, originPos, facing, drops, this);
            level.playSound(null, originPos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);
            markForSync();
        }
    }

    public boolean toggleAlternation() {
        alternation = !alternation;
        return alternation;
    }

    public boolean hasKineticPower() {
        return getActiveRotation() != null;
    }

    public @org.jetbrains.annotations.Nullable Rotation getActiveRotation() {
        if (node.rotation() != null && Math.abs(node.rotation().speed()) > 0.001f) {
            return node.rotation();
        }
        if (level == null) return null;
        final Direction facing = getBlockState().getValue(SawmillBlock.FACING);
        final BlockPos ccwPos = worldPosition.relative(facing.getCounterClockWise(), 3);
        final BlockPos cwPos = worldPosition.relative(facing.getClockWise(), 3);
        final BlockPos rearPos = worldPosition.relative(facing.getOpposite());

        final BlockPos[] checkPositions = new BlockPos[] {
            rearPos,                                       // Directly behind machine bed
            rearPos.above(),                               // Directly behind upper frame
            ccwPos.above().relative(facing.getOpposite()), // Behind upper gearbox
            ccwPos.relative(facing.getOpposite()),         // Behind lower gearbox
            ccwPos.above(),                                // At upper gearbox
            ccwPos,                                        // At lower gearbox
            cwPos.above().relative(facing.getOpposite()),  // Opposite side upper
            cwPos.relative(facing.getOpposite()),          // Opposite side lower
            worldPosition.above()                          // Directly above frame
        };
        for (BlockPos p : checkPositions) {
            if (level.getBlockEntity(p) instanceof net.dries007.tfc.common.blockentities.rotation.RotatingBlockEntity rotating) {
                final Node rNode = rotating.getRotationNode();
                if (rNode != null && rNode.rotation() != null && Math.abs(rNode.rotation().speed()) > 0.001f) {
                    return rNode.rotation();
                }
            }
        }
        return null;
    }

    @Override
    public float getRotationAngle(float partialTick) {
        final Rotation rot = getActiveRotation();
        return rot != null ? Rotation.angle(rot, partialTick) : 0.0f;
    }

    public float getProgress() {
        return progress;
    }

    public float getMaxProgress() {
        return maxProgress;
    }

    public boolean isJammed() {
        return isJammed;
    }

    @Override
    public Node getRotationNode() {
        return node;
    }

    @Override
    protected void onLoadAdditional() {
        performNetworkAction(NetworkAction.ADD);
    }

    @Override
    protected void onUnloadAdditional() {
        performNetworkAction(NetworkAction.REMOVE);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putFloat("progress", progress);
        tag.putBoolean("jammed", isJammed);
        if (!inputStack.isEmpty()) {
            tag.put("inputItem", inputStack.save(provider));
        }
        super.saveAdditional(tag, provider);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        this.progress = tag.getFloat("progress");
        this.isJammed = tag.getBoolean("jammed");
        if (tag.contains("inputItem")) {
            this.inputStack = ItemStack.parseOptional(provider, tag.getCompound("inputItem"));
        } else {
            this.inputStack = ItemStack.EMPTY;
        }
        super.loadAdditional(tag, provider);
    }
}
