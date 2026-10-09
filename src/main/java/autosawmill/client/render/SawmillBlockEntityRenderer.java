package autosawmill.client.render;

import autosawmill.common.block.SawmillBlock;
import autosawmill.common.blockentity.SawmillBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.dries007.tfc.common.blockentities.rotation.CrankshaftBlockEntity;
import net.dries007.tfc.common.blocks.rotation.CrankshaftBlock;
import net.dries007.tfc.util.rotation.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * High-performance, clean BlockEntityRenderer for the Sawmill.
 * Renders the reciprocating saw frame & blade synchronized with the native TFC crankshaft,
 * along with the bilateral mounting flange and multi-log cutting items.
 */
public class SawmillBlockEntityRenderer implements BlockEntityRenderer<SawmillBlockEntity> {

    // Maximum vertical stroke: 12 pixels = 0.75 blocks
    private static final float MAX_VERTICAL_STROKE = 12.0f / 16.0f;
    // Subtle horizontal pitch: 1.2 pixels = 0.075 blocks
    private static final float MAX_HORIZONTAL_PITCH = 1.2f / 16.0f;

    public SawmillBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SawmillBlockEntity sawmill, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        final Level level = sawmill.getLevel();
        if (level == null) {
            return;
        }

        final BlockState state = sawmill.getBlockState();
        if (!(state.getBlock() instanceof SawmillBlock)) {
            return;
        }

        final Direction facing = state.getValue(SawmillBlock.FACING);

        // 1. Calculate analytical displacement directly from native TFC crankshaft
        final CrankshaftBlockEntity crank = sawmill.getCrankBlockEntity();
        float xOffset = 0.0f;
        float yOffset = 0.0f;

        if (crank != null) {
            final Rotation rot = crank.getRotationNode().rotation();
            final boolean isRotating = rot != null && Math.abs(rot.speed()) > 0.001f;

            if (isRotating) {
                final Direction crankFace = crank.getBlockState().getValue(CrankshaftBlock.FACING);
                final float angle = CrankshaftBlockEntity.calculateRealRotationAngle(crank, crankFace, partialTick);

                // 1 full crankshaft revolution = 1 full down-up stroke of the saw frame
                // Harmonic motion: starts at rest (0), descends to MAX_VERTICAL_STROKE, returns to 0
                yOffset = -(1.0f - Mth.cos(angle)) * 0.5f * MAX_VERTICAL_STROKE;
                xOffset = Mth.sin(angle) * MAX_HORIZONTAL_PITCH;
            }
        }

        poseStack.pushPose();

