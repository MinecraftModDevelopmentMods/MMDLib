package zone.moddev.mc.lib.common.runtime;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import zone.moddev.mc.lib.api.MaterialEventContexts.RedstoneChange;
import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.api.MaterialEvents;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MaterialPart;

/**
 * A plain material block that reacts to and/or produces redstone, configured by the {@code
 * mmdlib:block} module's {@code factory_data.redstone}. Only the properties a block's settings use
 * are added to its state.
 */
public class RedstoneBlock extends Block {
  /**
   * Received power, stored so {@code redstone-changed} fires once per change. Present when {@code
   * input} is set.
   */
  public static final IntegerProperty INPUT = IntegerProperty.create("input", 0, 15);

  /**
   * Emitted power, set by handlers through {@link #setOutput}. Present when {@code output} is
   * {@code "state"}.
   */
  public static final IntegerProperty OUTPUT = IntegerProperty.create("output", 0, 15);

  /**
   * {@code input}: track received power and fire {@code redstone-changed}. {@code output}: a
   * constant 0-15, or {@code "state"} for power stored in the block. {@code strong}: also power
   * through the touching block. {@code faces}: faces that emit (all by default). {@code
   * comparator}: a constant 0-15, {@code "input"}, or {@code "output"}.
   */
  public record Settings(
      boolean input,
      Optional<Integer> constantOutput,
      boolean stateOutput,
      boolean strong,
      Set<Direction> faces,
      Optional<Integer> constantComparator,
      Optional<IntegerProperty> comparatorProperty) {
    /** Whether the block outputs any redstone signal. */
    public boolean emits() {
      return stateOutput || constantOutput.filter(power -> power > 0).isPresent();
    }

    /**
     * Decodes a part's {@code factory_data.redstone}, if present.
     *
     * @throws IllegalArgumentException naming the part, if the settings are invalid
     */
    public static Optional<Settings> of(MaterialPart part) {
      var json = part.factoryData().json();
      if (!json.isJsonObject() || !json.getAsJsonObject().has("redstone")) return Optional.empty();
      if (!json.getAsJsonObject().get("redstone").isJsonObject())
        throw error(part, "redstone must be an object");
      JsonObject redstone = json.getAsJsonObject().getAsJsonObject("redstone");
      for (String key : redstone.keySet())
        if (!Set.of("input", "output", "strong", "faces", "comparator").contains(key))
          throw error(part, "unknown redstone key '" + key + "'");
      boolean input = redstone.has("input") && redstone.get("input").getAsBoolean();
      Optional<Integer> constant = Optional.empty();
      boolean state = false;
      if (redstone.has("output")) {
        JsonElement output = redstone.get("output");
        if (output.isJsonPrimitive() && output.getAsJsonPrimitive().isString()) {
          if (!output.getAsString().equals("state"))
            throw error(part, "output must be 0-15 or \"state\"");
          state = true;
        } else constant = Optional.of(power(part, output, "output"));
      }
      boolean strong = redstone.has("strong") && redstone.get("strong").getAsBoolean();
      Set<Direction> faces = EnumSet.allOf(Direction.class);
      if (redstone.has("faces")) {
        faces = EnumSet.noneOf(Direction.class);
        for (JsonElement face : redstone.getAsJsonArray("faces")) {
          Direction direction = Direction.byName(face.getAsString());
          if (direction == null) throw error(part, "unknown face '" + face.getAsString() + "'");
          faces.add(direction);
        }
      }
      Optional<Integer> comparator = Optional.empty();
      Optional<IntegerProperty> comparatorProperty = Optional.empty();
      if (redstone.has("comparator")) {
        JsonElement value = redstone.get("comparator");
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
          comparatorProperty =
              Optional.of(
                  switch (value.getAsString()) {
                    case "input" -> {
                      if (!input) throw error(part, "comparator \"input\" needs input: true");
                      yield INPUT;
                    }
                    case "output" -> {
                      if (!state)
                        throw error(part, "comparator \"output\" needs output: \"state\"");
                      yield OUTPUT;
                    }
                    default ->
                        throw error(part, "comparator must be 0-15, \"input\", or \"output\"");
                  });
        } else comparator = Optional.of(power(part, value, "comparator"));
      }
      if ((constant.isPresent() || state) && faces.isEmpty())
        throw error(part, "faces must name at least one face");
      return Optional.of(
          new Settings(
              input, constant, state, strong, Set.copyOf(faces), comparator, comparatorProperty));
    }

    private static int power(MaterialPart part, JsonElement value, String key) {
      int power = value.getAsInt();
      if (power < 0 || power > 15) throw error(part, key + " must be 0-15");
      return power;
    }

