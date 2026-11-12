package zone.moddev.mc.lib.gui.runtime;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;
import zone.moddev.mc.lib.gui.runtime.network.NetworkRegistry;

/**
 * Menu/session-bound commands; a bounded snapshot includes live strengths and configuration
 * revision.
 */
public final class RedstoneNetwork {
    public static final String VERSION = "17";

    private RedstoneNetwork() {
    }

    public record Action(
        int menu, long session, int request, long revision, int kind, int face, int column,
        int value)
        implements
            CustomPacketPayload {
        public Action {
            if (request < 1
                || revision < 0
                || kind < 0
                || kind > 7
                || face < 0
                || face > 5
                || column < 0
                || column > 1
                || value < 0
                || value > 4096)
                throw new IllegalArgumentException("Invalid redstone action");
        }

        public static final Type<Action> TYPE = new Type<>(
            ResourceLocation.parse("mmdlib:redstone_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeVarInt(p.menu);
                b.writeLong(p.session);
                b.writeVarInt(p.request);
                b.writeVarLong(p.revision);
                b.writeVarInt(p.kind);
                b.writeVarInt(p.face);
                b.writeVarInt(p.column);
                b.writeVarInt(p.value);
            },
            b -> new Action(
                b.readVarInt(),
                b.readLong(),
                b.readVarInt(),
                b.readVarLong(),
                b.readVarInt(),
                b.readVarInt(),
                b.readVarInt(),
                b.readVarInt()));

        public Type<Action> type() {
            return TYPE;
        }
    }

    public record State(
        int menu,
        long session,
        int acknowledged,
        long revision,
        boolean open,
        int selected,
        boolean paused,
        int[] faces,
        int disabledFaces)
        implements
            CustomPacketPayload {
        public State {
            zone.moddev.mc.lib.api.capabilities.FaceMask.checked(disabledFaces);
            faces = faces.clone();
            if (acknowledged < 0 || revision < 0 || selected < 0 || selected > 5
                || faces.length != 48)
                throw new IllegalArgumentException("Invalid redstone state");
            for (int i = 0; i < faces.length; i++)
                if (faces[i] < 0
                    || faces[i] > (i % 8 == 0
                        ? 2
                        : (i % 8 == 1 || i % 8 == 7) ? 1 : i % 8 == 6 ? 4096 : 15))
                    throw new IllegalArgumentException("Invalid face state");
        }

        public State(int menu, long session, int acknowledged, long revision, boolean open,
            int selected, boolean paused, int[] faces) {
            this(menu, session, acknowledged, revision, open, selected, paused, faces, 0);
        }
        public static final Type<State> TYPE = new Type<>(
            ResourceLocation.parse("mmdlib:redstone_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeVarInt(p.menu);
                b.writeLong(p.session);
                b.writeVarInt(p.acknowledged);
                b.writeVarLong(p.revision);
                b.writeBoolean(p.open);
                b.writeVarInt(p.selected);
                b.writeBoolean(p.paused);
                b.writeVarIntArray(p.faces);
                b.writeByte(p.disabledFaces);
            },
            b -> new State(
                b.readVarInt(),
                b.readLong(),
                b.readVarInt(),
                b.readVarLong(),
                b.readBoolean(),
                b.readVarInt(),
                b.readBoolean(),
                b.readVarIntArray(48),
                b.readUnsignedByte()));

        public Type<State> type() {
            return TYPE;
        }
    }

    public static void register(NetworkRegistry.Registrar r) {
        r.playToServer(
            Action.TYPE,
            Action.CODEC,
            (p, c) -> {
                if (c.player().containerMenu instanceof MachineMenu m
                    && m.components().get(BuiltinMenuComponents.REDSTONE) != null)
                    m.components().get(BuiltinMenuComponents.REDSTONE).action(c.player(), p);
            });
        r.playToClient(
            State.TYPE,
            State.CODEC,
            (p, c) -> {
                if (c.player().containerMenu instanceof MachineMenu m
                    && m.components().get(BuiltinMenuComponents.REDSTONE) != null)
                    m.components().get(BuiltinMenuComponents.REDSTONE).receive(p);
            });
    }
}
