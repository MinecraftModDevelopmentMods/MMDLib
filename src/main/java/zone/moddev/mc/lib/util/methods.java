package zone.moddev.mc.lib.util;

import java.util.Random;

import net.minecraft.resources.ResourceLocation;

/** Miscellaneous helpers. */
public class methods {
  private methods() {}

  /** A random 16-character ID in the {@code minecraft} namespace. */
  public static ResourceLocation makeRandomName() {
    Random r = new Random();
    String valid = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-./";
    StringBuilder k = new StringBuilder();
    for (int i = 0; i < 16; i++) k.append(valid.charAt(r.nextInt(valid.length())));
    return ResourceLocation.parse(k.toString());
  }
}
