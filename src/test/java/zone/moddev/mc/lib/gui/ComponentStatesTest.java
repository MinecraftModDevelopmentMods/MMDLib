package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.runtime.component.ComponentSnapshot;
import zone.moddev.mc.lib.gui.runtime.component.ComponentStateView;
import zone.moddev.mc.lib.gui.runtime.component.ComponentStates;
import zone.moddev.mc.lib.gui.runtime.component.ComponentSync;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponent;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentKey;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentRegistry;
import zone.moddev.mc.lib.gui.runtime.component.MenuSnapshot;
import zone.moddev.mc.lib.gui.sync.ElementStateValue;

class ComponentStatesTest {
    private static final StreamCodec<RegistryFriendlyByteBuf, Integer> INT = StreamCodec
        .of((buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt());

    private static ComponentSync<Integer> number(String id) {
        return new ComponentSync<>(ResourceLocation.parse(id), 1, INT, 5, () -> 0,
            value -> value >= 0);
    }

    private static final class Probe implements MenuSnapshot {
        final ComponentSync<Integer> field;
        int value, applied;

        Probe(ComponentSync<Integer> field) {
            this.field = field;
        }

        public void snapshot(ComponentSnapshot values) {
            values.put(field, value);
        }

        public void applied(ComponentStateView values) {
            assertEquals(value, values.get(field));
            applied++;
        }
    }

    private static MenuComponentKey<Probe> key(String id) {
        return new MenuComponentKey<>(ResourceLocation.parse(id), Probe.class);
    }

    private static ComponentStates states(
        MenuComponentRegistry registry, Map<MenuComponentKey<?>, MenuComponent> active) {
        return new ComponentStates(registry.schema(), active, RegistryAccess.EMPTY);
    }

    @Test
    void registrationsAssignStableAddressesAndIsolateTheSameFieldName() {
        var field = number("test:value");
        var a = key("test:a");
        var b = key("test:b");
        var first = new MenuComponentRegistry();
        var second = new MenuComponentRegistry();
        first.register(b, 0, c -> null, field);
        first.register(a, 20, c -> null, field);
        second.register(a, 20, c -> null, field);
        second.register(b, 0, c -> null, field);
        assertEquals(first.schema(), second.schema());
        assertEquals(first.fingerprint(), second.fingerprint());
        var pa = new Probe(field);
        var pb = new Probe(field);
        pa.value = 3;
        pb.value = 8;
        var both = states(first, Map.of(a, pa, b, pb));
        var isolated = states(second, Map.of(b, pb));
        var before = isolated.snapshot().keySet();
        both.sample(List.of(pa, pb));
        isolated.sample(List.of(pb));
        assertEquals(3, both.get(pa, field));
        assertEquals(8, both.get(pb, field));
        assertEquals(before, isolated.snapshot().keySet());
        isolated
            .snapshot()
            .forEach((address, value) -> assertEquals(value, both.snapshot().get(address)));
        assertEquals(
            0,
            states(first, Map.of(a, new Probe(field))).snapshot().values().iterator().next()
                .data()[0]);
    }

    @Test
    void duplicateDeclarationsAndUndeclaredOrForgedFieldsAreRejected() {
        var field = number("test:value");
        var forged = number("test:value");
        var key = key("test:a");
        var registry = new MenuComponentRegistry();
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(key, 0, c -> null, field, forged));
        registry.register(key, 0, c -> null, field);
        var probe = new Probe(forged);
        var state = states(registry, Map.of(key, probe));
        assertThrows(IllegalArgumentException.class, () -> state.sample(List.of(probe)));
        assertThrows(IllegalArgumentException.class, () -> state.get(probe, forged));
        assertThrows(IllegalArgumentException.class, () -> state.get(new Probe(field), field));
    }

    @Test
    void updatesValidateTheEntireBatchBeforeChangingStateOrCallingComponents() {
        var field = number("test:value");
        var a = key("test:a");
        var b = key("test:b");
        var registry = new MenuComponentRegistry();
        registry.register(a, 0, c -> null, field);
        registry.register(b, 0, c -> null, field);
        var pa = new Probe(field);
        var pb = new Probe(field);
        var state = states(registry, Map.of(a, pa, b, pb));
        var addresses = state.snapshot().keySet().stream().sorted().toList();
        var batch = new LinkedHashMap<Integer, Object>();
        batch.put(addresses.get(0), field.encode(RegistryAccess.EMPTY, 7));
        batch.put(addresses.get(1), new ElementStateValue(new byte[]{1, 2}));
        assertThrows(IllegalArgumentException.class, () -> state.apply(batch));
        assertEquals(0, state.get(pa, field));
        assertEquals(0, state.get(pb, field));
        assertEquals(0, pa.applied + pb.applied);
        assertThrows(IllegalArgumentException.class,
            () -> state.apply(Map.of(addresses.get(0), 7)));
        assertThrows(
            IllegalArgumentException.class,
            () -> state.apply(Map.of(addresses.get(1) + 1, field.encode(RegistryAccess.EMPTY, 1))));
        pa.value = 7;
        state.apply(Map.of(addresses.get(0), field.encode(RegistryAccess.EMPTY, 7)));
        assertEquals(7, state.get(pa, field));
        assertEquals(1, pa.applied);
        state.apply(Map.of(addresses.get(0), field.encode(RegistryAccess.EMPTY, 7)));
        assertEquals(1, pa.applied, "Unchanged values do not trigger callbacks");
    }

    @Test
    void codecsEnforceBoundsValidationAndSchemaVersions() {
        var field = number("test:value");
        assertThrows(IllegalArgumentException.class, () -> field.encode(RegistryAccess.EMPTY, -1));
        assertThrows(
            IllegalArgumentException.class,
            () -> field.decode(RegistryAccess.EMPTY, new ElementStateValue(new byte[6])));
        assertThrows(
            IllegalArgumentException.class,
            () -> field.decode(RegistryAccess.EMPTY, new ElementStateValue(new byte[]{0, 0})));
        var tiny = new ComponentSync<>(field.id(), 1, INT, 1, () -> 0);
        assertThrows(IndexOutOfBoundsException.class, () -> tiny.encode(RegistryAccess.EMPTY, 128));
        var key = key("test:a");
        var registry = new MenuComponentRegistry();
        registry.register(key, 0, c -> null, field);
        var fingerprint = registry.fingerprint();
        registry.replace(key, 0, c -> null, new ComponentSync<>(field.id(), 2, INT, 5, () -> 0));
        assertNotEquals(fingerprint, registry.fingerprint());
        fingerprint = registry.fingerprint();
        registry.replace(key, 0, c -> null, new ComponentSync<>(field.id(), 2, INT, 4, () -> 0));
        assertNotEquals(fingerprint, registry.fingerprint());
    }

    @Test
    void mutableDecodedValuesCannotModifyStoredState() {
        var field = new ComponentSync<List<Integer>>(
            ResourceLocation.parse("test:list"),
            1,
            StreamCodec.of(
                (buffer, value) -> buffer.writeVarInt(value.getFirst()),
                buffer -> new ArrayList<>(List.of(buffer.readVarInt()))),
            5,
            () -> new ArrayList<>(List.of(5)));
        var key = key("test:a");
        var registry = new MenuComponentRegistry();
        registry.register(key, 0, c -> null, field);
        var probe = new Probe(number("test:unused"));
        var state = states(registry, Map.of(key, probe));
        state.get(probe, field).set(0, 99);
        assertEquals(List.of(5), state.get(probe, field));
    }
}
