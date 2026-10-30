package zone.moddev.mc.lib.common.runtime;

import zone.moddev.mc.lib.common.types.MMDMaterial;

/** Runtime items expose the same immutable material definition used during planning. */
public interface MaterialBacked {
  /** The material this item was built from. */
  MMDMaterial material();
}