        // 2. Align to center of block and rotate to match blockstate FACING
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YN.rotationDegrees((facing.toYRot() + 180.0f) % 360.0f));

        // --- Pass 1: Reciprocating Saw Frame, Blade & Bilateral Flange ---
        final VertexConsumer frameBuffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(SawmillModelData.SAWMILL_TEXTURE));

        poseStack.pushPose();
        poseStack.translate(xOffset, yOffset, 0.0f);

        // Render saw frame and teeth
        for (var cube : SawmillModelData.SAW) {
            cube.render(poseStack, frameBuffer, packedLight, packedOverlay);
        }

        // Render mounting flange on the connection side (or default left if not connected yet)
        final SawmillBlockEntity.CrankSide side = sawmill.getCrankConnectionSide();
        if (side == SawmillBlockEntity.CrankSide.RIGHT) {
            for (var cube : SawmillModelData.FLANGE_RIGHT) {
                cube.render(poseStack, frameBuffer, packedLight, packedOverlay);
            }
        } else {
            for (var cube : SawmillModelData.FLANGE_LEFT) {
                cube.render(poseStack, frameBuffer, packedLight, packedOverlay);
            }
        }

        poseStack.popPose();

        // --- Pass 2: Direct Item Input 3D Multi-Log Rendering ---
        if (sawmill.hasInputItem()) {
            final net.minecraft.world.item.ItemStack input = sawmill.getInputItem();
            final BlockState logState = resolveBlockStateForInput(input);
            final java.util.List<LogTransform> transforms = getLogTransforms(input.getCount());

            final float isSawing = (sawmill.hasInputItem() && sawmill.hasKineticPower()) ? 1.0f : 0.0f;
            final float time = level.getGameTime() + partialTick;
            final int light = Math.max(packedLight, net.minecraft.client.renderer.LevelRenderer.getLightColor(level, sawmill.getBlockPos().above()));

            for (LogTransform t : transforms) {
                poseStack.pushPose();
                final float vibX = isSawing * (float) Math.sin(time * 3.1f + t.x * 7.0f) * 0.002f;
                final float vibY = isSawing * (float) Math.cos(time * 3.7f + t.y * 5.0f) * 0.002f;
                poseStack.translate(t.x + vibX, t.y + vibY, t.z);

                if (logState != null) {
                    poseStack.scale(t.sx, t.sy, t.sz);
                    poseStack.translate(-0.5D, -0.5D, -0.5D);
                    Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                        logState,
                        poseStack,
                        bufferSource,
                        light,
                        packedOverlay
                    );
                } else {
                    poseStack.scale(t.sx * 2.0f, t.sy * 2.0f, t.sz);
                    Minecraft.getInstance().getItemRenderer().renderStatic(
                        input,
                        ItemDisplayContext.FIXED,
                        light,
                        packedOverlay,
                        poseStack,
                        bufferSource,
                        level,
                        0
                    );
                }
                poseStack.popPose();
            }
        }

        poseStack.popPose();
    }

    public record LogTransform(float x, float y, float z, float sx, float sy, float sz) {}

    public static java.util.List<LogTransform> getLogTransforms(int count) {
        final java.util.List<LogTransform> list = new java.util.ArrayList<>();
        final int c = Math.min(Math.max(count, 1), 16);
        final float D = 0.22f;
        final float R = D * 0.5f;
        final float dy = D * 0.866025f;
        final float yBed = 0.25f;
        final float xc = -0.24f;
        final float sz = 0.85f;
        final float zc = 0.0f;

        if (c == 1) {
            list.add(new LogTransform(xc, yBed + R, zc, D, D, sz));
        } else if (c == 2) {
            addRow(list, 2, xc, yBed + R, zc, D, sz);
        } else if (c == 3) {
            addRow(list, 2, xc, yBed + R, zc, D, sz);
            list.add(new LogTransform(xc, yBed + R + dy, zc, D, D, sz));
        } else if (c == 4) {
            addRow(list, 2, xc, yBed + R, zc, D, sz);
            addRow(list, 2, xc, yBed + R + D, zc, D, sz);
        } else if (c == 5) {
            addRow(list, 3, xc, yBed + R, zc, D, sz);
            addRow(list, 2, xc, yBed + R + dy, zc, D, sz);
        } else if (c == 6) {
            addRow(list, 3, xc, yBed + R, zc, D, sz);
            addRow(list, 2, xc, yBed + R + dy, zc, D, sz);
            list.add(new LogTransform(xc, yBed + R + 2f * dy, zc, D, D, sz));
        } else if (c == 7) {
            addRow(list, 4, xc, yBed + R, zc, D, sz);
            addRow(list, 2, xc, yBed + R + dy, zc, D, sz);
            list.add(new LogTransform(xc, yBed + R + 2f * dy, zc, D, D, sz));
        } else if (c == 8) {
            addRow(list, 4, xc, yBed + R, zc, D, sz);
            addRow(list, 3, xc, yBed + R + dy, zc, D, sz);
            list.add(new LogTransform(xc, yBed + R + 2f * dy, zc, D, D, sz));
        } else if (c == 9) {
            addRow(list, 4, xc, yBed + R, zc, D, sz);
            addRow(list, 3, xc, yBed + R + dy, zc, D, sz);
            addRow(list, 2, xc, yBed + R + 2f * dy, zc, D, sz);
        } else if (c == 10) {
            addRow(list, 4, xc, yBed + R, zc, D, sz);
            addRow(list, 3, xc, yBed + R + dy, zc, D, sz);
            addRow(list, 2, xc, yBed + R + 2f * dy, zc, D, sz);
            list.add(new LogTransform(xc, yBed + R + 3f * dy, zc, D, D, sz));
        } else if (c == 11) {
            addRow(list, 5, xc, yBed + R, zc, D, sz);
            addRow(list, 3, xc, yBed + R + dy, zc, D, sz);
            addRow(list, 2, xc, yBed + R + 2f * dy, zc, D, sz);
            list.add(new LogTransform(xc, yBed + R + 3f * dy, zc, D, D, sz));
        } else if (c == 12) {
            addRow(list, 5, xc, yBed + R, zc, D, sz);
            addRow(list, 4, xc, yBed + R + dy, zc, D, sz);
            addRow(list, 2, xc, yBed + R + 2f * dy, zc, D, sz);
            list.add(new LogTransform(xc, yBed + R + 3f * dy, zc, D, D, sz));
        } else if (c == 13) {
            addRow(list, 5, xc, yBed + R, zc, D, sz);
            addRow(list, 4, xc, yBed + R + dy, zc, D, sz);
            addRow(list, 3, xc, yBed + R + 2f * dy, zc, D, sz);
            list.add(new LogTransform(xc, yBed + R + 3f * dy, zc, D, D, sz));
        } else if (c == 14) {
            addRow(list, 5, xc, yBed + R, zc, D, sz);
            addRow(list, 4, xc, yBed + R + dy, zc, D, sz);
            addRow(list, 3, xc, yBed + R + 2f * dy, zc, D, sz);
            addRow(list, 2, xc, yBed + R + 3f * dy, zc, D, sz);
        } else if (c == 15) {
            addRow(list, 5, xc, yBed + R, zc, D, sz);
            addRow(list, 4, xc, yBed + R + dy, zc, D, sz);
            addRow(list, 3, xc, yBed + R + 2f * dy, zc, D, sz);
            addRow(list, 2, xc, yBed + R + 3f * dy, zc, D, sz);
            list.add(new LogTransform(xc, yBed + R + 4f * dy, zc, D, D, sz));
        } else {
            addRow(list, 6, xc, yBed + R, zc, D, sz);
            addRow(list, 4, xc, yBed + R + dy, zc, D, sz);
            addRow(list, 3, xc, yBed + R + 2f * dy, zc, D, sz);
            addRow(list, 2, xc, yBed + R + 3f * dy, zc, D, sz);
            list.add(new LogTransform(xc, yBed + R + 4f * dy, zc, D, D, sz));
        }
        return list;
    }

    private static void addRow(java.util.List<LogTransform> list, int n, float xc, float y, float zc, float D, float sz) {
        final float startX = xc - (n - 1) * 0.5f * D;
        for (int i = 0; i < n; i++) {
            list.add(new LogTransform(startX + i * D, y, zc, D, D, sz));
        }
    }

    public static BlockState resolveBlockStateForInput(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return null;
        final autosawmill.common.util.WoodHelper.WoodTarget target = autosawmill.common.util.WoodHelper.resolveItemTarget(stack);
        if (target != null && target.wood() != null) {
            if (target.type() == autosawmill.common.util.WoodHelper.TargetType.LOG) {
                var blockHolder = net.dries007.tfc.common.blocks.TFCBlocks.WOODS.get(target.wood()).get(net.dries007.tfc.common.blocks.wood.Wood.BlockType.LOG);
                if (blockHolder != null) {
                    return blockHolder.get().defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z);
                }
            } else if (target.type() == autosawmill.common.util.WoodHelper.TargetType.STRIPPED_LOG || target.type() == autosawmill.common.util.WoodHelper.TargetType.SUPPORT_PILE) {
                var blockHolder = net.dries007.tfc.common.blocks.TFCBlocks.WOODS.get(target.wood()).get(net.dries007.tfc.common.blocks.wood.Wood.BlockType.STRIPPED_LOG);
                if (blockHolder != null) {
                    return blockHolder.get().defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z);
                }
            }
        }
        final net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
        if (block != net.minecraft.world.level.block.Blocks.AIR) {
            BlockState bs = block.defaultBlockState();
            if (bs.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)) {
                bs = bs.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z);
            }
            return bs;
        }
        return null;
    }

    @Override
    public boolean shouldRenderOffScreen(SawmillBlockEntity blockEntity) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(SawmillBlockEntity blockEntity) {
        final BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos).inflate(4.0, 2.5, 4.0);
    }
}
