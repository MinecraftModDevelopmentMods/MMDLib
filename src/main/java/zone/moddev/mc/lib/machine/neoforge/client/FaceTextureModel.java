package zone.moddev.mc.lib.machine.neoforge.client;

import java.util.List;
import java.util.Map;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import zone.moddev.mc.lib.machine.neoforge.MachineVisualState;
import zone.moddev.mc.lib.machine.presentation.EmissiveMask;
import zone.moddev.mc.lib.machine.presentation.VisualCondition;

/**
 * Replaces base-face sprites while retaining geometry, UV layout, tint,
 * lighting and render layer.
 */
final class FaceTextureModel extends BakedModelWrapper<BakedModel> {
    record Rule(Direction face, VisualCondition condition,
        TextureAtlasSprite texture, EmissiveMask mask) {
    }
    private record Change(BakedQuad quad, TextureAtlasSprite texture,
        EmissiveMask mask) {
    }

    private final List<Rule> rules;
    private final ModelProperty<Map<Direction, Rule>> selection = new ModelProperty<>();
    private final LoadingCache<Change, List<BakedQuad>> retextured = CacheBuilder
        .newBuilder().maximumSize(1024)
        .build(CacheLoader.from(change -> change.mask() == null
            ? List.of(retexture(change.quad(), change.texture()))
            : MaskedFaceGeometry.apply(change.quad(), change.texture(),
                change.mask())));

    FaceTextureModel(BakedModel base, List<Rule> rules) {
        super(base);
        this.rules = List.copyOf(rules);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos,
        BlockState state, ModelData data) {
        var child = originalModel.getModelData(level, pos, state, data);
        var values = data.get(MachineVisualState.VARIABLES);
        var result = child.derive().with(selection,
            select(values == null ? Map.of() : values));
        if (values != null)
            result.with(MachineVisualState.VARIABLES, values);
        return result.build();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side,
        RandomSource random) {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side,
        RandomSource random, ModelData data, RenderType type) {
        var source = originalModel.getQuads(state, side, random, data, type);
        var selected = data.get(selection);
        if (selected == null) {
            var values = data.get(MachineVisualState.VARIABLES);
            selected = select(values == null ? Map.of() : values);
        }
        if (selected.isEmpty())
            return source;
        var textures = selected;
        return source.stream().flatMap(quad -> {
            var replacement = textures.get(quad.getDirection());
            return (replacement == null
                || (replacement.texture() == quad.getSprite()
                    && replacement.mask() == null)
                        ? List.of(quad)
                        : retextured.getUnchecked(new Change(quad,
                            replacement.texture(), replacement.mask())))
                .stream();
        }).toList();
    }

    private Map<Direction, Rule> select(Map<String, Object> snapshot) {
        var selected = new java.util.EnumMap<Direction, Rule>(Direction.class);
        for (var rule : rules)
            if (!selected.containsKey(rule.face())
                && rule.condition().test(snapshot))
                selected.put(rule.face(), rule);
        return Map.copyOf(selected);
    }

    private static BakedQuad retexture(BakedQuad quad,
        TextureAtlasSprite texture) {
        int[] vertices = quad.getVertices().clone();
        var original = quad.getSprite();
        for (int vertex = 0; vertex < 4; vertex++) {
            int uv = vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            float u = original.getUOffset(Float.intBitsToFloat(vertices[uv]));
            float v = original
                .getVOffset(Float.intBitsToFloat(vertices[uv + 1]));
            vertices[uv] = Float.floatToRawIntBits(texture.getU(u));
            vertices[uv + 1] = Float.floatToRawIntBits(texture.getV(v));
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(),
            texture, quad.isShade(), quad.hasAmbientOcclusion());
    }
}
