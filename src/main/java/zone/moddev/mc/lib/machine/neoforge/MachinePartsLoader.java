package zone.moddev.mc.lib.machine.neoforge;

import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import net.neoforged.neoforge.event.AddReloadListenerEvent;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.machine.internals.MachineParts;
import zone.moddev.mc.lib.machine.internals.PartSubject;

/**
 * Loads machine part files from {@code data/<ns>/mmdlib/machine_part/*.json} on every datapack load
 * and installs them as {@link MachineParts#current()}.
 */
public final class MachinePartsLoader extends SimpleJsonResourceReloadListener {
  private MachinePartsLoader() {
    super(new Gson(), "mmdlib/machine_part");
  }

  /** Adds the loader to server datapack reloads; registered on the game bus by MMDLib. */
  public static void register(AddReloadListenerEvent event) {
    event.addListener(new MachinePartsLoader());
  }

  /**
   * What MMDLib knows about {@code item}: its material part (the item template, such as {@code
   * mmdlib:gear}) and its material's stats. Both are empty for items MMDLib didn't make.
   */
  public static Optional<PartSubject> subject(ResourceLocation item) {
    return Optional.of(
        new PartSubject(
            item,
            Optional.ofNullable(MMDLib.itemDefinitions().get(item)).map(MMDItem::template),
            Optional.ofNullable(MMDLib.itemMaterials().get(item)).map(MMDMaterial::properties)));
  }

  @Override
  protected void apply(
      Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
    MachineParts.install(MachineParts.parse(files, MachinePartsLoader::subject));
  }
}
