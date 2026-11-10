package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.runtime.network.NetworkRegistry;

class NetworkRegistryTest {
    private record Message(int value) implements CustomPacketPayload {
        static final Type<Message> TYPE = new Type<>(id("message"));
        static final StreamCodec<RegistryFriendlyByteBuf, Message> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value.value()),
            buffer -> new Message(buffer.readVarInt()));

        public Type<Message> type() {
            return TYPE;
        }
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.parse("test:" + name);
    }

    private static void declare(NetworkRegistry.Registrar registrar) {
        registrar.playToServer(Message.TYPE, Message.CODEC, (message, context) -> {
        });
    }

    @Test
    void modContributionsKeepTheirDirectionVersionCodecAndHandler() {
        var registry = new NetworkRegistry();
        var received = new AtomicReference<Message>();
        registry.register(
            id("feature"),
            "custom-2",
            registrar -> registrar.playToClient(
                Message.TYPE, Message.CODEC, (message, context) -> received.set(message)));
        var contributor = registry.freeze().getFirst();
        assertEquals("custom-2", contributor.version());
        var declaration = contributor.payloads().getFirst();
        assertEquals(NetworkRegistry.Direction.TO_CLIENT, declaration.direction());
        assertSame(Message.TYPE, declaration.type());
        roundTrip(declaration, received);
        assertEquals(new Message(42), received.get());
    }

    private static <T extends CustomPacketPayload> void roundTrip(
        NetworkRegistry.Payload<T> declaration, AtomicReference<Message> received) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            Message.CODEC.encode(buffer, new Message(42));
            T decoded = declaration.codec().decode(buffer);
            assertFalse(buffer.isReadable());
            declaration.handler().handle(decoded, null);
            declaration.codec().encode(buffer, decoded);
            assertEquals(received.get(), Message.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void contributorOrderIsStableAndStartupFreezesImmutableDeclarations() {
        var registry = new NetworkRegistry();
        var retained = new AtomicReference<NetworkRegistry.Registrar>();
        registry.register(
            id("z"),
            "1",
            registrar -> {
                retained.set(registrar);
                declare(registrar);
            });
        registry.register(id("a"), "1", registrar -> {
        });
        assertThrows(IllegalStateException.class, () -> declare(retained.get()));
        var frozen = registry.freeze();
        assertEquals(
            List.of(id("a"), id("z")),
            frozen.stream().map(NetworkRegistry.Contributor::id).toList());
        assertSame(frozen, registry.freeze());
        assertThrows(UnsupportedOperationException.class, () -> frozen.clear());
        assertThrows(UnsupportedOperationException.class,
            () -> frozen.getLast().payloads().clear());
        assertThrows(IllegalStateException.class, () -> registry.register(id("late"), "1", r -> {
        }));
    }

    @Test
    void duplicateContributorsAndPayloadIdsAreRejectedWithoutPartialRegistration() {
        var registry = new NetworkRegistry();
        registry.register(id("one"), "1", NetworkRegistryTest::declare);
        assertThrows(IllegalArgumentException.class, () -> registry.register(id("one"), "1", r -> {
        }));
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(
                id("two"), "1", r -> r.playToClient(Message.TYPE, Message.CODEC, (p, c) -> {
                })));
        registry.register(id("two"), "1", r -> {
        });
        var separate = new NetworkRegistry();
        assertThrows(
            IllegalArgumentException.class,
            () -> separate.register(
                id("duplicate"),
                "1",
                r -> {
                    declare(r);
                    declare(r);
                }));
        separate.register(id("duplicate"), "1", NetworkRegistryTest::declare);
        assertEquals(1, separate.freeze().size());
    }

    @Test
    void failedDeclarationDoesNotReserveItsContributorOrPayloadIds() {
        var registry = new NetworkRegistry();
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(id("feature"), " ", NetworkRegistryTest::declare));
        var retained = new AtomicReference<NetworkRegistry.Registrar>();
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(
                id("feature"),
                "1",
                r -> {
                    retained.set(r);
                    declare(r);
                    throw new IllegalArgumentException("failed");
                }));
        assertThrows(IllegalStateException.class, () -> declare(retained.get()));
        registry.register(id("feature"), "1", NetworkRegistryTest::declare);
        assertEquals(1, registry.freeze().size());
    }
}
