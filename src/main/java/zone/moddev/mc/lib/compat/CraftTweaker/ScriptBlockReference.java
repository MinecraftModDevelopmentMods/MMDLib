package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import net.minecraft.resources.ResourceLocation;
import org.openzen.zencode.java.ZenCodeType;

/** A typed ID, never an eager registry lookup. The existing consumer resolves it later. */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.BlockReference")
public final class ScriptBlockReference {
  private final ResourceLocation id;

  @ZenCodeType.Constructor
  public ScriptBlockReference(String id) { this.id = ResourceLocation.parse(id); }

  @ZenCodeType.Getter("id")
  public String getId() { return id.toString(); }
}
