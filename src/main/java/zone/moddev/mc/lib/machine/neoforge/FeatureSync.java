package zone.moddev.mc.lib.machine.neoforge;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import zone.moddev.mc.lib.machine.feature.FeatureHost;

/**
 * Sends the state of a machine's syncing features to the players tracking it,
 * so a renderer, particles or a tooltip can ask {@code processor.status()} on
 * the client. The block entity opts in; nothing is sent without it:
 *
 * <pre>{@code
 * final FeatureHost features = new FeatureHost(this::setChanged,
 *     () -> FeatureSync.send(this)).add(processor);
 *
 * public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
 *     return FeatureSync.updateTag(features, registries);
 * }
 *
 * public Packet<ClientGamePacketListener> getUpdatePacket() {
 *     return ClientboundBlockEntityDataPacket.create(this);
 * }
 *
 * protected void loadAdditional(CompoundTag tag,
 *     HolderLookup.Provider registries) {
 *     super.loadAdditional(tag, registries);
 *     if (FeatureSync.read(features, tag, registries))
 *         return;
 *     // the usual loading
 * }
 * }</pre>
 *
 * Storages are not part of an update; a client that needs them has its own way
 * to get them.
 */
public final class FeatureSync {
    private static final String KEY = "mmdlib_features";

    private FeatureSync() {
    }

    /**
     * An update tag holding the state of the host's syncing features, for
     * {@code getUpdateTag}.
     */
    public static CompoundTag updateTag(FeatureHost host,
        HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put(KEY, host.saveForClient(
            registries.createSerializationContext(NbtOps.INSTANCE)));
        return tag;
    }

    /**
     * Takes on an update made by {@link #updateTag}. Returns true if
     * {@code tag} was an update, and the block entity should not load it as a
     * save; false for a save, which this leaves alone.
     */
    public static boolean read(FeatureHost host, CompoundTag tag,
        HolderLookup.Provider registries) {
        if (!tag.contains(KEY, Tag.TAG_COMPOUND))
            return false;
        host.loadOnClient(
            registries.createSerializationContext(NbtOps.INSTANCE),
            tag.get(KEY));
        return true;
    }

    /**
     * Sends the players tracking {@code entity} its update; does nothing
     * without a server one.
     */
    public static void send(BlockEntity entity) {
        if (!(entity.getLevel() instanceof ServerLevel level))
            return;
        var state = entity.getBlockState();
        level.sendBlockUpdated(entity.getBlockPos(), state, state,
            Block.UPDATE_CLIENTS);
    }
}
