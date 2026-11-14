package zone.moddev.mc.lib.machine.neoforge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.stats.StatsProvider;
import zone.moddev.mc.lib.common.runtime.DropsContents;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.machine.feature.FeatureHost;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;

/**
 * Shared features, persistence and screen contract with optional power storage.
 * Concrete screen XML can extend {@code mmdlib:base_machine}; inherited getters
 * and the keyless processor button work without per-machine bindings. Slot/tank
 * definitions, recipes, capabilities and block registration remain the
 * consumer's choices.
 */
public abstract class BaseMachine extends BlockEntity
    implements ScreenHost, DropsContents, Clearable,
    zone.moddev.mc.lib.machine.multiblock.MultiblockController {
    public static final ResourceLocation SCREEN_TEMPLATE = ResourceLocation
        .parse("mmdlib:base_machine");
    private static final String CLIENT_DATA = "mmdlib_machine";

    public final FeatureHost features = new FeatureHost(this::setChanged,
        () -> FeatureSync.send(this));

    protected BaseMachine(BlockEntityType<?> type, BlockPos pos,
        BlockState state) {
        super(type, pos, state);
    }

    /**
     * Optional composed structure; existing single-block machines return null.
     */
    @Override
    public zone.moddev.mc.lib.machine.multiblock.MultiblockInstance multiblock() {
        return null;
    }

    /**
     * Also gates consumer-owned push operations and custom before/after tick
     * work.
     */
    public final boolean structureReady() {
        return multiblock() == null || multiblock().ready();
    }

    @Override
    public boolean screenInteractionAnchor(BlockPos anchor) {
        if (anchor.equals(worldPosition))
            return true;
        if (level != null && level.isClientSide && multiblock() != null) {
            var link = MultiblockMembers.INSTANCE.link(level, anchor);
            return link != null && link.controller().equals(worldPosition)
                && link.dimension().equals(level.dimension().location())
                && link.formation().equals(multiblock().formation());
        }
        return multiblock() != null && level != null && level.hasChunkAt(anchor)
            && multiblock().owns(anchor,
                MultiblockMembers.INSTANCE.link(level, anchor));
    }

    @Override
    public String screenInteractionIdentity(BlockPos anchor) {
        return multiblock() == null || anchor.equals(worldPosition)
            ? ""
            : String.valueOf(multiblock().formation());
    }

    /**
     * Optional power storage; fluid-fuel and unpowered machines return null.
     */
    protected EnergyBuffer powerStorage() {
        return null;
    }

    public Direction facing() {
        return RelativeFace.facing(getBlockState());
    }

    @Override
    public EnergyBuffer screenEnergy() {
        return powerStorage();
    }

    /**
     * Common warnings for the inherited info panel; subclasses may append their
     * own messages.
     */
    public List<Component> getWarnings() {
        var warnings = new ArrayList<Component>();
        var redstone = screenRedstone();
        if (redstone != null
            && !pausePolicy().processingEnabled(redstone.paused()))
            warnings.add(Component
                .translatable("screen.mmdlib.machine.redstone_waiting"));
        for (var processor : features.processors()) {
            var key = switch (processor.status()) {
                case STALLED -> "screen.mmdlib.machine.stalled";
                case BLOCKED -> "screen.mmdlib.machine.blocked";
                case HELD -> "screen.mmdlib.machine.held";
                default -> null;
            };
            if (key != null)
                warnings.add(Component.translatable(
                    "screen.mmdlib.machine.processor_warning",
                    processor.displayName(), Component.translatable(key)));
        }
        return List.copyOf(warnings);
    }

    public int getProgress() {
        return features.processor(java.util.Optional.empty())
            .map(RecipeProcessor::progress).orElse(0);
    }

    public int getTime() {
        return features.processor(java.util.Optional.empty())
            .map(RecipeProcessor::time).orElse(0);
    }

    public boolean getPaused() {
        return features.findProcessor(java.util.Optional.empty())
            .map(zone.moddev.mc.lib.machine.feature.Processor::paused)
            .orElse(false);
    }

    @Override
    public final FeatureHost screenFeatures() {
        return features;
    }

    @Override
    public List<? extends StatsProvider> screenStats() {
        var parts = new ArrayList<StatsProvider>();
        if (powerStorage() != null)
            parts.add(powerStorage());
        parts.addAll(features.statisticsProviders());
        return List.copyOf(parts);
    }

    /** Opens the concrete screen registered under this entity type's ID. */
    public final boolean openScreen(Player player) {
        return ScreenRuntime.open(player, this);
    }

    /**
     * Return the lifecycle configured by the subclass after its inventories are
     * constructed. The base registers an optional energy buffer under
     * {@code energy} automatically and rejects conflicting bindings. Without a
     * lifecycle, the base persists energy and features only.
     */
    protected MachineInventoryLifecycle machineInventory() {
        return null;
    }

    private MachineInventoryLifecycle configuredInventory() {
        var inventory = machineInventory();
        if (inventory != null)
            inventory.bindPower(this, features, powerStorage());
        return inventory;
    }

    /**
     * Starts a lifecycle with this machine's optional power storage and feature
     * host wired.
     */
    protected final MachineInventoryLifecycle genericInventory(
        AutomationInventory automation) {
        var inventory = new MachineInventoryLifecycle(this, features,
            automation, powerStorage());
        return powerStorage() == null
            ? inventory
            : inventory.energy("energy", powerStorage());
    }

    @Override
    public AutomationInventory screenAutomation() {
        var inventory = configuredInventory();
        return inventory == null || inventory.automation().groups().isEmpty()
            ? null
            : inventory.automation();
    }

    protected boolean processingAllowed() {
        return true;
    }

    protected MachineInventoryLifecycle.PausePolicy pausePolicy() {
        return MachineInventoryLifecycle.PausePolicy.PROCESSING_ONLY;
    }

    protected void beforeMachineTick() {
    }

    protected void afterMachineTick() {
    }

    public final void serverTick() {
        if (level == null || level.isClientSide)
            return;
        if (multiblock() != null)
            multiblock().tick();
        if (isRemoved())
            return;
        boolean structureReady = structureReady();
        var redstone = screenRedstone();
        if (redstone != null)
            redstone.readInputs();
        if (structureReady)
            beforeMachineTick();
        boolean paused = redstone != null && redstone.paused();
        var inventory = configuredInventory();
        if (inventory != null)
            inventory.tick(structureReady && processingAllowed(), paused,
                pausePolicy(), structureReady);
        else
            features.tick(level, structureReady && processingAllowed()
                && pausePolicy().processingEnabled(paused));
        if (structureReady)
            afterMachineTick();
        if (redstone != null)
            redstone.updateOutputs();
    }

    /**
     * Additional machine metadata; common inventories/features/redstone are
     * already saved.
     */
    protected void saveMachineData(CompoundTag tag,
        HolderLookup.Provider registries) {
    }

    /**
     * Additional machine metadata; never called for a feature-only client
     * update.
     */
    protected void loadMachineData(CompoundTag tag,
        HolderLookup.Provider registries) {
    }

    /**
     * Maps old numeric item/tank indices before storage and processor
     * restoration. Stable IDs bypass it.
     */
    protected java.util.function.IntUnaryOperator legacySlots(String key,
        CompoundTag tag) {
        return java.util.function.IntUnaryOperator.identity();
    }

    /**
     * Writes extra client state into a dedicated compound, separate from
     * storage and feature data.
     */
    protected void saveClientData(CompoundTag tag,
        HolderLookup.Provider registries) {
    }

    /**
     * Reads extra client state after feature synchronization, never while
     * loading a world save.
     */
    protected void loadClientData(CompoundTag tag,
        HolderLookup.Provider registries) {
    }

    /**
     * Sends current feature and custom client state; call setChanged separately
     * for persistent edits.
     */
    protected final void syncToClient() {
        FeatureSync.send(this);
    }

    @Override
    protected final void saveAdditional(CompoundTag tag,
        HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var inventory = configuredInventory();
        if (inventory != null)
            inventory.save(tag, registries);
        else {
            if (powerStorage() != null)
                tag.put("energy", powerStorage().serializeNBT(registries));
            tag.put("features", features
                .save(registries.createSerializationContext(NbtOps.INSTANCE)));
        }
        var redstone = screenRedstone();
        if (redstone != null)
            tag.put("redstone_control", redstone.save(registries));
        saveMachineData(tag, registries);
        if (multiblock() != null) {
            var metadata = tag.getCompound(CLIENT_DATA);
            metadata.put("multiblock", multiblock().save());
            tag.put(CLIENT_DATA, metadata);
        }
    }

    @Override
    protected final void loadAdditional(CompoundTag tag,
        HolderLookup.Provider registries) {
        if (FeatureSync.read(features, tag, registries)) {
            if (multiblock() != null && (level == null || level.isClientSide))
                multiblock().readClientState(tag.getCompound(CLIENT_DATA)
                    .getCompound("multiblock_view"));
            var redstone = screenRedstone();
            if (redstone != null
                && tag.contains("redstone_control", Tag.TAG_COMPOUND))
                redstone.loadClient(tag.getCompound("redstone_control"),
                    registries);
            if (tag.contains(CLIENT_DATA, Tag.TAG_COMPOUND))
                loadClientData(tag.getCompound(CLIENT_DATA), registries);
            return;
        }
        super.loadAdditional(tag, registries);
        var inventory = configuredInventory();
        if (inventory != null)
            inventory.load(tag, registries, this::legacySlots);
        else {
            features.reset();
            if (powerStorage() != null && tag.contains("energy"))
                powerStorage().deserializeNBT(registries, tag.get("energy"));
            if (tag.contains("features"))
                features.load(
                    registries.createSerializationContext(NbtOps.INSTANCE),
                    tag.get("features"));
        }
        var redstone = screenRedstone();
        if (redstone != null) {
            if (tag.contains("redstone_control"))
                redstone.load(tag.getCompound("redstone_control"), registries);
            else {
                int mode = tag.getInt("redstone_mode");
                redstone.migrate(mode >= 0 && mode <= 2 ? mode : 0);
            }
        }
        loadMachineData(tag, registries);
        if (multiblock() != null) {
            var metadata = tag.getCompound(CLIENT_DATA);
            multiblock().load(metadata.contains("multiblock")
                ? metadata.getCompound("multiblock")
                : tag.getCompound("multiblock"), registries);
        }
    }

    @Override
    public final CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = FeatureSync.updateTag(features, registries);
        var redstone = screenRedstone();
        if (redstone != null)
            tag.put("redstone_control", redstone.saveClient(registries));
        var clientData = new CompoundTag();
        saveClientData(clientData, registries);
        tag.put(CLIENT_DATA, clientData);
        if (multiblock() != null)
            clientData.put("multiblock_view", multiblock().clientState());
        return tag;
    }

    @Override
    public final Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void clearContent() {
        var inventory = configuredInventory();
        if (inventory != null)
            inventory.clear();
        else {
            features.reset();
            if (powerStorage() != null)
                powerStorage().setStored(0);
            setChanged();
        }
        FeatureSync.send(this);
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        var inventory = configuredInventory();
        if (inventory != null)
            inventory
                .dropContents(payload -> recoverResources(level, pos, payload));
    }

    /**
     * Preserve displaced resources in a consumer-owned recovery item or
     * equivalent durable sink.
     */
    protected abstract void recoverResources(Level level, BlockPos pos,
        CompoundTag payload);
}
