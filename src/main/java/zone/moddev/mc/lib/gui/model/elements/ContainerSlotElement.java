package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.TooltipContext;
import zone.moddev.mc.lib.gui.runtime.EquippedContainers;

/**
 * A virtual paged cell. The drawer transport remains responsible for storage
 * interaction.
 */
public class ContainerSlotElement extends ItemSlotElement {
    /**
     * Detached frame values; selection is visual only and never authorizes a
     * request.
     */
    public record Display(ItemStack contents, boolean selected) {
        public Display {
            contents = contents.copy();
        }

        public ItemStack contents() {
            return contents.copy();
        }
    }

    @Override
    public Object displayValue(Object value) {
        return value instanceof Display display
            ? display.contents()
            : super.displayValue(value);
    }

    private final Optional<EquippedContainers.SlotInfo> description;
    private final boolean describedIcon;

    public ContainerSlotElement(int index,
        Optional<EquippedContainers.SlotInfo> description) {
        this(index, description,
            new Appearance(SlotSize.NORMAL, Optional.empty(),
                description.map(EquippedContainers.SlotInfo::icon),
                ResourceLocation.parse("mmdlib:slot"), .42f,
                Optional.of(InventoryMenu.BLOCK_ATLAS)),
            true);
    }

    public ContainerSlotElement(int index,
        Optional<EquippedContainers.SlotInfo> description,
        Appearance appearance) {
        this(index, description, appearance, false);
    }

    private ContainerSlotElement(int index,
        Optional<EquippedContainers.SlotInfo> description,
        Appearance appearance, boolean describedIcon) {
        super(Common.empty(), index, SlotKind.NORMAL, appearance);
        this.description = Objects.requireNonNull(description);
        this.describedIcon = describedIcon;
    }

    public Optional<EquippedContainers.SlotInfo> description() {
        return description;
    }

    @Override
    public Component cellLabel() {
        return description().map(EquippedContainers.SlotInfo::label)
            .orElseGet(Component::empty);
    }

    @Override
    public Optional<ResourceLocation> icon() {
        return describedIcon
            ? description().map(EquippedContainers.SlotInfo::icon)
            : super.icon();
    }

    public boolean available() {
        return description().map(EquippedContainers.SlotInfo::available)
            .orElse(true);
    }

    public Component placeholder() {
        return description().map(EquippedContainers.SlotInfo::placeholder)
            .orElseGet(Component::empty);
    }

    @Override
    public void render(Painter painter, ItemStack contents, ItemStack hint) {
        if (available())
            super.render(painter, contents, hint);
    }

    @Override
    public void appendTooltip(TooltipContext context, List<Component> rows) {
        super.appendTooltip(context, rows);
        description().ifPresent(info -> rows.addAll(info.tooltip()));
    }
}
