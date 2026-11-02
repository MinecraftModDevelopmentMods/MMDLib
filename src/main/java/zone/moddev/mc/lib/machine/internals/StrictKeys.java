package zone.moddev.mc.lib.machine.internals;

import java.util.List;
import java.util.TreeSet;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;

/** Makes a key a codec doesn't read a decode error, so a misspelt optional key can't go unseen. */
final class StrictKeys {
  private StrictKeys() {}

  /** {@code codec}, refusing any JSON object with a key outside {@code keys}. */
  static <T> Codec<T> of(Codec<T> codec, String... keys) {
    var known = new TreeSet<>(List.of(keys));
    return Codec.of(
        codec,
        new Decoder<>() {
          @Override
          public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
            var map = ops.getMap(input).result();
            if (map.isPresent()) {
              var unknown =
                  map.get()
                      .entries()
                      .map(entry -> ops.getStringValue(entry.getFirst()).result().orElse("?"))
                      .filter(key -> !known.contains(key))
                      .sorted()
                      .toList();
              if (!unknown.isEmpty())
                return DataResult.error(() -> "Unknown keys " + unknown + "; expected " + known);
            }
            return codec.decode(ops, input);
          }
        });
  }
}