    private static IllegalArgumentException error(MaterialPart part, String message) {
      return new IllegalArgumentException("Invalid redstone settings for " + part + ": " + message);
    }
  }

  // createBlockStateDefinition runs inside Block's constructor, before fields are set.
  private static final ThreadLocal<Settings> CONSTRUCTING = new ThreadLocal<>();
  private final Settings settings;
  private final MaterialEvents events;
  private final MMDBlock definition;
  private final MMDMaterial material;

  /**
   * A block with the given redstone settings; {@code redstone-changed} is dispatched through {@code
   * events} with the definition's or material's binding.
   */
  public RedstoneBlock(
      Properties properties,
      Settings settings,
      MaterialEvents events,
      MMDBlock definition,
      MMDMaterial material) {
    super(constructing(properties, settings));
    CONSTRUCTING.remove();
    this.settings = settings;
    this.events = events;
    this.definition = definition;
    this.material = material;
  }

  private static Properties constructing(Properties properties, Settings settings) {
    CONSTRUCTING.set(settings);
    return properties;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    Settings pending = CONSTRUCTING.get();
    if (pending.input()) builder.add(INPUT);
    if (pending.stateOutput()) builder.add(OUTPUT);
  }

  /** The block's redstone settings. */
  public Settings settings() {
    return settings;
  }

  /**
   * Sets a {@code "state"} output block's emitted power (0-15) and updates the blocks it powers.
   * Server side.
   */
  public static void setOutput(Level level, BlockPos pos, int power) {
    BlockState state = level.getBlockState(pos);
    if (!(state.getBlock() instanceof RedstoneBlock block) || !block.settings.stateOutput())
      throw new IllegalArgumentException("Not a redstone block with output \"state\": " + state);
    int clamped = Math.max(0, Math.min(15, power));
    if (state.getValue(OUTPUT) == clamped) return;
    level.setBlock(pos, state.setValue(OUTPUT, clamped), Block.UPDATE_ALL);
    block.updateNeighbours(level, pos);
  }

  @Override
  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
    super.onPlace(state, level, pos, old, moved);
    if (!old.is(state.getBlock())) {
      if (settings.input()) checkInput(state, level, pos);
      if (settings.emits()) updateNeighbours(level, pos);
    }
  }

  @Override
  protected void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
    boolean emitting = !moved && !state.is(replacement.getBlock()) && settings.emits();
    super.onRemove(state, level, pos, replacement, moved);
    if (emitting) updateNeighbours(level, pos);
  }

  @Override
  protected void neighborChanged(
      BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moved) {
    super.neighborChanged(state, level, pos, neighbor, from, moved);
    if (settings.input()) checkInput(state, level, pos);
  }

  private void checkInput(BlockState state, Level level, BlockPos pos) {
    if (!(level instanceof ServerLevel server)) return;
    int previous = state.getValue(INPUT), power = server.getBestNeighborSignal(pos);
    if (previous == power) return;
    BlockState updated = state.setValue(INPUT, power);
    server.setBlock(pos, updated, Block.UPDATE_CLIENTS);
    var handler =
        ItemEventBridge.binding(
            definition.events(), material, MaterialEventTypes.REDSTONE_CHANGED.name());
    if (handler != null)
      events.dispatch(
          handler,
          MaterialEventTypes.REDSTONE_CHANGED,
          new RedstoneChange(server, pos, updated, previous, power));
  }

  private void updateNeighbours(Level level, BlockPos pos) {
    level.updateNeighborsAt(pos, this);
    // A strong signal powers the touching block, so that block's own neighbours must update too.
    if (settings.strong())
      for (Direction face : settings.faces()) level.updateNeighborsAt(pos.relative(face), this);
  }

  private int emitted(BlockState state) {
    return settings.stateOutput() ? state.getValue(OUTPUT) : settings.constantOutput().orElse(0);
  }

  @Override
  protected boolean isSignalSource(BlockState state) {
    return settings.emits();
  }

  // Direction points from the asking block towards this one, so the emitting face is its opposite.
  @Override
  protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    return settings.faces().contains(direction.getOpposite()) ? emitted(state) : 0;
  }

  @Override
  protected int getDirectSignal(
      BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    return settings.strong() ? getSignal(state, level, pos, direction) : 0;
  }

  @Override
  protected boolean hasAnalogOutputSignal(BlockState state) {
    return settings.constantComparator().isPresent() || settings.comparatorProperty().isPresent();
  }

  @Override
  protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    return settings
        .comparatorProperty()
        .map(state::getValue)
        .orElseGet(() -> settings.constantComparator().orElse(0));
  }
}
