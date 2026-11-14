package zone.moddev.mc.companion.test;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.elements.EntityPortraitElement;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.ElementStates;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class ElementSyncSamplingTests {
    private record Probe(List<ElementSync<?>> synchronizedValues)
        implements SynchronizedElement {
        public Common common() {
            return Common.empty();
        }
    }

    private static DefinedMachineEntity machine(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos,
            MachineContent
                .block(ResourceLocation.parse("mmdlib_companion:test/portable"))
                .defaultBlockState(),
            3);
        return (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
    }

    private static StreamCodec<RegistryFriendlyByteBuf, String> strings(
        AtomicInteger encodes, AtomicInteger decodes) {
        return StreamCodec.of((buffer, value) -> {
            encodes.incrementAndGet();
            buffer.writeByteArray(value.getBytes(StandardCharsets.UTF_8));
        }, buffer -> {
            decodes.incrementAndGet();
            return new String(buffer.readByteArray(), StandardCharsets.UTF_8);
        });
    }

    @GameTest(template = "empty")
    public static void immutableSamplesReuseBytesAndRecoverAfterOversize(
        GameTestHelper h) {
        var machine = machine(h);
        var encodes = new AtomicInteger();
        var decodes = new AtomicInteger();
        var current = new AtomicReference<>("valid");
        var field = ElementSync
            .field(ResourceLocation.parse("test:cached"),
                strings(encodes, decodes), ElementSync.MAX_BYTES, element -> "")
            .sample(source -> current.get()).immutable().build();
        var element = new Probe(List.of(field));
        var states = new ElementStates(List.of(element),
            h.getLevel().registryAccess(), () -> {
            });
        var first = states.sample(machine, null);
        for (int i = 0; i < 20; i++) {
            h.assertTrue(states.sample(machine, null).equals(first),
                "Unchanged samples retain their bytes");
            h.assertTrue(states.get(element, field).equals("valid"),
                "Cached reads remain correct");
        }
        h.assertTrue(encodes.get() == 2 && decodes.get() == 0,
            "One initial and one sampled encoding; no previous-value or self-validation decoding");
        var remote = new ElementStates(List.of(element),
            h.getLevel().registryAccess(), () -> {
            });
        remote.apply(first);
        h.assertTrue(decodes.get() == 1,
            "Inbound payloads still receive codec validation");
        int beforeOversize = encodes.get();
        current.set("x".repeat(70000));
        for (int i = 0; i < 20; i++)
            states.sample(machine, null);
        h.assertTrue(encodes.get() == beforeOversize + 1,
            "An unchanged rejected immutable payload is attempted only once");
        h.assertTrue(states.get(element, field).isEmpty(),
            "Oversized state uses the default");
        current.set("recovered");
        states.sample(machine, null);
        h.assertTrue(states.get(element, field).equals("recovered"),
            "Later valid values recover normally");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oversizedPortraitShowsEmptyWithoutChangingOwnedSnapshot(
        GameTestHelper h) {
        var machine = machine(h);
        var data = new CompoundTag();
        data.putString("id", "minecraft:pig");
        data.putByteArray("large_inventory", new byte[70000]);
        var original = new EntitySnapshot(data);
        var value = new AtomicReference<>(original);
        var portrait = new EntityPortraitElement(Common.empty(),
            "$processors.portable.contents", 32, 32,
            java.util.Optional.empty()) {
            public EntitySnapshot read(ScreenHost host) {
                return value.get();
            }
        };
        for (int viewer = 0; viewer < 2; viewer++) {
            var states = new ElementStates(List.of(portrait),
                h.getLevel().registryAccess(), () -> {
                });
            for (int tick = 0; tick < 20; tick++)
                states.sample(machine, null);
            h.assertTrue(
                states.get(portrait, EntityPortraitElement.ENTITY).isEmpty(),
                "Each viewer receives the empty portrait fallback");
        }
        h.assertTrue(
            original.data().getByteArray("large_inventory").length == 70000,
            "Presentation fallback never trims the owned entity payload");
        data.remove("large_inventory");
        value.set(new EntitySnapshot(data));
        var recovered = new ElementStates(List.of(portrait),
            h.getLevel().registryAccess(), () -> {
            });
        recovered.sample(machine, null);
        h.assertTrue(
            !recovered.get(portrait, EntityPortraitElement.ENTITY).isEmpty(),
            "A smaller portrait can be displayed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void samplingFailuresAndCombinedBudgetCannotEscapeServerTick(
        GameTestHelper h) {
        var machine = machine(h);
        var codec = strings(new AtomicInteger(), new AtomicInteger());
        var current = new AtomicReference<>("x".repeat(60000));
        var field = ElementSync
            .field(ResourceLocation.parse("test:budget"), codec,
                ElementSync.MAX_BYTES, element -> "")
            .sample(source -> current.get()).immutable().build();
        var broken = ElementSync.field(ResourceLocation.parse("test:broken"),
            codec, 20, element -> "").sample(source -> {
                throw new IllegalArgumentException("Bad sampler");
            }).build();
        var order = new java.util.ArrayList<Element>();
        for (int i = 0; i < 5; i++)
            order.add(new Probe(List.of(field)));
        var brokenElement = new Probe(List.of(broken));
        order.add(brokenElement);
        var states = new ElementStates(order, h.getLevel().registryAccess(),
            () -> {
            });
        for (int i = 0; i < 3; i++)
            ElementStates.checkBudget(states.sample(machine, null));
        h.assertTrue(
            states.get(order.get(0), field).length() == 60000
                && states.get(order.get(4), field).isEmpty()
                && states.get(brokenElement, broken).isEmpty(),
            "Healthy fields survive while over-budget and failing fields use defaults");
        current.set("small");
        states.sample(machine, null);
        h.assertTrue(states.get(order.get(4), field).equals("small"),
            "Budget fallback recovers when the payload shrinks");
        h.succeed();
    }
}
