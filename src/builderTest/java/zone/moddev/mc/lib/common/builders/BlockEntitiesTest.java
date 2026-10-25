package zone.moddev.mc.lib.common.builders;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredRegister;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.codecs.MMDMaterialCodec;
import zone.moddev.mc.lib.common.factories.*;
import zone.moddev.mc.lib.common.modules.*;
import zone.moddev.mc.lib.common.types.*;
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class BlockEntitiesTest {
    static final class Machine extends BlockEntity {
        Machine(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    }
    static final class Other extends BlockEntity {
        Other(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    }
    public static void main(String[] args) {
        var api = new MaterialExtensions(false);
        var module = DataModules.Block.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"factory":"example:machine_block","template":{"has_item":false},
                 "part":{"aliases":["crusher"],
                   "block_entity":{"factory":"example:machine","factory_data":{"capacity":32}},
                   "entity_factory":"example:emission","entity_data":{"lifetime":5}}}
                """ )).getOrThrow();
        new ModuleCatalog(Map.of(), Map.of(id("example:mill"), module), Map.of()).install(api);
        var parts = api.snapshot().parts();
        var mill = parts.resolve("example:crusher").getOrThrow();
        require(mill.needsEntity() && mill.needsBlockEntity());
        require(parts.entities().contains(mill));
        var factory = new MaterialFactory(api, mod -> false);
        var result = factory.load(id("test:copper"), new StringReader("{\"has_fluid\":false}"));
        var plan = result.content();
        require(plan.items().isEmpty() && plan.blocks().size() == 1 && plan.entities().size() == 1 && plan.blockEntities().size() == 1);
        var definition = plan.blockEntities().get(mill);
        require(plan.blockEntityDefinitions().size() == 1);
        expectFailure(() -> new MaterialContentPlan(plan.material(), Map.of(), Map.of(), Map.of(), null, Map.of(), Map.of(), plan.blockEntities()));
        require(definition.block().equals(plan.blocks().get(mill).name()));
        require(definition.material().equals(id("test:copper")));
        require(definition.factory().equals(id("example:machine")));
        require(plan.entities().get(mill).factory().equals(id("example:emission")));
        require(plan.entities().get(mill).factoryData().decode(Codec.INT.fieldOf("lifetime").codec()).getOrThrow() == 5);
        require(definition.factoryData().decode(Codec.INT.fieldOf("capacity").codec()).getOrThrow() == 32);
        var codec = MMDMaterialCodec.forExtensions(api);
        var nbt = codec.encodeStart(NbtOps.INSTANCE, result.material()).getOrThrow();
        require(codec.parse(NbtOps.INSTANCE, nbt).getOrThrow().equals(result.material()));
        var moduleNbt = DataModules.Block.CODEC.encodeStart(NbtOps.INSTANCE, module).getOrThrow();
        require(DataModules.Block.CODEC.parse(NbtOps.INSTANCE, moduleNbt).getOrThrow().equals(module));
        require(DataModules.Block.CODEC.parse(JsonOps.INSTANCE, DataModules.Block.CODEC.encodeStart(JsonOps.INSTANCE, module).getOrThrow()).getOrThrow().equals(module));
        var excluded = factory.load(id("test:excluded"), new StringReader("{\"blocks\":\"all !example:crusher\",\"has_fluid\":false}")).content();
        require(excluded.blockEntities().isEmpty() && excluded.entities().isEmpty() && excluded.blocks().isEmpty());
        // A block entity does not require any world entity or item counterpart.
        var onlyBE = DataModules.Block.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"factory":"example:machine_block","template":{"has_item":false},
                 "part":{"block_entity":{"factory":"example:machine","factory_data":{"capacity":64}}}}
                """ )).getOrThrow();
        api.registerBlockModule(id("example:mill"), onlyBE, true);
        var machineOnly = factory.load(id("test:tin"), new StringReader("{\"entities\":\"\",\"has_fluid\":false}")).content();
        require(machineOnly.items().isEmpty() && machineOnly.entities().isEmpty() && machineOnly.blockEntities().size() == 1);
        var gated = DataModules.Block.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"factory":"example:machine_block","requires_mod":"optional_mod","template":{},
                 "part":{"block_entity":{"factory":"example:machine"}}}
                """ )).getOrThrow();
        api.registerBlockModule(id("example:optional"), gated);
        require(factory.load(id("test:gated"), new StringReader("{\"has_fluid\":false}")).content().blockEntities().size() == 1);
        require(new MaterialFactory(api, mod -> true).load(id("test:enabled"), new StringReader("{\"has_fluid\":false}")).content().blockEntities().size() == 2);
        require(DataModules.Item.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"factory":"example:item","template":{},"part":{"block_entity":{"factory":"example:machine"}}}
                """ )).error().isPresent());
        var factories = new BlockEntityFactories();
        AtomicInteger creations = new AtomicInteger();
        BlockEntityFactory<Machine, Integer> implementation = new BlockEntityFactory<>() {
            public Class<Machine> blockEntityClass() { return Machine.class; }
            public Codec<Integer> settingsCodec() { return Codec.intRange(1, 128).fieldOf("capacity").codec(); }
            public Machine create(BlockEntityType<Machine> type, BlockPos pos, BlockState state, MMDBlockEntity def, Integer capacity) {
                creations.incrementAndGet(); throw new AssertionError("Must defer construction");
            }
        };
        factories.register(id("example:machine"), implementation);
        var prepared = factories.prepare(definition, Machine.class);
        require(prepared.definition().equals(definition) && prepared.blockEntityClass() == Machine.class);
        require(factories.prepare(definition, Machine.class, JsonOps.INSTANCE).definition().equals(definition));
        expectFailure(() -> factories.register(id("example:machine"), implementation));
        expectFailure(() -> factories.prepare(definition, Other.class));
        expectFailure(() -> new BlockEntityFactories().prepare(definition, Machine.class));
        expectFailure(() -> factories.prepare(new MMDBlockEntity(definition.name(), definition.material(), definition.block(), definition.factory(),
                new FactoryData(JsonParser.parseString("{\"capacity\":0}"))), Machine.class));
        var wrongRegistry = DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, "wrong");
        expectFailure(() -> factories.queue(wrongRegistry, prepared, () -> { throw new AssertionError("Early block lookup"); }));
        require(creations.get() == 0);
        System.out.println("Machine block entities, block-owned world entities, selection/gating, codecs and deferred typed factory validation passed");
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void require(boolean condition) { if (!condition) throw new AssertionError("Block entity assertion failed"); }
    private static void expectFailure(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException ex) { return; }
        throw new AssertionError("Invalid block entity accepted");
    }
}
