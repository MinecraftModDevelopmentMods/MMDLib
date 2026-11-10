package zone.moddev.mc.lib.smoke;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import io.netty.buffer.Unpooled;

import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.runtime.StatsMenuComponent;
import zone.moddev.mc.lib.gui.runtime.StatsPage;
import zone.moddev.mc.lib.gui.sync.ElementStateValue;

/**
 * Component codecs require Minecraft registries; test their bounds and styles
 * in a real server.
 */
final class StatsPageSmoke {
    static void check(RegistryAccess registries) {
        var field = StatsMenuComponent.PAGES;
        var id = ResourceLocation.parse("test:hour");
        var label = Component.translatable("test.hour");
        var page = new StatsPage(id, label, List.of(
            Component.literal("Custom value").withStyle(ChatFormatting.GREEN)));
        var pages = List.of(page,
            new StatsPage(ResourceLocation.parse("test:other"),
                Component.literal("Other"), List.of()));
        if (!pages
            .equals(field.decode(registries, field.encode(registries, pages))))
            throw new AssertionError(
                "Statistics page identities, labels or styled rows did not round-trip");
        if (!field.decode(registries, field.encode(registries, List.of()))
            .isEmpty())
            throw new AssertionError("Empty pages did not round-trip");
        rejects(() -> field.decode(registries, payload(registries, b -> {
            b.writeVarInt(2);
            for (int i = 0; i < 2; i++) {
                b.writeResourceLocation(id);
                ComponentSerialization.STREAM_CODEC.encode(b, label);
                b.writeVarInt(0);
            }
        })));
        rejects(() -> field.decode(registries,
            payload(registries, b -> b.writeVarInt(-1))));
        rejects(() -> field.decode(registries, payload(registries, b -> {
            b.writeVarInt(1);
            b.writeResourceLocation(id);
            ComponentSerialization.STREAM_CODEC.encode(b, label);
            b.writeVarInt(StatsPage.MAX_ROWS + 1);
        })));
        var huge = new StatsPage(id, label,
            Collections.nCopies(3000, Component.literal("x".repeat(100))));
        rejects(() -> field.encode(registries, List.of(huge)));
        var encoded = field.encode(registries, pages).data();
        rejects(() -> field.decode(registries,
            new ElementStateValue(Arrays.copyOf(encoded, encoded.length + 1))));
    }

    private static ElementStateValue payload(RegistryAccess registries,
        Consumer<RegistryFriendlyByteBuf> write) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            write.accept(buffer);
            var data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return new ElementStateValue(data);
        } finally {
            buffer.release();
        }
    }

    private static void rejects(Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(
            "Malformed or oversized statistics pages accepted");
    }
}
