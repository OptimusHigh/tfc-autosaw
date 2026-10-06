package autosawmill.client.render;

import autosawmill.common.block.SawmillBlock;
import autosawmill.common.blockentity.SawmillBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * High-performance, clean BlockEntityRenderer for the Sawmill.
 * Renders the moving brass gear with eccentric pin, the oscillating connecting rod,
 * and the vertically reciprocating saw frame.
 */
public class SawmillBlockEntityRenderer implements BlockEntityRenderer<SawmillBlockEntity> {

    // Crank-slider kinematic dimensions (in pixels from model geometry)
    private static final float PIN_RADIUS = 4.950f;
    private static final float PIN_INITIAL_PHASE = 135.0f * (Mth.PI / 180.0f);
    private static final float SLIDER_X = -30.475f;
    private static final float BASE_SLIDER_Y = 31.850f;

    // 33 Keyframe points extracted directly from 'Пила (9).bbmodel' (animation 'animation', length 8.0s)
    private static final float[] SAW_X = new float[] {
        -0.055f, -2.160f, -2.020f,  0.875f,
         5.565f,  8.380f,  7.390f,  3.795f,
         0.315f, -1.070f, -0.245f,  3.160f,
         7.025f,  8.810f,  6.910f,  3.095f,
        -0.230f, -1.175f,  0.315f,  3.760f,
         7.470f,  7.890f,  5.155f,  0.685f,
        -2.350f, -2.145f,  0.465f,  3.680f,
         7.185f,  8.780f,  7.465f,  3.920f,
        -0.055f
    };

    private static final float[] SAW_Y = new float[] {
         -0.550f,  -0.620f,  -1.250f,  -1.840f,
         -2.500f,  -3.090f,  -3.750f,  -4.340f,
         -5.000f,  -5.590f,  -6.250f,  -6.850f,
         -7.500f,  -8.000f,  -8.560f,  -9.090f,
         -9.740f, -10.460f, -11.250f, -11.830f,
        -12.470f, -13.110f, -13.810f, -14.450f,
        -15.000f, -13.810f, -12.100f, -10.430f,
         -8.460f,  -6.470f,  -4.270f,  -2.270f,
         -0.550f
    };

    private static float evalCatmullRom(float[] arr, float pos) {
        final float p = (pos % 32.0f + 32.0f) % 32.0f;
        final int i = (int) p;
        final float t = p - i;
        final float p0 = arr[(i - 1 + 32) % 32];
        final float p1 = arr[i];
        final float p2 = arr[(i + 1) % 32];
        final float p3 = arr[(i + 2) % 32];
        return 0.5f * (
            (2.0f * p1) +
            (-p0 + p2) * t +
            (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * (t * t) +
            (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * (t * t * t)
        );
    }

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
        final float continuousAngle = sawmill.getContinuousAngle(partialTick);

        // Model animation has 4 revolutions (8*PI radians) per 8-second cycle with 32 segments (45 deg each):
        final float twoPi4 = 8.0f * (float) Math.PI;
        final float animAngle = ((continuousAngle % twoPi4) + twoPi4) % twoPi4;
        final float pos = animAngle * (4.0f / (float) Math.PI);

        final float sawXPixels = evalCatmullRom(SAW_X, pos);
        final float sawYPixels = evalCatmullRom(SAW_Y, pos);

        final float xOffset = sawXPixels / 16.0f; // in blocks
        final float yOffset = sawYPixels / 16.0f; // in blocks

        final float pinX = -48.0f + PIN_RADIUS * Mth.cos(animAngle + PIN_INITIAL_PHASE);
        final float pinY = 24.0f + PIN_RADIUS * Mth.sin(animAngle + PIN_INITIAL_PHASE);

        final float sliderX = SLIDER_X + sawXPixels;
        final float sliderY = BASE_SLIDER_Y + sawYPixels;

        final float rodAngle = (float) Math.atan2(pinY - sliderY, pinX - sliderX);

        poseStack.pushPose();

        // 1. Align to center of block and rotate to match blockstate FACING
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YN.rotationDegrees((facing.toYRot() + 180.0f) % 360.0f));

        // --- Pass 1: Sawmill Frame, Housing, Blades & Rod (SAWMILL_TEXTURE) ---
        final VertexConsumer frameBuffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(SawmillModelData.SAWMILL_TEXTURE));

        // 2. Static Gearbox Housing
        SawmillModelData.GEARBOX.render(poseStack, frameBuffer, packedLight, packedOverlay);

        // 3. Eccentric Pin (rotates on the gear face)
        poseStack.pushPose();
        poseStack.translate(-3.0f, 1.5f, 0.09375f);
        poseStack.mulPose(Axis.ZP.rotation(animAngle));
        for (var cube : SawmillModelData.PIN) {
            cube.render(poseStack, frameBuffer, packedLight, packedOverlay);
        }
        poseStack.popPose();

        // 4. Reciprocating Saw Frame & Blade (Horizontally & Vertically animated)
        poseStack.pushPose();
        poseStack.translate(xOffset, yOffset, 0.0f);
        for (var cube : SawmillModelData.SAW) {
            cube.render(poseStack, frameBuffer, packedLight, packedOverlay);
        }
        poseStack.popPose();

        // 5. Oscillating Connecting Rod (Шатун)
        poseStack.pushPose();
        poseStack.translate(sliderX / 16.0f, sliderY / 16.0f, 0.0f);
        poseStack.mulPose(Axis.ZP.rotation(rodAngle - (float) Math.PI));
        for (var cube : SawmillModelData.ROD) {
            cube.render(poseStack, frameBuffer, packedLight, packedOverlay);
        }
        poseStack.popPose();

        // --- Pass 2: Rotating Brass Gear (GEAR_TEXTURE) ---
        final VertexConsumer gearBuffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(SawmillModelData.GEAR_TEXTURE));
        poseStack.pushPose();
        poseStack.translate(-3.0f, 1.5f, 0.09375f);
        poseStack.mulPose(Axis.ZP.rotation(animAngle));
        for (var cube : SawmillModelData.BRASS_GEAR) {
            cube.render(poseStack, gearBuffer, packedLight, packedOverlay);
        }
        poseStack.popPose();

        // --- Pass 3: Direct Item Input 3D Multi-Log Rendering ---
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
