package autosawmill.common.util;

import autosawmill.common.blockentity.SawmillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages destination routing and physical ejection of sawmill products.
 * Prioritizes containers directly beneath the work area (e.g. hoppers), then falls back
 * to side ejection (left/right) with collision checks.
 */
public final class OutputRouter {

    private OutputRouter() {}

    /**
     * Checks if the sawmill has at least one valid exit route (container below, left side, or right side).
     */
    public static boolean canEject(Level level, BlockPos targetPos, Direction facing) {
        // 1. Container below or behind
        final BlockPos containerPos = targetPos.below();
        final IItemHandler handlerBelow = level.getCapability(Capabilities.ItemHandler.BLOCK, containerPos, Direction.UP);
        if (handlerBelow != null && hasOpenSlot(handlerBelow)) {
            return true;
        }

        final Direction rearDir = facing.getOpposite();
        final BlockPos rearPos = targetPos.relative(rearDir);
        final IItemHandler handlerRear = level.getCapability(Capabilities.ItemHandler.BLOCK, rearPos, facing);
        if (handlerRear != null && hasOpenSlot(handlerRear)) {
            return true;
        }

        // 2. Open air ejection checks (rear, left, right)
        final Direction leftDir = facing.getCounterClockWise();
        final Direction rightDir = facing.getClockWise();
        final BlockPos leftPos = targetPos.relative(leftDir);
        final BlockPos rightPos = targetPos.relative(rightDir);

        final boolean rearFree = level.getBlockState(rearPos).getCollisionShape(level, rearPos).isEmpty();
        final boolean leftFree = level.getBlockState(leftPos).getCollisionShape(level, leftPos).isEmpty();
        final boolean rightFree = level.getBlockState(rightPos).getCollisionShape(level, rightPos).isEmpty();

        return rearFree || leftFree || rightFree;
    }

    /**
     * Routes and ejects the generated drops into container below or out the sides.
     */
    public static void ejectDrops(Level level, BlockPos targetPos, Direction facing, List<ItemStack> drops, SawmillBlockEntity sawmill) {
        if (drops.isEmpty()) {
            return;
        }

        final List<ItemStack> remaining = new ArrayList<>();

        // Phase 1: Try container below (hopper, chest, etc.) or behind
        final BlockPos containerPos = targetPos.below();
        final IItemHandler handlerBelow = level.getCapability(Capabilities.ItemHandler.BLOCK, containerPos, Direction.UP);

        final Direction rearDir = facing.getOpposite();
        final BlockPos rearPos = targetPos.relative(rearDir);
        final IItemHandler handlerRear = level.getCapability(Capabilities.ItemHandler.BLOCK, rearPos, facing);

        for (ItemStack drop : drops) {
            ItemStack stackToRoute = drop.copy();
            if (handlerBelow != null) {
                stackToRoute = ItemHandlerHelper.insertItemStacked(handlerBelow, stackToRoute, false);
            }
            if (!stackToRoute.isEmpty() && handlerRear != null) {
                stackToRoute = ItemHandlerHelper.insertItemStacked(handlerRear, stackToRoute, false);
            }
            if (!stackToRoute.isEmpty()) {
                remaining.add(stackToRoute);
            }
        }

        if (remaining.isEmpty()) {
            return;
        }

        // Phase 2: Open ejection (prioritize rear exit, then sides)
        final Direction leftDir = facing.getCounterClockWise();
        final Direction rightDir = facing.getClockWise();
        final BlockPos leftPos = targetPos.relative(leftDir);
        final BlockPos rightPos = targetPos.relative(rightDir);

        final boolean rearFree = level.getBlockState(rearPos).getCollisionShape(level, rearPos).isEmpty();
        final boolean leftFree = level.getBlockState(leftPos).getCollisionShape(level, leftPos).isEmpty();
        final boolean rightFree = level.getBlockState(rightPos).getCollisionShape(level, rightPos).isEmpty();

        for (ItemStack stack : remaining) {
            if (rearFree) {
                spawnItemWithVelocity(level, targetPos, rearDir, stack);
            } else if (leftFree && rightFree) {
                final Direction chosenDir = sawmill.toggleAlternation() ? leftDir : rightDir;
                spawnItemWithVelocity(level, targetPos, chosenDir, stack);
            } else if (leftFree) {
                spawnItemWithVelocity(level, targetPos, leftDir, stack);
            } else if (rightFree) {
                spawnItemWithVelocity(level, targetPos, rightDir, stack);
            } else {
                spawnItemWithVelocity(level, targetPos, Direction.UP, stack);
            }
        }
    }

    private static void spawnItemWithVelocity(Level level, BlockPos targetPos, Direction dir, ItemStack stack) {
        if (stack.isEmpty()) return;
        final double x = targetPos.getX() + 0.5 + dir.getStepX() * 0.55;
        final double y = targetPos.getY() + 0.35 + (dir == Direction.UP ? 0.3 : 0);
        final double z = targetPos.getZ() + 0.5 + dir.getStepZ() * 0.55;

        final ItemEntity entity = new ItemEntity(level, x, y, z, stack);
        final double vx = dir.getStepX() * 0.12 + (level.random.nextDouble() - 0.5) * 0.04;
        final double vy = 0.10 + level.random.nextDouble() * 0.05;
        final double vz = dir.getStepZ() * 0.12 + (level.random.nextDouble() - 0.5) * 0.04;
        entity.setDeltaMovement(vx, vy, vz);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }

    private static boolean hasOpenSlot(IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).isEmpty() || handler.getStackInSlot(i).getCount() < handler.getSlotLimit(i)) {
                return true;
            }
        }
        return false;
    }
}
