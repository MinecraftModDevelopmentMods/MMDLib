package zone.moddev.mc.lib.machine.neoforge.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import net.neoforged.neoforge.client.model.IQuadTransformer;

import zone.moddev.mc.lib.machine.presentation.EmissiveMask;

/**
 * Partitions rectangular UV quads into lit/unlit regions without overlapping
 * geometry.
 */
final class MaskedFaceGeometry {
    private MaskedFaceGeometry() {
    }

    static List<BakedQuad> apply(BakedQuad quad, TextureAtlasSprite texture,
        EmissiveMask mask) {
        int stride = IQuadTransformer.STRIDE, uv = IQuadTransformer.UV0;
        var source = quad.getVertices();
        float[] u = new float[4], v = new float[4];
        float u0 = Float.POSITIVE_INFINITY, v0 = u0,
            u1 = Float.NEGATIVE_INFINITY, v1 = u1;
        for (int i = 0; i < 4; i++) {
            u[i] = quad.getSprite()
                .getUOffset(Float.intBitsToFloat(source[i * stride + uv]));
            v[i] = quad.getSprite()
                .getVOffset(Float.intBitsToFloat(source[i * stride + uv + 1]));
            u0 = Math.min(u0, u[i]);
            u1 = Math.max(u1, u[i]);
            v0 = Math.min(v0, v[i]);
            v1 = Math.max(v1, v[i]);
        }
        if (u1 - u0 < 0.000001f || v1 - v0 < 0.000001f)
            throw new IllegalArgumentException(
                "Emissive masks require non-degenerate rectangular UVs");
        int[] corners = {-1, -1, -1, -1};
        int[] vertexCorners = new int[4];
        for (int i = 0; i < 4; i++) {
            boolean right = Math.abs(u[i] - u1) < 0.00001f;
            boolean bottom = Math.abs(v[i] - v1) < 0.00001f;
            if ((!right && Math.abs(u[i] - u0) >= 0.00001f)
                || (!bottom && Math.abs(v[i] - v0) >= 0.00001f))
                throw new IllegalArgumentException(
                    "Emissive masks require rectangular UVs");
            int corner = (right ? 1 : 0) + (bottom ? 2 : 0);
            if (corners[corner] != -1)
                throw new IllegalArgumentException(
                    "Duplicate emissive-mask UV corner");
            corners[corner] = i;
            vertexCorners[i] = corner;
        }
        var result = new ArrayList<BakedQuad>();
        for (var region : mask.regions()) {
            float left = Math.max(u0, (float) region.x0() / mask.width());
            float right = Math.min(u1, (float) region.x1() / mask.width());
            float top = Math.max(v0, (float) region.y0() / mask.height());
            float bottom = Math.min(v1, (float) region.y1() / mask.height());
            if (left >= right || top >= bottom)
                continue;
            int[] vertices = source.clone();
            for (int i = 0; i < 4; i++) {
                int offset = i * stride;
                float nextU = (vertexCorners[i] & 1) == 0 ? left : right;
                float nextV = (vertexCorners[i] & 2) == 0 ? top : bottom;
                float s = (nextU - u0) / (u1 - u0),
                    t = (nextV - v0) / (v1 - v0);
                float[] weights = {(1 - s) * (1 - t), s * (1 - t), (1 - s) * t,
                    s * t};
                for (int axis = 0; axis < 3; axis++) {
                    float coordinate = 0;
                    for (int corner = 0; corner < 4; corner++)
                        coordinate += Float
                            .intBitsToFloat(source[corners[corner] * stride
                                + IQuadTransformer.POSITION + axis])
                            * weights[corner];
                    vertices[offset + IQuadTransformer.POSITION + axis] = Float
                        .floatToRawIntBits(coordinate);
                }
                vertices[offset + IQuadTransformer.COLOR] = interpolatePacked(
                    source, corners, weights, IQuadTransformer.COLOR, 8);
                vertices[offset + IQuadTransformer.UV2] = region.emissive()
                    ? LightTexture.FULL_BRIGHT
                    : interpolatePacked(source, corners, weights,
                        IQuadTransformer.UV2, 16);
                vertices[offset + uv] = Float
                    .floatToRawIntBits(texture.getU(nextU));
                vertices[offset + uv + 1] = Float
                    .floatToRawIntBits(texture.getV(nextV));
            }
            result.add(new BakedQuad(vertices, quad.getTintIndex(),
                quad.getDirection(), texture,
                !region.emissive() && quad.isShade(),
                !region.emissive() && quad.hasAmbientOcclusion()));
        }
        return List.copyOf(result);
    }

    private static int interpolatePacked(int[] vertices, int[] corners,
        float[] weights, int field, int bits) {
        int result = 0, mask = (1 << bits) - 1;
        for (int shift = 0; shift < 32; shift += bits) {
            float value = 0;
            for (int corner = 0; corner < 4; corner++)
                value += ((vertices[corners[corner] * IQuadTransformer.STRIDE
                    + field] >>> shift) & mask) * weights[corner];
            result |= (Math.round(value) & mask) << shift;
        }
        return result;
    }
}
