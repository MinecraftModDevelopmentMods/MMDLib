package zone.moddev.mc.companion.test;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.companion.CompanionAddons;
import zone.moddev.mc.lib.api.automation.FilterControl;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.gui.client.ElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementFragment;
import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.client.FilterEdits;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.elements.GhostSlotElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;

/**
 * Real-client coverage for a mod filter element using only the shared
 * registries and host service.
 */
final class FilterElementClientSmoke {
    interface LeftLayer extends Element {
    }

    interface RightLayer extends Element {
    }

    record Probe(String id) implements FilterControl, LeftLayer, RightLayer {
    }

    static class ModSamples extends FilterControl.Samples {
        private final SlotElement slot = new GhostSlotElement(Common.empty(), 0,
            new SlotElement.Appearance(SlotSize.LARGE, Optional.of(0x55aa88),
                Optional.empty(), ResourceLocation.parse("mmdlib:slot"), .8f));

        ModSamples() {
            super("samples", 5, ResourceType.ENERGY);
        }

        public int cellSize() {
            return 30;
        }

        public Rect sampleBounds(Rect bounds, int index) {
            var cell = super.sampleBounds(bounds, index);
            return new Rect(cell.x() + 2, cell.y() + 2, 26, 26);
        }

        public SlotElement sampleSlot(int index) {
            return slot;
        }

        public ItemStack sampleDisplay(CompoundTag configuration, int index) {
            sampleRenders++;
            return new ItemStack(index == 4 ? Items.DIAMOND : Items.EMERALD);
        }

        public List<Component> sampleTooltip(CompoundTag configuration,
            int index) {
            return List.of(Component.literal("Mod sample " + index),
                Component.literal("Custom second row"));
        }
    }

    private static final ModSamples samples = new ModSamples();
    private static int sampleRenders, submittedSample = -1, validations,
        behaviorCreations;
    private static String submittedValue;
    private static boolean rejectValidation = true;

    private static final Probe probe = new Probe("probe");
    private static final FilterControl.Text text = new FilterControl.Text(
        "text", 8, Component.literal("Text"));
    private static final FilterControl.Number number = new FilterControl.Number(
        "number", 0, 3, 1, Component.literal("Number"));
    private static final FilterControl.Custom custom = new FilterControl.Custom(
        "deny", CompanionAddons.TAG_MODE, 14);
    private static ElementFragment fragment;
    private static int frames, phase, drags, releases, cancels, removals;
    private static int enters, leaves, hostEnters, hostLeaves, parentEnters;
    private static boolean pending;
    private static final CompoundTag config = new CompoundTag();

    static void install() {
        ElementRenderers.GLOBAL.register(Probe.class,
            new ElementRenderers.Renderer<Probe>() {
                public void validate(Probe element) {
                    validations++;
                    if (rejectValidation)
                        throw new IllegalArgumentException(
                            "Missing probe dependency");
                }

                public void render(Probe element, ElementRenderers.Context c) {
                    frames++;
                    c.graphics().fill(c.bounds().x(), c.bounds().y(),
                        c.bounds().right(), c.bounds().bottom(), 0xff55aa88);
                }
            });
        ElementRenderers.GLOBAL.registerOverlay(LeftLayer.class, (e, c) -> {
        });
        ElementRenderers.GLOBAL.registerOverlay(RightLayer.class, (e, c) -> {
        });
        ElementBehaviors.GLOBAL.register(Probe.class,
            (element, access) -> new ElementBehavior() {
                {
                    behaviorCreations++;
                }

                public void hoverChanged(boolean hovered) {
                    if (hovered)
                        enters++;
                    else
                        leaves++;
                }

                public boolean focusable() {
                    return true;
                }

                public Click mouseClicked(double x, double y, int button) {
                    return Click.CAPTURE;
                }

                public boolean mouseDragged(double x, double y, int button,
                    double dx, double dy) {
                    drags++;
                    return true;
                }

                public boolean mouseReleased(double x, double y, int button) {
                    releases++;
                    return true;
                }

                public void cancelInteraction() {
                    cancels++;
                }

                public void removed() {
                    require(leaves == enters,
                        "Fragment leaves hover before disposing its behavior");
                    removals++;
                }
            });
    }

