package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.runtime.ScreenNetwork;

class WidgetActionTest {
    private static final WidgetAction<String> TEXT = new WidgetAction<>(
        ResourceLocation.parse("test:text"),
        StreamCodec.of((b, s) -> b.writeUtf(s, 32), b -> b.readUtf(32)), 132,
        (c, s) -> true);

    @Test
    void typedPayloadRoundTripsAndRejectsMalformedOrTrailingData() {
        var data = TEXT.encode(RegistryAccess.EMPTY, "Copper crusher");
        assertEquals("Copper crusher", TEXT.decode(RegistryAccess.EMPTY, data));
        assertThrows(RuntimeException.class,
            () -> TEXT.decode(RegistryAccess.EMPTY, new byte[]{32, 65}));
        assertThrows(RuntimeException.class,
            () -> TEXT.decode(RegistryAccess.EMPTY,
                Arrays.copyOf(data, data.length + 1)));
        assertThrows(RuntimeException.class,
            () -> TEXT.decode(RegistryAccess.EMPTY, new byte[133]));
        assertThrows(RuntimeException.class,
            () -> TEXT.encode(RegistryAccess.EMPTY, "x".repeat(33)));
        var bounded = new WidgetAction<>(ResourceLocation.parse("test:bounded"),
            TEXT.codec(), 2, (c, s) -> true);
        assertThrows(RuntimeException.class,
            () -> bounded.encode(RegistryAccess.EMPTY, "abc"));
    }

    @Test
    void envelopeIsBoundedBeforeTheModCodecAndCopiesBytes() {
        var data = TEXT.encode(RegistryAccess.EMPTY, "Name");
        var packet = new ScreenNetwork.Widget(4, 12345L, 3, 1, TEXT.id(), data);
        data[0] = 100;
        assertEquals("Name", TEXT.decode(RegistryAccess.EMPTY, packet.data()));
        var copy = packet.data();
        copy[0] = 100;
        assertEquals("Name", TEXT.decode(RegistryAccess.EMPTY, packet.data()));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
            RegistryAccess.EMPTY);
        try {
            ScreenNetwork.Widget.CODEC.encode(buffer, packet);
            var decoded = ScreenNetwork.Widget.CODEC.decode(buffer);
            assertEquals(packet.menu(), decoded.menu());
            assertEquals(packet.session(), decoded.session());
            assertEquals(packet.element(), decoded.element());
            assertEquals(packet.request(), decoded.request());
            assertArrayEquals(packet.data(), decoded.data());
            assertFalse(buffer.isReadable());
            buffer.clear();
            buffer.writeVarInt(4);
            buffer.writeLong(12345L);
            buffer.writeVarInt(3);
            buffer.writeVarInt(1);
            buffer.writeResourceLocation(TEXT.id());
            buffer.writeVarInt(4097);
            assertThrows(RuntimeException.class,
                () -> ScreenNetwork.Widget.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void typesRejectDuplicateActionsAndResultsCarryAuthoritativeValues() {
        assertThrows(IllegalArgumentException.class,
            () -> new WidgetType(Set.of(), w -> {
            }, List.of(TEXT, TEXT)));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
            RegistryAccess.EMPTY);
        try {
            var reply = new ScreenNetwork.WidgetResult(4, 42L, 3, 10, false,
                java.math.BigInteger.TEN, null);
            ScreenNetwork.WidgetResult.CODEC.encode(buffer, reply);
            assertEquals(reply,
                ScreenNetwork.WidgetResult.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
