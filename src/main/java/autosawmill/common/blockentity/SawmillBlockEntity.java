package autosawmill.common.blockentity;

import autosawmill.common.block.SawmillBlock;
import autosawmill.common.util.OutputRouter;
import autosawmill.common.util.WoodHelper;
import net.dries007.tfc.common.blockentities.TFCBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.CrankshaftBlockEntity;
import net.dries007.tfc.common.blocks.rotation.CrankshaftBlock;
import net.dries007.tfc.util.rotation.Rotation;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * BlockEntity for the Sawmill.
 * Powered by a native TFC crankshaft connected to either the left or right side.
 */
public class SawmillBlockEntity extends TFCBlockEntity {
    public static final float PROGRESS_MODIFIER = 10.0f;
    public static final float DEFAULT_MAX_PROGRESS = 8.0f * (float) Math.PI * PROGRESS_MODIFIER;

    private ItemStack inputStack = ItemStack.EMPTY;
    private ItemStack bladeStack = ItemStack.EMPTY;
    private float progress = 0.0f;
    private float maxProgress = DEFAULT_MAX_PROGRESS;
    private boolean isJammed = false;
    private boolean alternation = false;

    private float prevContinuousAngle = 0.0f;
    private float continuousAngle = 0.0f;
    private float prevCutProgress = 0.0f;
    private float cutProgress = 0.0f;

