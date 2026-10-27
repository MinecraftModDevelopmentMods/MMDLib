package zone.moddev.mc.lib.common.builders;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.api.MaterialEventContexts.ToolTarget;
import zone.moddev.mc.lib.common.codecs.EventExtension;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import java.io.StringReader;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class MethodEventsTest {
    private static int initializations;
    private static int playerCalls, weaponCalls, toolCalls, projectileCalls;
    private static final List<String> received = new ArrayList<>();
    public record Pair(String text, int count) {}
    public static final class Handlers {
        static { initializations++; }
        public static void text(String value) { received.add(value); }
        public static void pair(String value, int count) { received.add(value.repeat(count)); }
        public static void player(Player player) { playerCalls++; }
        public static void weapon(Player player, LivingEntity target) { weaponCalls++; }
        public static void tool(Player player, ToolTarget target) { toolCalls++; }
        public static void projectileDamage(net.minecraft.world.entity.projectile.Projectile projectile, LivingEntity target,
                net.minecraft.world.item.ItemStack ammo, net.neoforged.neoforge.common.damagesource.DamageContainer damage) { projectileCalls++; }
        public static void projectileHit(net.minecraft.world.entity.projectile.Projectile projectile, LivingEntity target,
                net.minecraft.world.item.ItemStack ammo) { projectileCalls++; }
        public static void badTool(Player player, LivingEntity target) {}
        public static void checked(String value) throws java.io.IOException { throw new java.io.IOException("handler failure"); }
        public static void runtime(String value) { throw new UnsupportedOperationException("runtime failure"); }
        public void instance(String value) {}
        private static void hidden(String value) {}
        public static int nonvoid(String value) { return 0; }
        public static void wrong(Integer value) {}
    }
    static final class HiddenClass { public static void text(String value) {} }
    public static void main(String[] args) throws Throwable {
        var api = new MaterialExtensions(false); var events = api.events();
        var message = events.registerEvent("test:message", String.class);
        var paired = events.registerEvent("test:paired", Pair.class,
                MethodType.methodType(void.class, String.class, int.class),
                (handle, context) -> { handle.invokeExact(context.text(), context.count()); });
        String owner = Handlers.class.getName();
        AtomicInteger lookups = new AtomicInteger();
        var loader = new ClassLoader(MethodEventsTest.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(owner)) lookups.incrementAndGet();
                return super.loadClass(name, resolve);
            }
        };
        var extension = extension(List.of(binding("test:message", owner + "#text", "test:append"),
                binding("test:message", owner + "#text", "test:append_second"),
                binding("test:paired", owner + "#pair", "test:repeat")));
        var json = EventExtension.CODEC.encodeStart(JsonOps.INSTANCE, extension).getOrThrow();
        require(EventExtension.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(extension));
        var nbt = EventExtension.CODEC.encodeStart(NbtOps.INSTANCE, extension).getOrThrow();
        require(EventExtension.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().equals(extension));
        var loaded = events.loadExtension(extension, loader, "fixture.json");
        require(initializations == 0 && received.isEmpty());
        require(loaded.size() == 3 && loaded.getFirst().definition().label().equals("Test handler"));
        require(events.resolvedHandler(id("test:append")).methodHandle().type().equals(MethodType.methodType(void.class, String.class)));
        int before = lookups.get();
        events.dispatch(id("test:append"), message, "one");
        events.dispatch(id("test:append_second"), message, "two");
        events.dispatch(id("test:repeat"), paired, new Pair("x", 3));
        require(initializations == 1 && received.equals(List.of("one", "two", "xxx")) && lookups.get() == before);
        var material = new MaterialFactory(api, mod -> false).decode(id("test:metal"), new StringReader("""
                {"has_fluid":false,"event":{"test:message":"test:append"}}
                """));
        events.dispatch(material, message, "material");
        require(received.getLast().equals("material"));
        expectFailure(() -> events.dispatch(id("test:append"), events.registerEvent("test:other", String.class), "wrong"));
        expectFailure(() -> events.dispatch(id("test:missing"), message, "missing"));
        expectFailure(() -> events.loadExtension(extension, loader));
        require(events.callbacks().size() == 3);
        // Failures are detected at load, before any invocation, with no partial file installation.
        for (var reference : List.of(owner + "#missing", owner + "#instance", owner + "#hidden", owner + "#nonvoid",
                owner + "#wrong", "missing.NoSuchClass#text", HiddenClass.class.getName() + "#text", "bad-reference")) {
            var broken = extension(List.of(binding("test:message", owner + "#text", "test:pending"),
                    binding("test:message", reference, "test:broken")));
            try { events.loadExtension(broken, loader, "broken.json"); throw new AssertionError("Bad handler accepted"); }
            catch (IllegalArgumentException ex) { require(ex.getMessage().contains("broken.json")); }
            require(!events.callbacks().containsKey(id("test:pending")) && !events.callbacks().containsKey(id("test:broken")));
        }
        expectFailure(() -> events.loadExtension(extension(List.of(binding("unknown", owner + "#text", "test:unknown"))), loader));
        expectFailure(() -> events.loadExtension(extension(List.of(binding("test:message", owner + "#text", "test:same"),
                binding("test:message", owner + "#text", "test:same"))), loader));
        events.registerCallback(id("test:code"), message, value -> {});
        expectFailure(() -> events.loadExtension(extension(List.of(binding("test:message", owner + "#text", "test:code"))), loader));
        var denied = new ClassLoader(loader) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(owner)) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        expectFailure(() -> events.loadExtension(extension(List.of(binding("test:message", owner + "#text", "test:denied"))), denied));
        // Built-in game signatures resolve without constructing a Minecraft player or world.
        events.loadExtension(extension(List.of(binding("player-tick", owner + "#player", "test:player"),
                binding("weapon-hit", owner + "#weapon", "test:weapon"),
                binding("tool-left-click", owner + "#tool", "test:left"),
                binding("tool-right-click", owner + "#tool", "test:right"))), loader);
        events.resolvedHandler(id("test:player")).methodHandle().invokeExact((Player) null);
        events.resolvedHandler(id("test:weapon")).methodHandle().invokeExact((Player) null, (LivingEntity) null);
        events.resolvedHandler(id("test:left")).methodHandle().invokeExact((Player) null, (ToolTarget) null);
        events.resolvedHandler(id("test:right")).methodHandle().invokeExact((Player) null, (ToolTarget) null);
        require(playerCalls == 1 && weaponCalls == 1 && toolCalls == 2);
        events.loadExtension(extension(List.of(binding("projectile-damage", owner + "#projectileDamage", "test:projectile_damage"),
                binding("projectile-hit", owner + "#projectileHit", "test:projectile_hit"))), loader);
        events.resolvedHandler(id("test:projectile_damage")).methodHandle().invokeExact((net.minecraft.world.entity.projectile.Projectile) null,
                (LivingEntity) null, (net.minecraft.world.item.ItemStack) null, (net.neoforged.neoforge.common.damagesource.DamageContainer) null);
        events.resolvedHandler(id("test:projectile_hit")).methodHandle().invokeExact((net.minecraft.world.entity.projectile.Projectile) null,
                (LivingEntity) null, (net.minecraft.world.item.ItemStack) null);
        require(projectileCalls == 2);
        // Projectile handlers must take the projectile contract's parameters, not the tool contract's.
        expectFailure(() -> events.loadExtension(extension(List.of(binding("projectile-hit", owner + "#tool", "test:badprojectile"))), loader));
        expectFailure(() -> events.loadExtension(extension(List.of(binding("tool-right-click", owner + "#badTool", "test:badtool"))), loader));
        events.loadExtension(extension(List.of(binding("test:message", owner + "#checked", "test:checked"),
                binding("test:message", owner + "#runtime", "test:runtime"))), loader);
        try { events.dispatch(id("test:checked"), message, ""); throw new AssertionError(); }
        catch (IllegalStateException ex) { require(ex.getCause() instanceof java.io.IOException && ex.getMessage().contains("test:checked")); }
        try { events.dispatch(id("test:runtime"), message, ""); throw new AssertionError(); }
        catch (UnsupportedOperationException ex) { require(ex.getMessage().equals("runtime failure")); }
        for (String bad : List.of("{}", "{\"events\":[{\"type\":\"x\",\"handler\":\"bad\",\"id\":\"test:x\"}]}",
                "{\"events\":[{\"type\":\"x\",\"handler\":\"A#x\"}]}"))
            require(EventExtension.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(bad)).error().isPresent());
        var duplicateJson = EventExtension.CODEC.encodeStart(JsonOps.INSTANCE, extension(List.of(
                binding("test:message", owner + "#text", "test:same"), binding("test:message", owner + "#text", "test:same"))));
        require(duplicateJson.error().isPresent());
        var file = Files.createTempFile("mmdlib-event-extension", ".json");
        try {
            Files.writeString(file, EventExtension.CODEC.encodeStart(JsonOps.INSTANCE,
                    extension(List.of(binding("test:message", owner + "#text", "test:file")))).getOrThrow().toString());
            require(events.loadExtension(file, loader).getFirst().definition().id().equals(id("test:file")));
            Files.writeString(file, "{\"events\":17}");
            try { events.loadExtension(file, loader); throw new AssertionError(); }
            catch (IllegalArgumentException ex) { require(ex.getMessage().contains(file.toString())); }
        } finally { Files.delete(file); }
        System.out.println("Load-time MethodHandles, typed signatures, ID/material dispatch, atomic failures, class loaders, and JSON/NBT checks passed");
    }
    private static EventExtension.Handler binding(String type, String reference, String id) {
        return new EventExtension.Handler(type, reference, id(id), "Test handler");
    }
    private static EventExtension extension(List<EventExtension.Handler> handlers) { return new EventExtension(handlers); }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void require(boolean condition) { if (!condition) throw new AssertionError("Method event assertion failed"); }
    private static void expectFailure(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException ex) { return; }
        throw new AssertionError("Invalid event handler accepted");
    }
}
