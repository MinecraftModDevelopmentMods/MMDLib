package zone.moddev.mc.lib.gui.runtime.network;

import java.util.*;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Common-side network contributors. Register during mod construction, before payload setup. */
public final class NetworkRegistry {
  public static final NetworkRegistry GLOBAL = builtins();

  /** A feature declares typed payloads; their codecs and handlers remain owned by that feature. */
  public interface Registrar {
    <T extends CustomPacketPayload> void playToServer(
        CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
        IPayloadHandler<T> handler);

    <T extends CustomPacketPayload> void playToClient(
        CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
        IPayloadHandler<T> handler);
  }

  public enum Direction {
    TO_SERVER,
    TO_CLIENT
  }

  public record Payload<T extends CustomPacketPayload>(
      CustomPacketPayload.Type<T> type,
      StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
      IPayloadHandler<T> handler,
      Direction direction) {
    public Payload {
      Objects.requireNonNull(type);
      Objects.requireNonNull(codec);
      Objects.requireNonNull(handler);
      Objects.requireNonNull(direction);
    }

    void install(PayloadRegistrar registrar) {
      if (direction == Direction.TO_SERVER) registrar.playToServer(type, codec, handler);
      else registrar.playToClient(type, codec, handler);
    }
  }

  public record Contributor(ResourceLocation id, String version, List<Payload<?>> payloads) {
    public Contributor {
      payloads = List.copyOf(payloads);
    }
  }

  private static final class Collector implements Registrar {
    private final List<Payload<?>> payloads = new ArrayList<>();
    private boolean closed;

    private <T extends CustomPacketPayload> void add(
        CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
        IPayloadHandler<T> handler,
        Direction direction) {
      if (closed) throw new IllegalStateException("Network declaration is closed");
      payloads.add(new Payload<>(type, codec, handler, direction));
    }

    public <T extends CustomPacketPayload> void playToServer(
        CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
        IPayloadHandler<T> handler) {
      add(type, codec, handler, Direction.TO_SERVER);
    }

    public <T extends CustomPacketPayload> void playToClient(
        CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
        IPayloadHandler<T> handler) {
      add(type, codec, handler, Direction.TO_CLIENT);
    }
  }

  private final Map<ResourceLocation, Contributor> contributors =
      new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
  private final Set<ResourceLocation> payloadIds = new HashSet<>();
  private List<Contributor> frozen;

  public static NetworkRegistry builtins() {
    var registry = new NetworkRegistry();
    BuiltinNetworks.register(registry);
    return registry;
  }

  /** Declarations run immediately; a failed or conflicting declaration registers nothing. */
  public synchronized void register(
      ResourceLocation id, String version, Consumer<Registrar> declare) {
    if (frozen != null) throw new IllegalStateException("Network registry is frozen");
    Objects.requireNonNull(id);
    Objects.requireNonNull(version);
    Objects.requireNonNull(declare);
    if (version.isBlank()) throw new IllegalArgumentException("Network version must not be blank");
    if (contributors.containsKey(id))
      throw new IllegalArgumentException("Duplicate network contributor: " + id);
    var collector = new Collector();
    try {
      declare.accept(collector);
    } finally {
      collector.closed = true;
    }
    var ids = new HashSet<ResourceLocation>();
    for (var payload : collector.payloads) {
      var name = payload.type().id();
      if (!ids.add(name) || payloadIds.contains(name))
        throw new IllegalArgumentException("Duplicate network payload: " + name);
    }
    contributors.put(id, new Contributor(id, version, collector.payloads));
    payloadIds.addAll(ids);
  }

  /** Immutable startup declarations in contributor-ID order; later registration is rejected. */
  public synchronized List<Contributor> freeze() {
    if (frozen == null) frozen = List.copyOf(contributors.values());
    return frozen;
  }

  /** NeoForge owns negotiation and transport; this registry only assembles the declarations. */
  public void install(RegisterPayloadHandlersEvent event) {
    for (var contributor : freeze()) {
      var registrar = event.registrar(contributor.version());
      for (var payload : contributor.payloads()) payload.install(registrar);
    }
  }
}