    static void render(GuiGraphics graphics) {
        if (fragment != null) {
            var r = fragment.bounds(probe);
            fragment.render(graphics, phase == 1 ? r.x() + 2 : -1,
                phase == 1 ? r.y() + 2 : -1);
        }
    }

    static boolean run() {
        if (phase == 0) {
            var validationHost = new ElementFragment.Host() {
                public ResourceLocation screenId() {
                    return ResourceLocation.parse("test:validation");
                }

                public String source(Element element) {
                    return "mod-filter.xml";
                }
            };
            expectInvalidFragment(validationHost,
                "mod-filter.xml: Missing probe dependency");
            rejectValidation = false;
            expectInvalidFragment(validationHost,
                "mod-filter.xml: Ambiguous element renderers for OVERLAY");
            require(behaviorCreations == 0,
                "Validation must finish before behaviors are created");
            ElementRenderers.GLOBAL.registerOverlay(Probe.class, (e, c) -> {
            });
            FilterEdits edits = (element, entry, value) -> {
                if (element == samples) {
                    submittedSample = entry;
                    submittedValue = value;
                    return true;
                }
                return element.edit(new FilterControl.Edit(null, null, config,
                    entry, value, null, null));
            };
            fragment = new ElementFragment(
                new FilterControl.Box("root", false,
                    List.of(probe, text, number, custom, samples)),
                100, new ElementFragment.Host() {
                    public ResourceLocation screenId() {
                        return ResourceLocation.parse("test:fragment");
                    }

                    public Object value(Element element, boolean maximum) {
                        return config.copy();
                    }

                    public boolean pending(Element element) {
                        return pending;
                    }

                    public void hoverChanged(Element element, boolean entered) {
                        if (element == probe) {
                            if (entered)
                                hostEnters++;
                            else
                                hostLeaves++;
                        }
                        if (element instanceof FilterControl.Box && entered)
                            parentEnters++;
                    }

                    public <T> Optional<T> service(Class<T> type) {
                        return type.isInstance(edits)
                            ? Optional.of(type.cast(edits))
                            : Optional.empty();
                    }
                });
            fragment.place(5, 5, new Rect(5, 5, 100, fragment.height()));
            phase++;
            return false;
        }
        if (phase == 1) {
            require(frames > 0, "Mod control uses registered renderer");
            require(validations >= 3 && behaviorCreations == 1,
                "Fragment shares renderer validation");
            require(sampleRenders > 0,
                "Sample subclass inherits renderer and display hook");
            require(
                enters == 1 && leaves == 0 && hostEnters == 1
                    && parentEnters == 0,
                "Repeated fragment frames enter only the topmost element once");
            fragment.place(5, 5, new Rect(5, 5, 1, fragment.height()));
            require(leaves == 1 && hostLeaves == 1,
                "Clipping leaves even while the pointer is inside element bounds");
            fragment.place(5, 5, new Rect(5, 5, 100, fragment.height()));
            require(enters == 2 && hostEnters == 2,
                "Expanding the viewport reenters the stationary pointer");
            var r = fragment.bounds(probe);
            fragment.click(r.x() + 2, r.y() + 2, 0);
            fragment.drag(500, 500, 0, 3, 3);
            fragment.release(500, 500, 0);
            require(drags == 1 && releases == 1,
                "Captured drag/release survives leaving the viewport");
            fragment.click(r.x() + 2, r.y() + 2, 0);
            int before = cancels;
            fragment.place(6, 5, new Rect(6, 5, 100, fragment.height()));
            fragment.release(500, 500, 0);
            require(cancels > before && releases == 1,
                "Viewport movement cancels without committing");
            r = fragment.bounds(probe);
            fragment.click(r.x() + 2, r.y() + 2, 0);
            before = cancels;
            fragment.place(6, 5, new Rect(6, 5, 100, 10));
            fragment.release(500, 500, 0);
            require(cancels > before && releases == 1,
                "Clipping resize cancels a partly visible capture");
            fragment.place(6, 5, new Rect(6, 5, 100, fragment.height()));
            r = fragment.bounds(text);
            fragment.click(r.x() + 3, r.y() + 5, 0);
            fragment.release(r.x() + 3, r.y() + 5, 0);
            fragment.character('x', 0);
            fragment.key(257, 0, 0);
            require(config.getString("text").equals("x"),
                "Text behavior submits its draft");
            fragment.key(256, 0, 0);
            require(!fragment.character('y', 0), "Escape releases text focus");
            r = fragment.bounds(number);
            fragment.click(r.right() - 2, r.y() + 5, 0);
            require(config.getInt("number") == 1,
                "Number behavior edits its own value");
            pending = true;
            fragment.click(r.right() - 2, r.y() + 5, 0);
            require(config.getInt("number") == 1,
                "Pending requests suppress edits");
            pending = false;
            r = fragment.bounds(custom);
            fragment.click(r.x() + 3, r.y() + 3, 0);
            fragment.release(r.x() + 3, r.y() + 3, 0);
            require(config.getBoolean("deny"),
                "Existing filter widget adapts to shared routing");
            var sampleBounds = samples.sampleBounds(fragment.bounds(samples),
                4);
            var rows = fragment.tooltip(sampleBounds.x() + 1,
                sampleBounds.y() + 1);
            require(
                rows.stream()
                    .anyMatch(row -> row.getString().equals("Mod sample 4"))
                    && rows.stream().anyMatch(
                        row -> row.getString().equals("Custom second row")),
                "Custom sample tooltip rows");
            fragment.click(sampleBounds.x() + 1, sampleBounds.y() + 1, 0);
            require(submittedSample == 4 && submittedValue.equals("sample"),
                "Custom geometry selects sample index");
            fragment.click(sampleBounds.x() + 1, sampleBounds.y() + 1, 1);
            require(submittedSample == 4 && submittedValue.equals("clear"),
                "Custom geometry preserves clearing");
            submittedSample = -1;
            fragment.click(sampleBounds.x() - 1, sampleBounds.y() + 1, 0);
            require(submittedSample == -1, "Sample gaps do not submit edits");
            verifyDefaultSamples();
            phase++;
            return false;
        }
        if (phase == 2) {
            var mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, "filter-element-fragment.png",
                mc.getMainRenderTarget(), message -> {
                });
            var r = fragment.bounds(probe);
            fragment.mouseMoved(r.x() + 2, r.y() + 2);
            fragment.removed();
            require(enters == hostEnters && leaves == hostLeaves,
                "Host and behavior observe the same transitions");
            fragment = null;
            require(removals == 1, "Replacement/removal releases mod state");
            phase++;
        }
        return true;
    }

    private static void expectInvalidFragment(ElementFragment.Host host,
        String message) {
        try {
            new ElementFragment(probe, 100, host);
            throw new AssertionError("Invalid fragment was accepted");
        } catch (zone.moddev.mc.lib.gui.load.ScreenException expected) {
            require(expected.getMessage().startsWith(message),
                "Fragment diagnostic: " + expected.getMessage());
        }
    }

    private static void verifyDefaultSamples() {
        var tags = new CompoundTag();
        var entries = new ListTag();
        entries.add(StringTag.valueOf("minecraft:stone"));
        entries.add(StringTag.valueOf("minecraft:water"));
        entries.add(StringTag.valueOf("invalid id"));
        tags.put("samples", entries);
        var item = new FilterControl.Samples("samples", 3, ResourceType.ITEMS);
        var fluid = new FilterControl.Samples("samples", 3,
            ResourceType.FLUIDS);
        require(item.sampleDisplay(tags, 0).is(Items.STONE),
            "Default item display preserved");
        require(fluid.sampleDisplay(tags, 1).is(Items.WATER_BUCKET),
            "Default fluid bucket preserved");
        require(item.sampleDisplay(tags, 2).isEmpty(),
            "Invalid sample remains empty");
        require(
            item.sampleTooltip(tags, 0).getFirst().getString()
                .equals(new ItemStack(Items.STONE).getHoverName().getString()),
            "Default tooltip follows displayed sample");
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
