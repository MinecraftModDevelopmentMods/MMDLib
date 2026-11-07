package zone.moddev.mc.companion.test;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.companion.ProcessorEntity;
import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.machine.feature.*;
import zone.moddev.mc.lib.machine.neoforge.*;

@GameTestHolder(CompanionMod.ID)
@PrefixGameTestTemplate(false)
public final class BasePoweredMachineTests {
  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  @GameTest(template = "empty")
  public static void ownedPowerSurvivesEvenWhenCustomLifecycleOmitsIt(GameTestHelper helper) {
    var registries = helper.getLevel().registryAccess();
    for (int mode = 0; mode < 3; mode++) {
      var original = new Probe(helper, mode);
      original.energy.setStored(1234);
      original.featureValue = 7;
      if (mode != 0) original.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
      var saved = original.saveWithoutMetadata(registries);
      var restored = new Probe(helper, mode);
      restored.loadWithComponents(saved, registries);
      require(
          restored.getEnergy() == 1234 && restored.getEnergyCapacity() == 3210,
          "Base owns power persistence with no lifecycle, a raw lifecycle, or its factory");
      require(
          restored.powerAtRestore == 1234 && restored.featureValue == 7,
          "Owned power loads before feature restoration");
      if (mode != 0)
        require(restored.items.getStackInSlot(0).getCount() == 3, "Custom storage also survives");
      restored.clearContent();
      require(
          restored.getEnergy() == 0
              && restored.featureValue == 0
              && restored.items.getStackInSlot(0).isEmpty(),
          "Clear includes power even when the subclass omitted it");
    }
    var conflict = new Probe(helper, 1);
    conflict.lifecycle.energy("energy", new EnergyBuffer(100, 10));
    try {
      conflict.saveWithoutMetadata(registries);
      throw new AssertionError("A conflicting energy binding must fail");
    } catch (IllegalArgumentException expected) {
      require(expected.getMessage().contains("energy"), "Binding error identifies energy");
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void customClientUpdatesAreIsolatedFromPersistence(GameTestHelper helper) {
    var registries = helper.getLevel().registryAccess();
    var server = new Probe(helper, 1);
    server.featureValue = 9;
    server.visual = 42;
    var client = new Probe(helper, 1);
    client.energy.setStored(2345);
    client.items.setStackInSlot(0, new ItemStack(Items.EMERALD, 4));
    client.metadata = 61;
    var packet = (ClientboundBlockEntityDataPacket) server.getUpdatePacket();
    client.loadWithComponents(packet.getTag(), registries);
    require(
        client.featureValue == 9 && client.visual == 42 && client.clientReads == 1,
        "Normal update packets carry features and custom renderer state together");
    require(
        client.getEnergy() == 2345
            && client.items.getStackInSlot(0).getCount() == 4
            && client.metadata == 61
            && client.worldReads == 0,
        "Client hook data cannot overwrite common storage or run world-load hooks");
    client.loadWithComponents(FeatureSync.updateTag(server.features, registries), registries);
    require(
        client.visual == 42 && client.clientReads == 1,
        "Older feature-only updates do not reset custom client state");
    server.visual = 0;
    client.loadWithComponents(server.getUpdateTag(registries), registries);
    require(
        client.visual == 0 && client.clientReads == 2,
        "Custom state can be cleared by a later update");
    var saved = client.saveWithoutMetadata(registries);
    require(!saved.contains("mmdlib_machine"), "Client-only payload is not written to world saves");
    var restored = new Probe(helper, 1);
    restored.visual = 88;
    restored.loadWithComponents(saved, registries);
    require(
        restored.metadata == 61
            && restored.visual == 88
            && restored.clientReads == 0
            && restored.worldReads == 1,
        "World saves use their own metadata hook without invoking the client hook");
    server.sendVisualUpdate();
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void baseWarningsDescribeProcessorsAndRespectPausePolicy(GameTestHelper helper) {
    var probe = new Probe(helper, 0);
    var resources =
        new ProcessorEntity(
            CompanionMod.DUAL_CRUSHER.type.get(),
            probe.getBlockPos(),
            CompanionMod.DUAL_CRUSHER.block.get().defaultBlockState(),
            ProcessorEntity.Kind.DUAL);
    for (var lane : resources.lanes) probe.features.add(lane);
    require(
        probe.getWarnings().isEmpty(),
        "Idle processors and unrelated features produce no warnings");
    var first = resources.lanes.getFirst();
    for (var status : RecipeProcessor.Status.values()) {
      first.restoreOnClient(
          new RecipeProcessor.State(
              status == RecipeProcessor.Status.PAUSED,
              0,
              Optional.empty(),
              0,
              Optional.empty(),
              Optional.of(status)));
      var key =
          switch (status) {
            case STALLED -> "screen.mmdlib.machine.stalled";
            case BLOCKED -> "screen.mmdlib.machine.blocked";
            case HELD -> "screen.mmdlib.machine.held";
            default -> null;
          };
      var warnings = probe.getWarnings();
      if (key == null) require(warnings.isEmpty(), "Normal/manual states are not automatic holds");
      else {
        require(warnings.size() == 1, "Exactly one warning for the affected processor");
        var text = (TranslatableContents) warnings.getFirst().getContents();
        require(
            text.getArgs()[0].equals(first.displayName()),
            "Warnings use the registered processor name");
        require(
            ((Component) text.getArgs()[1]).getContents() instanceof TranslatableContents cause
                && cause.getKey().equals(key),
            "Warning uses the library's status translation");
      }
    }
    probe.redstone.migrate(1);
    probe.redstone.readInputs();
    require(
        probe.redstone.paused() && probe.getWarnings().size() == 1, "Redstone hold is reported");
    probe.policy = MachineInventoryLifecycle.PausePolicy.TRANSFERS_ONLY;
    require(
        probe.getWarnings().isEmpty(), "A transfer-only pause does not claim processing is held");
    resources.setReservePercent(50);
    require(
        resources.getWarnings().stream()
            .anyMatch(
                c ->
                    c.getContents() instanceof TranslatableContents t
                        && t.getKey().equals("screen.mmdlib_companion.reserve.waiting")),
        "Subclasses retain custom warnings");
    helper.succeed();
  }

  private static final class Probe extends BasePoweredMachine {
    final ItemStorage items = new ItemStorage(1);
    final MachineInventoryLifecycle lifecycle;
    final RedstoneControl redstone = new RedstoneControl(this, List.of());
    MachineInventoryLifecycle.PausePolicy policy =
        MachineInventoryLifecycle.PausePolicy.PROCESSING_ONLY;
    int featureValue, visual, metadata, clientReads, worldReads;
    long powerAtRestore;

    Probe(GameTestHelper helper, int mode) {
      super(
          CompanionMod.CRUSHER.type.get(),
          helper.absolutePos(new BlockPos(2, 2, 2)),
          CompanionMod.CRUSHER.block.get().defaultBlockState(),
          3210,
          40,
          20);
      setLevel(helper.getLevel());
      var group =
          new ResourceGroup<net.neoforged.neoforge.energy.IEnergyStorage>(
              "energy",
              Component.literal("Energy"),
              0xffffff,
              ResourceType.ENERGY,
              IoMode.BOTH,
              List.of(0),
              List.of("energy"),
              new SideConfig(IoMode.BOTH));
      var automation = new AutomationInventory(this, List.of(group));
      lifecycle =
          mode == 0
              ? null
              : (mode == 1
                      ? new MachineInventoryLifecycle(this, features, automation, energy)
                      : poweredInventory(automation))
                  .items("items", items);
      features.add(
          new MachineFeature<Integer>() {
            public String key() {
              return "probe";
            }

            public Codec<Integer> codec() {
              return Codec.INT;
            }

            public Integer state() {
              return featureValue;
            }

            public void restore(Integer value) {
              featureValue = value;
              powerAtRestore = energy.stored();
            }

            public void reset() {
              featureValue = 0;
            }

            public boolean syncsToClient() {
              return true;
            }
          });
    }

    @Override
    public ItemStorage screenItems() {
      return items;
    }

    @Override
    public RedstoneControl screenRedstone() {
      return redstone;
    }

    @Override
    protected MachineInventoryLifecycle machineInventory() {
      return lifecycle;
    }

    @Override
    protected MachineInventoryLifecycle.PausePolicy pausePolicy() {
      return policy;
    }

    @Override
    protected void saveMachineData(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("metadata", metadata);
    }

    @Override
    protected void loadMachineData(CompoundTag tag, HolderLookup.Provider registries) {
      metadata = tag.getInt("metadata");
      worldReads++;
    }

    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("visual", visual);
      tag.putInt("expected_feature", featureValue);
      // These names must remain isolated from the base's persistence and sync envelopes.
      tag.putLong("energy", -1);
      tag.putString("mmdlib_features", "custom");
    }

    @Override
    protected void loadClientData(CompoundTag tag, HolderLookup.Provider registries) {
      require(
          featureValue == tag.getInt("expected_feature"),
          "Features restore before custom client state");
      visual = tag.getInt("visual");
      clientReads++;
    }

    void sendVisualUpdate() {
      syncToClient();
    }

    @Override
    protected void recoverResources(Level level, BlockPos pos, CompoundTag payload) {
      throw new AssertionError("Unexpected recovery");
    }
  }
}