    public SawmillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAWMILL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SawmillBlockEntity sawmill) {
        sawmill.tick(level, pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SawmillBlockEntity sawmill) {
        sawmill.prevContinuousAngle = sawmill.continuousAngle;
        final Rotation rot = sawmill.getActiveRotation();
        final boolean isRotating = rot != null && Math.abs(rot.speed()) > 0.001f;
        if (isRotating) {
            sawmill.continuousAngle += Math.abs(rot.speed());
            final float twoPi4 = 8.0f * (float) Math.PI;
            if (sawmill.continuousAngle >= twoPi4) {
                sawmill.continuousAngle -= twoPi4;
                sawmill.prevContinuousAngle -= twoPi4;
            }
        }

        sawmill.prevCutProgress = sawmill.cutProgress;
        if (sawmill.hasInputItem() && sawmill.hasBlade() && isRotating && !sawmill.isJammed) {
            final float speed = Math.abs(rot.speed());
            sawmill.cutProgress += speed * PROGRESS_MODIFIER;
            if (sawmill.cutProgress >= sawmill.maxProgress) {
                sawmill.cutProgress = 0.0f;
                sawmill.prevCutProgress = 0.0f;
            }
        } else if (!sawmill.hasInputItem() || !sawmill.hasBlade()) {
            sawmill.cutProgress = Math.max(0.0f, sawmill.cutProgress - 5.0f);
        }
    }

    public float getInterpolatedCutProgress(float partialTick) {
        if (!hasInputItem() || !hasBlade()) {
            return 0.0f;
        }
        return net.minecraft.util.Mth.lerp(partialTick, prevCutProgress, cutProgress);
    }

    public float getContinuousAngle(float partialTick) {
        final Rotation rot = getActiveRotation();
        if (rot != null && Math.abs(rot.speed()) > 0.001f) {
            return net.minecraft.util.Mth.lerp(partialTick, prevContinuousAngle, continuousAngle);
        }
        return continuousAngle;
    }

    /**
     * Finds connected native TFC crankshaft on either side (left/CCW or right/CW),
     * checking both outer frame post distance and direct distance, at BED and FRAME_TOP levels.
     * Prioritizes actively rotating crankshafts if multiple are connected.
     */
    @Nullable
    public CrankshaftBlockEntity getCrankBlockEntity() {
        if (level == null) return null;
        final BlockState state = getBlockState();
        if (!state.hasProperty(SawmillBlock.FACING)) return null;

        final Direction facing = state.getValue(SawmillBlock.FACING);
        final Direction ccw = facing.getCounterClockWise();
        final Direction cw = facing.getClockWise();
        final BlockPos bedPos = worldPosition;
        final BlockPos topPos = worldPosition.above();

        final Direction[] sides = new Direction[] {cw, ccw};
        CrankshaftBlockEntity firstConnected = null;

        for (Direction side : sides) {
            // Check frame post connection (distance 2/3) first, then direct connection (distance 1/2)
            final BlockPos[] queryPositions = new BlockPos[] {
                topPos.relative(side),
                bedPos.relative(side),
                topPos,
                bedPos
            };

            for (BlockPos qPos : queryPositions) {
                final CrankshaftBlockEntity crank = CrankshaftBlockEntity.getCrankShaftAt(level, qPos, side);
                if (crank != null) {
                    final Rotation rot = crank.getRotationNode() != null ? crank.getRotationNode().rotation() : null;
                    if (rot != null && Math.abs(rot.speed()) > 0.001f) {
                        return crank; // Prioritize active running crankshaft
                    }
                    if (firstConnected == null) {
                        firstConnected = crank;
                    }
                }
            }
        }

        return firstConnected;
    }

    public enum CrankSide {
        LEFT, RIGHT, NONE
    }

    public CrankSide getCrankConnectionSide() {
        final CrankshaftBlockEntity crank = getCrankBlockEntity();
        if (crank == null || level == null) return CrankSide.NONE;

        final BlockState state = getBlockState();
        if (!state.hasProperty(SawmillBlock.FACING)) return CrankSide.NONE;

        final Direction facing = state.getValue(SawmillBlock.FACING);
        final Direction ccw = facing.getCounterClockWise();
        final Direction cw = facing.getClockWise();
        final BlockPos crankPos = crank.getBlockPos();

        final int dx = crankPos.getX() - worldPosition.getX();
        final int dz = crankPos.getZ() - worldPosition.getZ();

        if (dx * cw.getStepX() + dz * cw.getStepZ() > 0) {
            return CrankSide.RIGHT;
        }
        if (dx * ccw.getStepX() + dz * ccw.getStepZ() > 0) {
            return CrankSide.LEFT;
        }

        return CrankSide.NONE;
    }

    @Nullable
    public Rotation getCrankRotation() {
        final CrankshaftBlockEntity crank = getCrankBlockEntity();
        return crank != null && crank.getRotationNode() != null ? crank.getRotationNode().rotation() : null;
    }

    @Nullable
    public Rotation getActiveRotation() {
        return getCrankRotation();
    }

    public boolean hasKineticPower() {
        final Rotation rot = getCrankRotation();
        return rot != null && Math.abs(rot.speed()) > 0.001f;
    }

    public float getRotationAngle(float partialTick) {
        final CrankshaftBlockEntity crank = getCrankBlockEntity();
        if (crank != null) {
            final Direction face = crank.getBlockState().getValue(CrankshaftBlock.FACING);
            return CrankshaftBlockEntity.calculateRealRotationAngle(crank, face, partialTick);
        }
        return 0.0f;
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

    public boolean hasBlade() {
        return !bladeStack.isEmpty();
    }

    public ItemStack getBlade() {
        return bladeStack;
    }

    public boolean insertBlade(ItemStack stack) {
        if (hasBlade() || stack.isEmpty() || !WoodHelper.isSawBlade(stack)) {
            return false;
        }
        bladeStack = stack.copyWithCount(1);
        markForSync();
        return true;
    }

    public ItemStack extractBlade() {
        if (!hasBlade()) {
            return ItemStack.EMPTY;
        }
        final ItemStack extracted = bladeStack.copy();
        bladeStack = ItemStack.EMPTY;
        progress = 0.0f;
        markForSync();
        return extracted;
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        if (!hasBlade()) {
            if (progress > 0) {
                progress = 0;
                markForSync();
            }
            return;
        }

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
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putFloat("progress", progress);
        tag.putBoolean("jammed", isJammed);
        if (!inputStack.isEmpty()) {
            tag.put("inputItem", inputStack.save(provider));
        }
        if (!bladeStack.isEmpty()) {
            tag.put("blade", bladeStack.save(provider));
        }
        super.saveAdditional(tag, provider);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        this.progress = tag.getFloat("progress");
        this.cutProgress = this.progress;
        this.prevCutProgress = this.progress;
        this.isJammed = tag.getBoolean("jammed");
        if (tag.contains("inputItem")) {
            this.inputStack = ItemStack.parseOptional(provider, tag.getCompound("inputItem"));
        } else {
            this.inputStack = ItemStack.EMPTY;
        }
        if (tag.contains("blade")) {
            this.bladeStack = ItemStack.parseOptional(provider, tag.getCompound("blade"));
        } else {
            this.bladeStack = ItemStack.EMPTY;
        }
        super.loadAdditional(tag, provider);
    }
}
