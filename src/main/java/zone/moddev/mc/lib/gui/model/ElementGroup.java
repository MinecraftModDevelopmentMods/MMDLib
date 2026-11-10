package zone.moddev.mc.lib.gui.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Read-only group association metadata, without storage, side configuration or transfer access. */
public record ElementGroup(String id, List<String> anchors, Optional<Members> members) {
    public ElementGroup {
        Objects.requireNonNull(id);
        anchors = List.copyOf(anchors);
        Objects.requireNonNull(members);
    }

    public ElementGroup(String id, List<String> anchors) {
        this(id, anchors, Optional.empty());
    }

    /** Resource type and local member addresses; interpretation belongs to the element. */
    public record Members(
        ResourceLocation type, String inventory, List<Integer> indices,
        List<Integer> legacyIndices) {
        public Members(ResourceLocation type, String inventory, List<Integer> indices) {
            this(type, inventory, indices, indices);
        }

        public Members {
            Objects.requireNonNull(type);
            Objects.requireNonNull(inventory);
            indices = List.copyOf(indices);
            legacyIndices = List.copyOf(legacyIndices);
        }
    }
}
