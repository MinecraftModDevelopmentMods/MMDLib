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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
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

  /** The way the block faces: its front. Present when {@code facing} is set. */
  // Created here rather than taken from BlockStateProperties, whose class initialization needs a
  // bootstrapped game; the name and values are the same.
  public static final DirectionProperty FACING =
      DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);

  /**
   * {@code input}: track received power and fire {@code redstone-changed}. {@code output}: a
   * constant 0-15, or {@code "state"} for power stored in the block. {@code strong}: also power
   * through the touching block. {@code faces}: faces that emit (all by default). {@code
   * comparator}: a constant 0-15, {@code "input"}, or {@code "output"}. {@code facing}: the block
   * faces the way its placer looked, and {@code faces} and {@code inputs} may name {@code front},
   * {@code back}, {@code left} and {@code right}. {@code inputs}: the faces read (all by default).
   * {@code delay}: ticks between a neighbor change and reading the inputs (0, the default, reads at
   * once).
   *
   * <p>With {@code facing}, {@link #faces} and {@link #inputs} hold the faces of a block facing
   * north: front is north, back south, left west, right east.
   */
  public record Settings(
      boolean input,
      Optional<Integer> constantOutput,
      boolean stateOutput,
      boolean strong,
      Set<Direction> faces,
      Optional<Integer> constantComparator,
      Optional<IntegerProperty> comparatorProperty,
      boolean facing,
      Set<Direction> inputs,
      int delay) {
    /** Settings without facing, reading every face at once. */
    public Settings(
        boolean input,
        Optional<Integer> constantOutput,
        boolean stateOutput,
        boolean strong,
        Set<Direction> faces,
        Optional<Integer> constantComparator,
        Optional<IntegerProperty> comparatorProperty) {
      this(
          input,
          constantOutput,
          stateOutput,
          strong,
          faces,
          constantComparator,
          comparatorProperty,
          false,
          Set.of(Direction.values()),
          0);
    }

    /**
     * Whether {@code redstone-changed} fires on any read face's change rather than only when the
     * strongest input changes: true when only some faces are read.
     */
    public boolean perFace() {
      return inputs.size() < Direction.values().length;
    }

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
        if (!Set.of("input", "output", "strong", "faces", "comparator", "facing", "inputs", "delay")
            .contains(key))
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
      boolean facing = redstone.has("facing") && redstone.get("facing").getAsBoolean();
      Set<Direction> faces = faceList(part, redstone, "faces", facing);
      Set<Direction> inputs = faceList(part, redstone, "inputs", facing);
      if (redstone.has("inputs") && !input) throw error(part, "inputs needs input: true");
      if (redstone.has("inputs") && inputs.isEmpty())
        throw error(part, "inputs must name at least one face");
      int delay = redstone.has("delay") ? redstone.get("delay").getAsInt() : 0;
      if (delay < 0 || delay > 100) throw error(part, "delay must be 0-100 ticks");
      if (delay > 0 && !input) throw error(part, "delay needs input: true");
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
              input,
              constant,
              state,
              strong,
              Set.copyOf(faces),
              comparator,
              comparatorProperty,
              facing,
              Set.copyOf(inputs),
              delay));
    }

    /** A face list, all faces when absent; relative names need {@code facing}. */
    private static Set<Direction> faceList(
        MaterialPart part, JsonObject redstone, String key, boolean facing) {
      if (!redstone.has(key)) return EnumSet.allOf(Direction.class);
      if (!redstone.get(key).isJsonArray()) throw error(part, key + " must be a list of faces");
      Set<Direction> faces = EnumSet.noneOf(Direction.class);
      for (JsonElement face : redstone.getAsJsonArray(key)) {
        String name = face.getAsString();
        Direction direction =
            switch (name) {
              case "front", "back", "left", "right" -> {
                if (!facing) throw error(part, "'" + name + "' in " + key + " needs facing: true");
                yield relative(name);
              }
              default -> Direction.byName(name);
            };
        if (direction == null) throw error(part, "unknown face '" + name + "'");
        faces.add(direction);
      }
      return faces;
    }

    /** A relative face name as the face of a block facing north. */
    private static Direction relative(String name) {
      return switch (name) {
        case "front" -> Direction.NORTH;
        case "back" -> Direction.SOUTH;
        case "left" -> Direction.WEST;
        default -> Direction.EAST;
      };
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
  private final BlockHooks hooks;

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
    hooks = new BlockHooks(events, definition, material);
  }

  @Override
  public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
    super.stepOn(level, pos, state, entity);
    hooks.steppedOn(level, pos, state, entity);
  }

  @Override
  protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    super.randomTick(state, level, pos, random);
    hooks.randomTick(level, pos, state);
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
    if (pending.facing()) builder.add(FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    BlockState state = defaultBlockState();
    // Placed facing away from the player, like a repeater: the front is where they looked.
    return settings.facing() ? state.setValue(FACING, context.getHorizontalDirection()) : state;
  }

  @Override
  protected BlockState rotate(BlockState state, Rotation rotation) {
    return settings.facing() ? state.setValue(FACING, rotation.rotate(state.getValue(FACING))) : state;
  }

  @Override
  protected BlockState mirror(BlockState state, Mirror mirror) {
    return settings.facing() ? state.rotate(mirror.getRotation(state.getValue(FACING))) : state;
  }

  /**
   * The world direction of a face given as on a block facing north ({@link Settings#faces}): the
   * same face for blocks without facing.
   */
  public Direction toWorld(BlockState state, Direction face) {
    return settings.facing() ? rotate(face, state.getValue(FACING)) : face;
  }

  /** The inverse of {@link #toWorld}. */
  public Direction toLocal(BlockState state, Direction face) {
    return settings.facing() ? unrotate(face, state.getValue(FACING)) : face;
  }

  /**
   * {@code face} of a block facing north, turned with the block to face {@code facing}: the front
   * (north) becomes {@code facing}. Up and down do not turn.
   */
  public static Direction rotate(Direction face, Direction facing) {
    if (face.getAxis().isVertical()) return face;
    return Direction.from2DDataValue(face.get2DDataValue() + turns(facing));
  }

  /** The inverse of {@link #rotate}: a world face of a block facing {@code facing}, as if north. */
  public static Direction unrotate(Direction face, Direction facing) {
    if (face.getAxis().isVertical()) return face;
    return Direction.from2DDataValue(face.get2DDataValue() - turns(facing) + 4);
  }

  // Quarter turns clockwise from north to facing.
  private static int turns(Direction facing) {
    return (facing.get2DDataValue() - Direction.NORTH.get2DDataValue() + 4) % 4;
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
    block.updateNeighbours(level, pos, state);
  }

  @Override
  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
    super.onPlace(state, level, pos, old, moved);
    if (!old.is(state.getBlock())) {
      if (settings.input()) inputChanged(state, level, pos);
      if (settings.emits()) updateNeighbours(level, pos, state);
    }
  }

  @Override
  protected void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
    boolean emitting = !moved && !state.is(replacement.getBlock()) && settings.emits();
    super.onRemove(state, level, pos, replacement, moved);
    if (emitting) updateNeighbours(level, pos, state);
  }

  @Override
  protected void neighborChanged(
      BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moved) {
    super.neighborChanged(state, level, pos, neighbor, from, moved);
    if (settings.input()) inputChanged(state, level, pos);
  }

  // A delay defers reading to a scheduled tick, like a repeater, so wired loops advance one step
  // per tick instead of recursing.
  private void inputChanged(BlockState state, Level level, BlockPos pos) {
    if (!(level instanceof ServerLevel server)) return;
    if (settings.delay() == 0) checkInput(state, server, pos);
    else if (!server.getBlockTicks().hasScheduledTick(pos, this))
      server.scheduleTick(pos, this, settings.delay());
  }

  @Override
  protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    if (settings.input()) checkInput(state, level, pos);
  }

  /** The power a face receives from its neighbor, as {@link Level#getSignal} reports it. */
  public static int signal(Level level, BlockPos pos, Direction face) {
    return level.getSignal(pos.relative(face), face);
  }

  private void checkInput(BlockState state, ServerLevel server, BlockPos pos) {
    int previous = state.getValue(INPUT), power = 0;
    for (Direction face : settings.inputs())
      power = Math.max(power, signal(server, pos, toWorld(state, face)));
    // Blocks reading single faces react to any face's change; the strongest may stay the same.
    if (previous == power && !settings.perFace()) return;
    BlockState updated = state.setValue(INPUT, power);
    if (previous != power) server.setBlock(pos, updated, Block.UPDATE_CLIENTS);
    var handler =
        ItemEventBridge.binding(
            definition.events(), material, MaterialEventTypes.REDSTONE_CHANGED.name());
    if (handler != null)
      events.dispatch(
          handler,
          MaterialEventTypes.REDSTONE_CHANGED,
          new RedstoneChange(server, pos, updated, previous, power));
  }

  private void updateNeighbours(Level level, BlockPos pos, BlockState state) {
    level.updateNeighborsAt(pos, this);
    // A strong signal powers the touching block, so that block's own neighbours must update too.
    if (settings.strong())
      for (Direction face : settings.faces())
        level.updateNeighborsAt(pos.relative(toWorld(state, face)), this);
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
    return settings.faces().contains(toLocal(state, direction.getOpposite())) ? emitted(state) : 0;
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
