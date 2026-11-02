package zone.moddev.mc.lib.machine.internals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import zone.moddev.mc.lib.machine.stat.StatDefinition;

/**
 * A machine's internals: the sockets parts go into, and the stats they change. A mod puts {@link
 * #CODEC} inside its block entity's settings (for example as {@code factory_data.internals}), so a
 * bad definition fails at startup. JSON keys: {@code open_with}, {@code swap} (default {@code
 * when_idle}), {@code sockets}, {@code stats}.
 *
 * <p>Slots are laid out socket by socket in name order: with sockets {@code coil} (1), {@code
 * gears} (2) and {@code motor} (1), {@code coil} is slot 0, {@code gears} slots 1 and 2, {@code
 * motor} slot 3.
 *
 * @param openWith what a player must hold to open the engine view; empty for bare hands
 */
public record InternalsDefinition(
    Optional<ItemMatch> openWith,
    SwapRule swap,
    SortedMap<String, SocketDefinition> sockets,
    Map<String, StatDefinition> stats) {
  /** The JSON form. */
  public static final Codec<InternalsDefinition> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      ItemMatch.CODEC
                          .optionalFieldOf("open_with")
                          .forGetter(InternalsDefinition::openWith),
                      SwapRule.CODEC
                          .optionalFieldOf("swap", SwapRule.WHEN_IDLE)
                          .forGetter(InternalsDefinition::swap),
                      Codec.unboundedMap(Codec.STRING, SocketDefinition.CODEC)
                          .<SortedMap<String, SocketDefinition>>xmap(TreeMap::new, Map::copyOf)
                          .optionalFieldOf("sockets", Collections.emptySortedMap())
                          .forGetter(InternalsDefinition::sockets),
                      Codec.unboundedMap(Codec.STRING, StatDefinition.CODEC)
                          .optionalFieldOf("stats", Map.of())
                          .forGetter(InternalsDefinition::stats))
                  .apply(instance, InternalsDefinition::new));

  /** Copies the maps; sockets are kept sorted by name. */
  public InternalsDefinition {
    sockets = Collections.unmodifiableSortedMap(new TreeMap<>(sockets));
    stats = Map.copyOf(stats);
  }

  /** The number of slots across every socket. */
  public int slotCount() {
    int count = 0;
    for (var socket : sockets.values()) count += socket.count();
    return count;
  }

  /**
   * The slot indexes of {@code socket}'s positions.
   *
   * @throws IllegalArgumentException if there is no such socket
   */
  public List<Integer> slots(String socket) {
    int first = 0;
    for (var entry : sockets.entrySet()) {
      if (entry.getKey().equals(socket)) {
        var slots = new ArrayList<Integer>();
        for (int i = 0; i < entry.getValue().count(); i++) slots.add(first + i);
        return List.copyOf(slots);
      }
      first += entry.getValue().count();
    }
    throw new IllegalArgumentException("Machine has no socket " + socket);
  }

  /** The socket that slot {@code slot} belongs to. */
  public String socketAt(int slot) {
    if (slot >= 0) {
      int first = 0;
      for (var entry : sockets.entrySet()) {
        first += entry.getValue().count();
        if (slot < first) return entry.getKey();
      }
    }
    throw new IllegalArgumentException("Machine has no socket slot " + slot);
  }
}
