package autosawmill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Baked geometry and rendering data extracted from the sawmill model.
 * Tower, gear, pin and rod have been stripped in favor of native TFC crankshaft drive.
 */
public final class SawmillModelData {

    public static final ResourceLocation SAWMILL_TEXTURE = ResourceLocation.fromNamespaceAndPath("autosawmill", "textures/block/sawmill.png");

    public record ModelCube(
        float minX, float minY, float minZ,
        float maxX, float maxY, float maxZ,
        float[] uvs, // 6 faces * 4 (u0, v0, u1, v1): north, south, east, west, up, down
        @Nullable float[] rot // ox, oy, oz, rx, ry, rz (degrees)
    ) {
        public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
            if (rot != null) {
                poseStack.pushPose();
                poseStack.translate(rot[0], rot[1], rot[2]);
                if (rot[5] != 0.0f) poseStack.mulPose(Axis.ZP.rotationDegrees(rot[5]));
                if (rot[4] != 0.0f) poseStack.mulPose(Axis.YP.rotationDegrees(rot[4]));
                if (rot[3] != 0.0f) poseStack.mulPose(Axis.XP.rotationDegrees(rot[3]));
                poseStack.translate(-rot[0], -rot[1], -rot[2]);
                renderCuboidQuads(poseStack, buffer, minX, minY, minZ, maxX, maxY, maxZ, uvs, packedLight, packedOverlay);
                poseStack.popPose();
            } else {
                renderCuboidQuads(poseStack, buffer, minX, minY, minZ, maxX, maxY, maxZ, uvs, packedLight, packedOverlay);
            }
        }
    }

    private static void renderCuboidQuads(
        PoseStack poseStack, VertexConsumer buffer,
        float x0, float y0, float z0,
        float x1, float y1, float z1,
        float[] uvs, int light, int overlay
    ) {
        var pose = poseStack.last();

        // 0: NORTH (facing -Z, normal 0, 0, -1) -> CCW order: (x0, y1) -> (x1, y1) -> (x1, y0) -> (x0, y0)
        addQuad(pose, buffer, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, uvs[0], uvs[1], uvs[2], uvs[3], 0, 0, -1, light, overlay);

        // 1: SOUTH (facing +Z, normal 0, 0, 1) -> CCW order: (x1, y1) -> (x0, y1) -> (x0, y0) -> (x1, y0)
        addQuad(pose, buffer, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, uvs[4], uvs[5], uvs[6], uvs[7], 0, 0, 1, light, overlay);

        // 2: EAST (facing +X, normal 1, 0, 0) -> CCW order: (y1, z0) -> (y1, z1) -> (y0, z1) -> (y0, z0)
        addQuad(pose, buffer, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, uvs[8], uvs[9], uvs[10], uvs[11], 1, 0, 0, light, overlay);

        // 3: WEST (facing -X, normal -1, 0, 0) -> CCW order: (y1, z1) -> (y1, z0) -> (y0, z0) -> (y0, z1)
        addQuad(pose, buffer, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, uvs[12], uvs[13], uvs[14], uvs[15], -1, 0, 0, light, overlay);

        // 4: UP (facing +Y, normal 0, 1, 0) -> CCW order: (x0, z1) -> (x1, z1) -> (x1, z0) -> (x0, z0)
        addQuad(pose, buffer, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, uvs[16], uvs[17], uvs[18], uvs[19], 0, 1, 0, light, overlay);

        // 5: DOWN (facing -Y, normal 0, -1, 0) -> CCW order: (x0, z0) -> (x1, z0) -> (x1, z1) -> (x0, z1)
        addQuad(pose, buffer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, uvs[20], uvs[21], uvs[22], uvs[23], 0, -1, 0, light, overlay);
    }

    private static void addQuad(
        PoseStack.Pose pose, VertexConsumer buffer,
        float x0, float y0, float z0,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        float u0, float v0, float u1, float v1,
        float nx, float ny, float nz,
        int light, int overlay
    ) {
        var mat = pose.pose();
        buffer.addVertex(mat, x0, y0, z0).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(mat, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(mat, x2, y2, z2).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(mat, x3, y3, z3).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /**
     * Moving carriage frame (vertical posts, top cross beam, middle cross beam).
     */
    public static final ModelCube[] MOVING_FRAME = new ModelCube[] {
        // 1. Right vertical post
        new ModelCube(0.64219f, 0.99062f, -0.06250f, 0.70469f, 2.55313f, 0.06250f, new float[] {0.02344f, 0.51562f, 0.03125f, 0.71094f, 0.03906f, 0.51562f, 0.04688f, 0.71094f, 0.33594f, 0.48438f, 0.35156f, 0.67969f, 0.35938f, 0.48438f, 0.37500f, 0.67969f, 0.53906f, 0.66406f, 0.54688f, 0.67969f, 0.66406f, 0.54688f, 0.67188f, 0.56250f}, null),

        // 2. Left vertical post
        new ModelCube(-1.17031f, 0.99062f, -0.06250f, -1.10781f, 2.55313f, 0.06250f, new float[] {0.05469f, 0.51562f, 0.06250f, 0.71094f, 0.07031f, 0.51562f, 0.07812f, 0.71094f, 0.38281f, 0.48438f, 0.39844f, 0.67969f, 0.40625f, 0.48438f, 0.42188f, 0.67969f, 0.55469f, 0.66406f, 0.56250f, 0.67969f, 0.57031f, 0.66406f, 0.57812f, 0.67969f}, null),

        // 3. Top horizontal beam
        new ModelCube(-1.23281f, 2.45938f, -0.03125f, 0.76719f, 2.52188f, 0.03125f, new float[] {0.51562f, 0.13281f, 0.75781f, 0.14062f, 0.51562f, 0.14844f, 0.75781f, 0.15625f, 0.56250f, 0.52344f, 0.57031f, 0.53125f, 0.10938f, 0.64062f, 0.11719f, 0.64844f, 0.51562f, 0.16406f, 0.75781f, 0.17188f, 0.51562f, 0.17969f, 0.75781f, 0.18750f}, null),

        // 4. Middle horizontal cross beam (connecting left & right vertical posts)
        new ModelCube(-1.10781f, 1.94375f, -0.04688f, 0.64219f, 2.03750f, 0.04688f, new float[] {0.42188f, 0.38281f, 0.63281f, 0.39844f, 0.31250f, 0.46094f, 0.52344f, 0.47656f, 0.08594f, 0.64062f, 0.10156f, 0.65625f, 0.64062f, 0.38281f, 0.65625f, 0.39844f, 0.46875f, 0.06250f, 0.67969f, 0.07812f, 0.46875f, 0.08594f, 0.67969f, 0.10156f}, null)
    };

    /**
     * Installable saw blade (horizontal steel blade bar + 15 saw teeth).
     */
    public static final ModelCube[] BLADE = new ModelCube[] {
        // Horizontal blade bar
        new ModelCube(-1.10781f, 1.08438f, -0.01562f, 0.64219f, 1.20938f, 0.01562f, new float[] {0.42188f, 0.00000f, 0.63281f, 0.01562f, 0.42188f, 0.03125f, 0.63281f, 0.04688f, 0.15625f, 0.65625f, 0.16406f, 0.67188f, 0.62500f, 0.65625f, 0.63281f, 0.67188f, 0.51562f, 0.19531f, 0.72656f, 0.20312f, 0.51562f, 0.21094f, 0.72656f, 0.21875f}, null),

        // 15 Saw teeth
        new ModelCube(-0.99844f, 1.03594f, -0.01250f, -0.86562f, 1.12500f, 0.01250f, new float[] {0.60938f, 0.01562f, 0.61719f, 0.02344f, 0.43750f, 0.04688f, 0.44531f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.53125f, 0.19531f, 0.53906f, 0.20312f, 0.53125f, 0.21094f, 0.53906f, 0.21875f}, new float[] {-0.9515625f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(-0.87344f, 1.03594f, -0.01250f, -0.74062f, 1.12500f, 0.01250f, new float[] {0.59375f, 0.01562f, 0.60156f, 0.02344f, 0.45312f, 0.04688f, 0.46094f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.54688f, 0.19531f, 0.55469f, 0.20312f, 0.54688f, 0.21094f, 0.55469f, 0.21875f}, new float[] {-0.8265625f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(-0.74844f, 1.03594f, -0.01250f, -0.61562f, 1.12500f, 0.01250f, new float[] {0.57812f, 0.01562f, 0.58594f, 0.02344f, 0.46875f, 0.04688f, 0.47656f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.56250f, 0.19531f, 0.57031f, 0.20312f, 0.56250f, 0.21094f, 0.57031f, 0.21875f}, new float[] {-0.7015625f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(-1.12344f, 1.03594f, -0.01250f, -0.99062f, 1.12500f, 0.01250f, new float[] {0.57812f, 0.01562f, 0.58594f, 0.02344f, 0.46875f, 0.04688f, 0.47656f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.56250f, 0.19531f, 0.57031f, 0.20312f, 0.56250f, 0.21094f, 0.57031f, 0.21875f}, new float[] {-1.0765625f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(-0.62344f, 1.03594f, -0.01250f, -0.49062f, 1.12500f, 0.01250f, new float[] {0.56250f, 0.01562f, 0.57031f, 0.02344f, 0.48438f, 0.04688f, 0.49219f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.57812f, 0.19531f, 0.58594f, 0.20312f, 0.57812f, 0.21094f, 0.58594f, 0.21875f}, new float[] {-0.5765625f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(-0.49844f, 1.03594f, -0.01250f, -0.36562f, 1.12500f, 0.01250f, new float[] {0.54688f, 0.01562f, 0.55469f, 0.02344f, 0.50000f, 0.04688f, 0.50781f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.59375f, 0.19531f, 0.60156f, 0.20312f, 0.59375f, 0.21094f, 0.60156f, 0.21875f}, new float[] {-0.4515625f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(-0.37344f, 1.03594f, -0.01250f, -0.24063f, 1.12500f, 0.01250f, new float[] {0.53125f, 0.01562f, 0.53906f, 0.02344f, 0.51562f, 0.04688f, 0.52344f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.60938f, 0.19531f, 0.61719f, 0.20312f, 0.60938f, 0.21094f, 0.61719f, 0.21875f}, new float[] {-0.3265625f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(-0.24844f, 1.03594f, -0.01250f, -0.11563f, 1.12500f, 0.01250f, new float[] {0.51562f, 0.01562f, 0.52344f, 0.02344f, 0.53125f, 0.04688f, 0.53906f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.62500f, 0.19531f, 0.63281f, 0.20312f, 0.62500f, 0.21094f, 0.63281f, 0.21875f}, new float[] {-0.2015625f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(-0.12344f, 1.03594f, -0.01250f, 0.00937f, 1.12500f, 0.01250f, new float[] {0.50000f, 0.01562f, 0.50781f, 0.02344f, 0.54688f, 0.04688f, 0.55469f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.64062f, 0.19531f, 0.64844f, 0.20312f, 0.64062f, 0.21094f, 0.64844f, 0.21875f}, new float[] {-0.0765625f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(0.00156f, 1.03594f, -0.01250f, 0.13437f, 1.12500f, 0.01250f, new float[] {0.48438f, 0.01562f, 0.49219f, 0.02344f, 0.56250f, 0.04688f, 0.57031f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.65625f, 0.19531f, 0.66406f, 0.20312f, 0.65625f, 0.21094f, 0.66406f, 0.21875f}, new float[] {0.0484375f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(0.12656f, 1.03594f, -0.01250f, 0.25938f, 1.12500f, 0.01250f, new float[] {0.46875f, 0.01562f, 0.47656f, 0.02344f, 0.57812f, 0.04688f, 0.58594f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.67188f, 0.19531f, 0.67969f, 0.20312f, 0.67188f, 0.21094f, 0.67969f, 0.21875f}, new float[] {0.1734375f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(0.25156f, 1.03594f, -0.01250f, 0.38438f, 1.12500f, 0.01250f, new float[] {0.45312f, 0.01562f, 0.46094f, 0.02344f, 0.59375f, 0.04688f, 0.60156f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.68750f, 0.19531f, 0.69531f, 0.20312f, 0.68750f, 0.21094f, 0.69531f, 0.21875f}, new float[] {0.2984375f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(0.37656f, 1.03594f, -0.01250f, 0.50938f, 1.12500f, 0.01250f, new float[] {0.43750f, 0.01562f, 0.44531f, 0.02344f, 0.60938f, 0.04688f, 0.61719f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.70312f, 0.19531f, 0.71094f, 0.20312f, 0.70312f, 0.21094f, 0.71094f, 0.21875f}, new float[] {0.4234375f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(0.50156f, 1.03594f, -0.01250f, 0.63438f, 1.12500f, 0.01250f, new float[] {0.42188f, 0.01562f, 0.42969f, 0.02344f, 0.62500f, 0.04688f, 0.63281f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.71875f, 0.19531f, 0.72656f, 0.20312f, 0.71875f, 0.21094f, 0.72656f, 0.21875f}, new float[] {0.5484375f, 1.0828125f, 0.0f, 0f, 0f, -45f}),
        new ModelCube(0.62656f, 1.03594f, -0.01250f, 0.69688f, 1.06250f, 0.01250f, new float[] {0.42188f, 0.01562f, 0.42969f, 0.02344f, 0.62500f, 0.04688f, 0.63281f, 0.05469f, 0.15625f, 0.67188f, 0.16406f, 0.67969f, 0.62500f, 0.67188f, 0.63281f, 0.67969f, 0.71875f, 0.19531f, 0.72656f, 0.20312f, 0.71875f, 0.21094f, 0.72656f, 0.21875f}, new float[] {0.6734375f, 1.0828125f, 0.0f, 0f, 0f, -45f})
    };

    /**
     * Complete combined array for backward compatibility.
     */
    public static final ModelCube[] SAW = new ModelCube[] {
        MOVING_FRAME[0], MOVING_FRAME[1],
        BLADE[1], BLADE[2], BLADE[3], BLADE[4], BLADE[5], BLADE[6], BLADE[7], BLADE[8], BLADE[9], BLADE[10], BLADE[11], BLADE[12], BLADE[13], BLADE[14], BLADE[15],
        BLADE[0], MOVING_FRAME[2], MOVING_FRAME[3]
    };

    /**
     * Compact mounting flange / clevis socket on the left side of the saw frame (upper level, Y ~ 1.95),
     * mating with the TFC crankshaft piston rod when mounted at FRAME_TOP.
     */
    public static final ModelCube[] FLANGE_LEFT = new ModelCube[] {
        new ModelCube(-1.26406f, 1.92500f, -0.06250f, -1.17031f, 2.05625f, 0.06250f, new float[] {0.66406f, 0.57031f, 0.67188f, 0.58594f, 0.66406f, 0.61719f, 0.67188f, 0.63281f, 0.66406f, 0.59375f, 0.67188f, 0.60938f, 0.64062f, 0.66406f, 0.64844f, 0.67969f, 0.15625f, 0.68750f, 0.16406f, 0.69531f, 0.25000f, 0.68750f, 0.25781f, 0.69531f}, null),
        new ModelCube(-1.35781f, 1.95625f, -0.03125f, -1.26406f, 2.02500f, 0.03125f, new float[] {0.53125f, 0.46094f, 0.55469f, 0.47656f, 0.21094f, 0.60156f, 0.23438f, 0.61719f, 0.66406f, 0.34375f, 0.67188f, 0.35938f, 0.66406f, 0.36719f, 0.67188f, 0.38281f, 0.64062f, 0.04688f, 0.66406f, 0.05469f, 0.64844f, 0.29688f, 0.67188f, 0.30469f}, null)
    };

    /**
     * Compact mounting flange / clevis socket on the right side of the saw frame (upper level, Y ~ 1.95),
     * mating with the TFC crankshaft piston rod when mounted at FRAME_TOP.
     */
    public static final ModelCube[] FLANGE_RIGHT = new ModelCube[] {
        new ModelCube(0.70469f, 1.92500f, -0.06250f, 0.79844f, 2.05625f, 0.06250f, new float[] {0.66406f, 0.57031f, 0.67188f, 0.58594f, 0.66406f, 0.61719f, 0.67188f, 0.63281f, 0.66406f, 0.59375f, 0.67188f, 0.60938f, 0.64062f, 0.66406f, 0.64844f, 0.67969f, 0.15625f, 0.68750f, 0.16406f, 0.69531f, 0.25000f, 0.68750f, 0.25781f, 0.69531f}, null),
        new ModelCube(0.79844f, 1.95625f, -0.03125f, 0.89219f, 2.02500f, 0.03125f, new float[] {0.53125f, 0.46094f, 0.55469f, 0.47656f, 0.21094f, 0.60156f, 0.23438f, 0.61719f, 0.66406f, 0.34375f, 0.67188f, 0.35938f, 0.66406f, 0.36719f, 0.67188f, 0.38281f, 0.64062f, 0.04688f, 0.66406f, 0.05469f, 0.64844f, 0.29688f, 0.67188f, 0.30469f}, null)
    };

    /**
     * Compact mounting flange on the left side at blade height (Y ~ 1.14),
     * mating with the TFC crankshaft piston rod when mounted at ground/BED level.
     */
    public static final ModelCube[] FLANGE_LOWER_LEFT = new ModelCube[] {
        new ModelCube(-1.26406f, 1.09000f, -0.06250f, -1.17031f, 1.21000f, 0.06250f, new float[] {0.66406f, 0.57031f, 0.67188f, 0.58594f, 0.66406f, 0.61719f, 0.67188f, 0.63281f, 0.66406f, 0.59375f, 0.67188f, 0.60938f, 0.64062f, 0.66406f, 0.64844f, 0.67969f, 0.15625f, 0.68750f, 0.16406f, 0.69531f, 0.25000f, 0.68750f, 0.25781f, 0.69531f}, null),
        new ModelCube(-1.35781f, 1.11500f, -0.03125f, -1.26406f, 1.18500f, 0.03125f, new float[] {0.53125f, 0.46094f, 0.55469f, 0.47656f, 0.21094f, 0.60156f, 0.23438f, 0.61719f, 0.66406f, 0.34375f, 0.67188f, 0.35938f, 0.66406f, 0.36719f, 0.67188f, 0.38281f, 0.64062f, 0.04688f, 0.66406f, 0.05469f, 0.64844f, 0.29688f, 0.67188f, 0.30469f}, null)
    };

    /**
     * Compact mounting flange on the right side at blade height (Y ~ 1.14),
     * mating with the TFC crankshaft piston rod when mounted at ground/BED level.
     */
    public static final ModelCube[] FLANGE_LOWER_RIGHT = new ModelCube[] {
        new ModelCube(0.70469f, 1.09000f, -0.06250f, 0.79844f, 1.21000f, 0.06250f, new float[] {0.66406f, 0.57031f, 0.67188f, 0.58594f, 0.66406f, 0.61719f, 0.67188f, 0.63281f, 0.66406f, 0.59375f, 0.67188f, 0.60938f, 0.64062f, 0.66406f, 0.64844f, 0.67969f, 0.15625f, 0.68750f, 0.16406f, 0.69531f, 0.25000f, 0.68750f, 0.25781f, 0.69531f}, null),
        new ModelCube(0.79844f, 1.11500f, -0.03125f, 0.89219f, 1.18500f, 0.03125f, new float[] {0.53125f, 0.46094f, 0.55469f, 0.47656f, 0.21094f, 0.60156f, 0.23438f, 0.61719f, 0.66406f, 0.34375f, 0.67188f, 0.35938f, 0.66406f, 0.36719f, 0.67188f, 0.38281f, 0.64062f, 0.04688f, 0.66406f, 0.05469f, 0.64844f, 0.29688f, 0.67188f, 0.30469f}, null)
    };

    /**
     * Steel drive linkage extending from the right flange through the static frame post (upper level)
     * to meet the TFC crankshaft piston head in a continuous kinematic chain.
     */
    public static final ModelCube[] DRIVE_LINKAGE_RIGHT = new ModelCube[] {
        new ModelCube(0.85000f, 1.95625f, -0.03125f, 1.60000f, 2.02500f, 0.03125f, new float[] {0.53125f, 0.46094f, 0.55469f, 0.47656f, 0.21094f, 0.60156f, 0.23438f, 0.61719f, 0.66406f, 0.34375f, 0.67188f, 0.35938f, 0.66406f, 0.36719f, 0.67188f, 0.38281f, 0.64062f, 0.04688f, 0.66406f, 0.05469f, 0.64844f, 0.29688f, 0.67188f, 0.30469f}, null),
        new ModelCube(1.48000f, 1.92500f, -0.06250f, 1.60000f, 2.05625f, 0.06250f, new float[] {0.66406f, 0.57031f, 0.67188f, 0.58594f, 0.66406f, 0.61719f, 0.67188f, 0.63281f, 0.66406f, 0.59375f, 0.67188f, 0.60938f, 0.64062f, 0.66406f, 0.64844f, 0.67969f, 0.15625f, 0.68750f, 0.16406f, 0.69531f, 0.25000f, 0.68750f, 0.25781f, 0.69531f}, null)
    };

    /**
     * Steel drive linkage extending from the left flange through the static frame post (upper level)
     * to meet the TFC crankshaft piston head in a continuous kinematic chain.
     */
    public static final ModelCube[] DRIVE_LINKAGE_LEFT = new ModelCube[] {
        new ModelCube(-1.60000f, 1.95625f, -0.03125f, -1.26000f, 2.02500f, 0.03125f, new float[] {0.53125f, 0.46094f, 0.55469f, 0.47656f, 0.21094f, 0.60156f, 0.23438f, 0.61719f, 0.66406f, 0.34375f, 0.67188f, 0.35938f, 0.66406f, 0.36719f, 0.67188f, 0.38281f, 0.64062f, 0.04688f, 0.66406f, 0.05469f, 0.64844f, 0.29688f, 0.67188f, 0.30469f}, null),
        new ModelCube(-1.60000f, 1.92500f, -0.06250f, -1.48000f, 2.05625f, 0.06250f, new float[] {0.66406f, 0.57031f, 0.67188f, 0.58594f, 0.66406f, 0.61719f, 0.67188f, 0.63281f, 0.66406f, 0.59375f, 0.67188f, 0.60938f, 0.64062f, 0.66406f, 0.64844f, 0.67969f, 0.15625f, 0.68750f, 0.16406f, 0.69531f, 0.25000f, 0.68750f, 0.25781f, 0.69531f}, null)
    };

    /**
     * Steel drive linkage extending from the left flange through the static frame post at blade height (lower level)
     * into the TFC crankshaft piston rod.
     */
    public static final ModelCube[] DRIVE_LINKAGE_LOWER_LEFT = new ModelCube[] {
        new ModelCube(-1.60000f, 1.11500f, -0.03125f, -1.26000f, 1.18500f, 0.03125f, new float[] {0.53125f, 0.46094f, 0.55469f, 0.47656f, 0.21094f, 0.60156f, 0.23438f, 0.61719f, 0.66406f, 0.34375f, 0.67188f, 0.35938f, 0.66406f, 0.36719f, 0.67188f, 0.38281f, 0.64062f, 0.04688f, 0.66406f, 0.05469f, 0.64844f, 0.29688f, 0.67188f, 0.30469f}, null),
        new ModelCube(-1.60000f, 1.09000f, -0.06250f, -1.48000f, 1.21000f, 0.06250f, new float[] {0.66406f, 0.57031f, 0.67188f, 0.58594f, 0.66406f, 0.61719f, 0.67188f, 0.63281f, 0.66406f, 0.59375f, 0.67188f, 0.60938f, 0.64062f, 0.66406f, 0.64844f, 0.67969f, 0.15625f, 0.68750f, 0.16406f, 0.69531f, 0.25000f, 0.68750f, 0.25781f, 0.69531f}, null)
    };

    /**
     * Steel drive linkage extending from the right flange through the static frame post at blade height (lower level)
     * into the TFC crankshaft piston rod.
     */
    public static final ModelCube[] DRIVE_LINKAGE_LOWER_RIGHT = new ModelCube[] {
        new ModelCube(0.85000f, 1.11500f, -0.03125f, 1.60000f, 1.18500f, 0.03125f, new float[] {0.53125f, 0.46094f, 0.55469f, 0.47656f, 0.21094f, 0.60156f, 0.23438f, 0.61719f, 0.66406f, 0.34375f, 0.67188f, 0.35938f, 0.66406f, 0.36719f, 0.67188f, 0.38281f, 0.64062f, 0.04688f, 0.66406f, 0.05469f, 0.64844f, 0.29688f, 0.67188f, 0.30469f}, null),
        new ModelCube(1.48000f, 1.09000f, -0.06250f, 1.60000f, 1.21000f, 0.06250f, new float[] {0.66406f, 0.57031f, 0.67188f, 0.58594f, 0.66406f, 0.61719f, 0.67188f, 0.63281f, 0.66406f, 0.59375f, 0.67188f, 0.60938f, 0.64062f, 0.66406f, 0.64844f, 0.67969f, 0.15625f, 0.68750f, 0.16406f, 0.69531f, 0.25000f, 0.68750f, 0.25781f, 0.69531f}, null)
    };

    private SawmillModelData() {}
}
