package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import java.math.BigInteger;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Set;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypeRegistry;
import zone.moddev.mc.lib.gui.value.ValueTypes;

class ValueTypesTest {
    public static final class Reading {
        int amount;

        Reading(int amount) {
            this.amount = amount;
        }
    }

    public static final class Getters {
        static final Reading LIVE = new Reading(3);

        public static Reading reading(BlockEntity ignored) {
            return LIVE;
        }

        public static Reading[] unsupported(BlockEntity ignored) {
            return new Reading[0];
        }
    }

    private static ValueType<Reading> reading(int version) {
        return ValueType
            .<Reading>builder(ResourceLocation.parse("example:reading"),
                Reading.class,
                () -> StreamCodec.of((b, v) -> b.writeInt(v.amount),
                    b -> new Reading(b.readInt())),
                4)
            .version(version).description("a machine reading")
            .validate(v -> v.amount >= 0)
            .equality((a, b) -> a.amount == b.amount).build();
    }

    @Test
    void mutableModValuesDetachCompareAndRoundTrip() {
        var registry = ValueTypeRegistry.builtins();
        var type = registry.register(reading(1));
        var live = new Reading(7);
        var before = (Reading) registry.snapshot(live, RegistryAccess.EMPTY);
        live.amount = 9;
        assertEquals(7, before.amount);
        assertTrue(registry.equal(before, new Reading(7)));
        assertFalse(registry.equal(before, live));
        assertFalse(registry.equal(before, null));
        assertTrue(registry.equal(null, null));
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(),
            RegistryAccess.EMPTY);
        try {
            registry.write(wire, before);
            registry.write(wire, null);
            assertTrue(registry.equal(before, registry.read(wire)));
            assertNull(registry.read(wire));
            assertFalse(wire.isReadable());
        } finally {
            wire.release();
        }
        assertThrows(IllegalArgumentException.class,
            () -> type.snapshot(new Reading(-1), RegistryAccess.EMPTY));
    }

    @Test
    void customGetterBindsCopiesAndRejectsWrongContractsWithSource()
        throws Throwable {
        var types = ValueTypeRegistry.builtins();
        var type = types.register(reading(1));
        var widgets = new WidgetTypes();
        widgets.register(ResourceLocation.parse("example:reader"),
            new WidgetType(Set.of(type), e -> {
            }));
        var xml = """
            <screen xmlns="https://moddev.zone/mmdlib/screen" title="test"
              block-entity="zone.moddev.mc.lib.gui.ScreenLoaderTest$Press">
              <custom type="example:reader" width="20" height="10" value="%s#reading"/>
            </screen>
            """
            .formatted(Getters.class.getName());
        var screen = ScreenReader.read(
            ResourceLocation.parse("example:reading"), "reading.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
            widgets);
        var bound = ScreenBinder.bind(screen, getClass().getClassLoader(),
            widgets, types);
        var getter = bound.values().get(screen.body().getFirst());
        assertSame(type, getter.kind());
        var snapshot = (Reading) getter.snapshot(null, RegistryAccess.EMPTY);
        assertNotSame(Getters.LIVE, snapshot);
        assertEquals(Getters.LIVE.amount, snapshot.amount);
        var wrong = new WidgetTypes();
        wrong.register(ResourceLocation.parse("example:reader"),
            new WidgetType(Set.of(ValueTypes.WHOLE), e -> {
            }));
        var error = assertThrows(ScreenException.class, () -> ScreenBinder
            .bind(screen, getClass().getClassLoader(), wrong, types));
        assertTrue(error.getMessage().contains("reading.xml:3:"),
            error.getMessage());
        assertTrue(error.getMessage().contains("whole number"));
        assertThrows(ScreenException.class,
            () -> ScreenBinder.bind(screen, getClass().getClassLoader(),
                widgets, ValueTypeRegistry.builtins()));
    }

    interface Broad {
    }

    interface Specific extends Broad {
    }

    interface Other {
    }

    static final class Ambiguous implements Specific, Other {
    }

    static final class Narrow implements Specific {
    }

    private static ValueType<Object> marker(String id, Class<?> type) {
        return ValueType.builder(ResourceLocation.parse("example:" + id), type,
            () -> StreamCodec.of((b, v) -> {
            }, b -> new Object()), 1).build();
    }

    @Test
    void javaResolutionIsSpecificDeterministicAndInvalidatedByRegistration() {
        var registry = new ValueTypeRegistry();
        assertTrue(registry.binding(Narrow.class).isEmpty());
        var broad = registry.register(marker("broad", Broad.class));
        assertSame(broad, registry.binding(Narrow.class).orElseThrow());
        var specific = registry.register(marker("specific", Specific.class));
        assertSame(specific, registry.binding(Narrow.class).orElseThrow());
        registry.register(marker("other", Other.class));
        assertThrows(IllegalArgumentException.class,
            () -> registry.binding(Ambiguous.class));
        var exact = registry.register(marker("exact", Ambiguous.class));
        assertSame(exact, registry.binding(Ambiguous.class).orElseThrow());
    }

    @Test
    void registryRejectsCollisionsLateRegistrationAndWireOnlyBinding() {
        var registry = ValueTypeRegistry.builtins();
        registry.register(reading(1));
        assertThrows(IllegalArgumentException.class,
            () -> registry.register(reading(2)));
        assertThrows(IllegalArgumentException.class,
            () -> registry.register(marker("collision", Reading.class)));
        assertThrows(IllegalArgumentException.class, () -> registry
            .require(ResourceLocation.parse("example:collision")));
        assertTrue(registry
            .binding(zone.moddev.mc.lib.gui.sync.ElementStateValue.class)
            .isEmpty());
        assertFalse(registry.bindingTypes().contains(ValueTypes.STATE));
        registry.freeze();
        assertThrows(IllegalStateException.class,
            () -> registry.register(marker("late", Object.class)));
    }

    @Test
    void fingerprintIgnoresRegistrationOrderButIncludesSchema() {
        var first = new ValueTypeRegistry();
        first.register(ValueTypes.WHOLE);
        first.register(reading(1));
        var second = new ValueTypeRegistry();
        second.register(reading(1));
        second.register(ValueTypes.WHOLE);
        assertEquals(first.fingerprint(), second.fingerprint());
        var changed = new ValueTypeRegistry();
        changed.register(ValueTypes.WHOLE);
        changed.register(reading(2));
        assertNotEquals(first.fingerprint(), changed.fingerprint());
    }

    @Test
    void malformedAndOversizedValuesAreRejected() {
        var registry = new ValueTypeRegistry();
        var type = registry.register(reading(1));
        assertThrows(IllegalArgumentException.class,
            () -> type.decode(RegistryAccess.EMPTY, new byte[5]));
        assertThrows(RuntimeException.class,
            () -> type.decode(RegistryAccess.EMPTY, new byte[3]));
        assertThrows(IllegalArgumentException.class, () -> type
            .decode(RegistryAccess.EMPTY, new byte[]{-1, -1, -1, -1}));
        var trailing = ValueType.<Reading>builder(
            ResourceLocation.parse("example:trailing"), Reading.class,
            () -> StreamCodec.of((b, v) -> b.writeInt(v.amount),
                b -> new Reading(b.readInt())),
            8).build();
        assertThrows(IllegalArgumentException.class,
            () -> trailing.decode(RegistryAccess.EMPTY, new byte[5]));
        var oversized = ValueType.<Reading>builder(
            ResourceLocation.parse("example:oversized"), Reading.class,
            () -> StreamCodec.of((b, v) -> b.writeLong(v.amount),
                b -> new Reading(b.readInt())),
            4).build();
        assertThrows(RuntimeException.class,
            () -> oversized.encode(RegistryAccess.EMPTY, new Reading(1)));
        for (int failure = 0; failure < 3; failure++) {
            var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.EMPTY);
            try {
                wire.writeBoolean(true);
                wire.writeResourceLocation(failure == 0
                    ? ResourceLocation.parse("example:unknown")
                    : type.id());
                wire.writeVarInt(failure == 1 ? 2 : 1);
                wire.writeByteArray(new byte[5]);
                assertThrows(RuntimeException.class, () -> registry.read(wire));
            } finally {
                wire.release();
            }
        }
    }

    @Test
    void builtinsNormalizeAliasesAndPreserveNumericPrecision() {
        var types = ValueTypeRegistry.builtins();
        for (var cls : List.of(byte.class, short.class, int.class, long.class,
            Integer.class, BigInteger.class))
            assertSame(ValueTypes.WHOLE, types.binding(cls).orElseThrow());
        assertEquals(BigInteger.valueOf(42),
            types.snapshot(42L, RegistryAccess.EMPTY));
        assertTrue(types.binding(java.math.BigDecimal.class).isEmpty());
        assertTrue(
            types.binding(java.util.concurrent.atomic.AtomicInteger.class)
                .isEmpty());
        assertSame(ValueTypes.NUMBER,
            types.binding(Number.class).orElseThrow());
        var huge = BigInteger.TEN.pow(2000);
        assertEquals(huge,
            ValueTypes.NUMBER.snapshot(huge, RegistryAccess.EMPTY));
        assertEquals(1.25D,
            ValueTypes.NUMBER.snapshot(1.25F, RegistryAccess.EMPTY));
        assertThrows(IllegalArgumentException.class,
            () -> types.snapshot(Double.NaN, RegistryAccess.EMPTY));
        assertThrows(IllegalArgumentException.class,
            () -> ValueTypes.DECIMAL.decode(RegistryAccess.EMPTY,
                new byte[]{0x7f, (byte) 0xf0, 0, 0, 0, 0, 0, 0}));
    }
}
