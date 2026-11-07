package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.data.IData;
import com.blamejared.crafttweaker.api.data.converter.JSONConverter;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.types.FactoryData;

/**
 * What every script block, item and module shares: tags, event bindings, and {@code factory_data}
 * for its Java implementation. Collections have an add and a remove function; remove accepts
 * {@code "all"}.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.DefinitionProperties")
public abstract class ScriptDefinitionProperties {
  static final String ALL = "all";

  private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

  private final Set<ResourceLocation> tags;
  private final Map<String, ResourceLocation> events;
  private final Map<String, Object> hooks = new java.util.HashMap<>();
  private JsonObject factoryData;

  ScriptDefinitionProperties(
      Set<ResourceLocation> tags, Map<String, ResourceLocation> events, FactoryData factoryData) {
    this.tags = new LinkedHashSet<>(tags);
    this.events = new LinkedHashMap<>(events);
    this.factoryData =
        factoryData.json().isJsonObject()
            ? factoryData.json().getAsJsonObject().deepCopy()
            : new JsonObject();
  }

  /** Finishes the definition. Call it once. */
  @ZenCodeType.Method
  public abstract void build();

  /** The registry or module ID, which names the definition's script callbacks. */
  abstract ResourceLocation definitionId();

  /**
   * Binds a script function to {@code event}: registers a callback {@code <id>/<event>} the first
   * time, which runs the latest function given; {@code invoke} hands the event's context to it. A
   * function that throws is logged and the game carries on.
   */
  <C, H> void hook(
      zone.moddev.mc.lib.api.MaterialEvent<C> event,
      H handler,
      java.util.function.BiConsumer<H, C> invoke) {
    var id = definitionId().withSuffix("/" + event.name());
    boolean first = !hooks.containsKey(event.name());
    hooks.put(event.name(), java.util.Objects.requireNonNull(handler));
    if (first)
      ScriptDefinitions.catalog()
          .events()
          .registerCallback(
              id,
              event,
              context -> {
                @SuppressWarnings("unchecked")
                var current = (H) hooks.get(event.name());
                try {
                  invoke.accept(current, context);
                } catch (RuntimeException ex) {
                  LOGGER.error("Script {} handler of {} failed", event.name(), definitionId(), ex);
                }
              });
    addEvent(event.name(), id.toString());
  }

  /** Settings for the Java implementation, such as {@code {detects: "players"}}. */
  @ZenCodeType.Getter("factoryData")
  public IData getFactoryData() {
    return JSONConverter.convert(factoryData);
  }

  @ZenCodeType.Setter("factoryData")
  public void setFactoryData(IData data) {
    var json = ScriptData.json(data);
    if (!json.isJsonObject())
      throw new IllegalArgumentException("factoryData must be a map, such as {speed: 2}");
    factoryData = json.getAsJsonObject();
  }

  /** The tags, such as {@code minecraft:mineable/pickaxe} for a block. */
  @ZenCodeType.Getter("tags")
  public String[] getTags() {
    return tags.stream().map(ResourceLocation::toString).sorted().toArray(String[]::new);
  }

  /** Adds tags. */
  @ZenCodeType.Method
  public ScriptDefinitionProperties addTags(String... tags) {
    for (var tag : tags) {
      if (tag.equals(ALL))
        throw new IllegalArgumentException("\"all\" can only remove tags, not add them");
      this.tags.add(ResourceLocation.parse(tag));
    }
    return this;
  }

  /** Removes tags; {@code "all"} removes every tag. */
  @ZenCodeType.Method
  public ScriptDefinitionProperties removeTags(String... tags) {
    for (var tag : tags)
      if (tag.equals(ALL)) this.tags.clear();
      else this.tags.remove(ResourceLocation.parse(tag));
    return this;
  }

  /** The event bindings, as {@code event=callback}. */
  @ZenCodeType.Getter("events")
  public String[] getEvents() {
    return events.entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .sorted()
        .toArray(String[]::new);
  }

  /**
   * Binds a callback to an event, such as {@code addEvent("block-right-click",
   * "mmdlib:open_menu")}; an existing binding for the event is replaced.
   */
  @ZenCodeType.Method
  public ScriptDefinitionProperties addEvent(String event, String callback) {
    var id = ResourceLocation.parse(callback);
    ScriptDefinitions.catalog().events().validate(Map.of(event, id));
    events.put(event, id);
    return this;
  }

  /** Removes event bindings by event name; {@code "all"} removes every binding. */
  @ZenCodeType.Method
  public ScriptDefinitionProperties removeEvents(String... events) {
    for (var event : events)
      if (event.equals(ALL)) this.events.clear();
      else this.events.remove(event);
    return this;
  }

  /** The tags set so far. */
  Set<ResourceLocation> tags() {
    return Set.copyOf(tags);
  }

  /** The event bindings set so far. */
  Map<String, ResourceLocation> events() {
    return Map.copyOf(events);
  }

  /** The {@code factory_data} set so far. */
  FactoryData factoryData() {
    return new FactoryData(factoryData.deepCopy());
  }

  /**
   * {@code name} as an ID in MMDLib's namespace: {@code "marble"} or {@code "mmdlib:marble"}.
   *
   * @throws IllegalArgumentException for another namespace
   */
  static ResourceLocation mmdlibId(String name, String what) {
    var id = ResourceLocation.parse(name.contains(":") ? name : "mmdlib:" + name);
    if (!id.getNamespace().equals("mmdlib"))
      throw new IllegalArgumentException(
          "Script "
              + what
              + "s are registered in the mmdlib namespace; use \""
              + id.getPath()
              + "\" instead of \""
              + name
              + "\"");
    return id;
  }
}
