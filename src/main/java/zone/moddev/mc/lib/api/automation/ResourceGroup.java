package zone.moddev.mc.lib.api.automation;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.capabilities.SideConfig;

/** One independently configurable item inventory, tank, buffer or mod-defined resource. */
public final class ResourceGroup<T> {
    private final String id;
    private final Component name;
    private final int colour;
    private final ResourceType<T> type;
    private final IoMode supported;
    private final List<Integer> members;
    private final List<String> highlights;
    private final SideConfig sides;
    private final String definitionKey;
    private String inventory = "";
    private Function<Direction, T> port;
    private Supplier<Direction> facing = () -> Direction.NORTH;

    public ResourceGroup(
        String id,
        Component name,
        int colour,
        ResourceType<T> type,
        IoMode supported,
        List<Integer> members,
        List<String> highlights,
        SideConfig sides) {
        if (!id.matches("[a-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("Invalid group ID: " + id);
        if ((colour & 0xff000000) != 0)
            throw new IllegalArgumentException("Group colour must be RGB");
        if (members.stream().anyMatch(i -> i < 0)
            || members.stream().distinct().count() != members.size())
            throw new IllegalArgumentException("Invalid or duplicate group members");
        this.id = id;
        this.name = Objects.requireNonNull(name).copy();
        this.colour = colour;
        this.type = Objects.requireNonNull(type);
        this.supported = Objects.requireNonNull(supported);
        this.members = List.copyOf(members);
        this.highlights = List.copyOf(highlights);
        this.sides = Objects.requireNonNull(sides);
        definitionKey = id
            + "/"
            + Integer.toHexString(
                Objects.hash(
                    type.id().toString(), supported.name(), colour, this.members, this.highlights));
        for (var face : RelativeFace.values())
            sides.set(face, intersect(sides.get(face), supported));
    }

    public String id() {
        return id;
    }

    /** Identity of the backing inventory; member indexes are local to this inventory. */
    public String inventory() {
        return inventory;
    }

    public ResourceGroup<T> inventory(String name) {
        if (port != null)
            throw new IllegalStateException("Inventory identity must be set before binding");
        if (name == null || !name.matches("[a-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("Invalid inventory identity");
        inventory = name;
        return this;
    }

    public String definitionKey() {
        return definitionKey;
    }

    public Component name() {
        return name.copy();
    }

    public int colour() {
        return colour;
    }

    public ResourceType<T> type() {
        return type;
    }

    public IoMode supported() {
        return supported;
    }

    public List<Integer> members() {
        return members;
    }

    public List<String> highlights() {
        return highlights;
    }

    public SideConfig sides() {
        return sides;
    }

    public boolean contains(int index) {
        return members.contains(index);
    }

    public ResourceGroup<T> bind(Supplier<Direction> facing, Function<Direction, T> port) {
        this.facing = Objects.requireNonNull(facing);
        this.port = Objects.requireNonNull(port);
        return this;
    }

    public T port(Direction side) {
        if (port == null)
            throw new IllegalStateException("Unbound resource group: " + id);
        return port.apply(side);
    }

    public Direction world(RelativeFace face) {
        return face.toWorld(facing.get());
    }

    /** Null-side access keeps the declared role; configured directions follow machine rotation. */
    public IoMode mode(Direction side) {
        return intersect(supported, side == null ? IoMode.BOTH : sides.mode(facing.get(), side));
    }

    public boolean supports(IoMode mode) {
        return (!mode.canInsert() || supported.canInsert())
            && (!mode.canExtract() || supported.canExtract());
    }

    public IoMode next(IoMode current, boolean reverse) {
        var modes = IoMode.values();
        int index = current.ordinal();
        do {
            index = Math.floorMod(index + (reverse ? -1 : 1), modes.length);
        } while (!supports(modes[index]));
        return modes[index];
    }

    /** Checked compare-and-set: concurrent viewers cannot cycle a newer value accidentally. */
    public boolean cycle(RelativeFace face, IoMode expected, boolean reverse) {
        if (!sides.faceAvailable(face) || sides.get(face) != expected)
            return false;
        sides.set(face, next(expected, reverse));
        return true;
    }

    public static IoMode intersect(IoMode a, IoMode b) {
        boolean in = a.canInsert() && b.canInsert(), out = a.canExtract() && b.canExtract();
        return in ? (out ? IoMode.BOTH : IoMode.INPUT) : (out ? IoMode.OUTPUT : IoMode.DISABLED);
    }
}
