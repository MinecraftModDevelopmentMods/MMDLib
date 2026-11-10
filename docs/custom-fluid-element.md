# A complete custom fluid element

This example targets **NeoForge 1.21.1 / Java 21** and the current MMDLib API. It adds
a water-only, 4,000 mB tank with persistent storage, a custom gauge, cursor-container
transfers, and a namespaced XML tag. All Java files below include their imports.

The example plugs into a mod with ID `demo` and an existing registered machine block
and block-entity type `demo:machine`. Keep your normal block/item/type registration,
assets and MMDLib dependency declaration; those are outside this element example.
Use the host class below as that type's block entity, passing its registered type to
the constructor. Replace `demo` and `com.example.demo` consistently for your mod.

The responsibilities are:

| Piece | Owns |
|---|---|
| `MyFluidStorage` | Capacity, accepted fluids and change notifications |
| `MyMachineBlockEntity` | Storage lifetime and persistence |
| `MyFluidTankElement` | Server snapshots, tooltip rows and transfer clicks |
| `MyElements` | Common element declaration and XML registration |
| `MyClientElements` | Client-only renderer registration and drawing |

## 1. Supply your own storage

`MyFluidStorage.java` uses NeoForge's `FluidTank`, independently of MMDLib's
`FluidStorage`. You can replace it with your own `IFluidHandler` implementation.
The handler returned to each element must expose exactly one tank at local index 0.
Its fill/drain operations must honor simulation without modifying contents.

```java
package com.example.demo;

import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class MyFluidStorage extends FluidTank {
    private final Runnable changed;

    public MyFluidStorage(Runnable changed) {
        super(4000, stack -> stack.getFluid() == Fluids.WATER);
        this.changed = changed;
    }

    @Override
    protected void onContentsChanged() {
        changed.run();
    }
}
```

`MyTankHost.java` is the small contract between the element and your machine:

```java
package com.example.demo;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

public interface MyTankHost extends ScreenHost {
    IFluidHandler uiTank(int index);
}
```

`MyMachineBlockEntity.java` stores the tank, marks changes for saving and persists its
contents. It has no machine item slots; the screen can still show the player's inventory.

```java
package com.example.demo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;

public class MyMachineBlockEntity extends BlockEntity implements MyTankHost {
    private final MyFluidStorage coolant = new MyFluidStorage(this::setChanged);
    private final ItemStackHandler items = new ItemStackHandler(0);

    public MyMachineBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public IFluidHandler uiTank(int index) {
        if (index != 0) throw new IllegalArgumentException("Unknown tank: " + index);
        return coolant;
    }

    @Override
    public IItemHandlerModifiable screenItems() {
        return items;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("coolant", coolant.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        coolant.readFromNBT(registries, tag.getCompound("coolant"));
    }
}
```

Your block-entity factory constructs this class using the registered `demo:machine`
type, position and block state. Your existing block's interaction handler can open it
with `ScreenRuntime.open(player, blockEntity)` (`gui.runtime.ScreenRuntime`), or use
the `mmdlib:open_menu` callback for an MMDLib-defined block. See [opening screens](gui-screens.md).

The UI accesses storage through `MyTankHost`, so it does not require a block fluid
capability. To support pipes too, separately register `Capabilities.FluidHandler.BLOCK`
for your machine type, returning the appropriate handler for each side. This example
does not add automation side rules or expose storage automatically.

## 2. Own the snapshots and server clicks

`MyFluidTankElement.java` inherits dimensions, numeric fluid display and ordinary click
dispatch. It replaces every inherited hook that would otherwise use
`ScreenHost.screenFluids()` and MMDLib's `FluidStorage`.

```java
package com.example.demo;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.ElementSnapshot;
import zone.moddev.mc.lib.gui.model.Orientation;
import zone.moddev.mc.lib.gui.model.ResourceBinding;
import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

public class MyFluidTankElement extends FluidTankElement {
    public MyFluidTankElement(Common common, int tank) {
        super(common, tank, Orientation.VERTICAL, 54);
    }

    private IFluidHandler storage(ScreenHost host) {
        return ((MyTankHost) host).uiTank(tank());
    }

    @Override
    public void validateHostType(Class<?> type) {
        if (!MyTankHost.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Requires MyTankHost");
        }
    }

    @Override
    public void validateHost(ScreenHost host) {
        if (!(host instanceof MyTankHost)) {
            throw new IllegalArgumentException("Requires MyTankHost");
        }
        if (storage(host).getTanks() != 1) {
            throw new IllegalArgumentException("uiTank must expose exactly one tank");
        }
    }

    @Override
    public Optional<ResourceBinding> resourceBinding() {
        // Our tank is outside MMDLib's indexed resource bindings.
        return Optional.empty();
    }

    @Override
    public Optional<ElementSnapshot> snapshot(ScreenHost host) {
        var storage = storage(host);
        return Optional.of(new ElementSnapshot(
            storage.getFluidInTank(0).copy(),
            storage.getTankCapacity(0)
        ));
    }

    @Override
    public List<Component> tooltip(ScreenHost host, ItemStack carried) {
        var storage = storage(host);
        var fluid = storage.getFluidInTank(0);
        return List.of(
            Component.translatable("demo.gui.tank.title"),
            fluid.isEmpty()
                ? Component.translatable("demo.gui.tank.empty")
                : fluid.getHoverName(),
            Component.translatable(
                "demo.gui.tank.contents", fluid.getAmount(), storage.getTankCapacity(0)),
            Component.translatable("demo.gui.tank.accepts"),
            Component.translatable("demo.gui.tank.controls")
        );
    }

    @Override
    public boolean interact(ScreenClick click) {
        // Called on the server after the menu validates the action.
        if (!(click.menu().blockEntity instanceof MyTankHost host)) return false;

        var carried = click.menu().getCarried();
        if (carried.isEmpty() || FluidUtil.getFluidHandler(carried).isEmpty()) return false;

        var inventory = click.player().getCapability(Capabilities.ItemHandler.ENTITY);
        if (inventory == null) return false;

        var storage = storage(host);
        int limit = click.shift() ? Integer.MAX_VALUE : 1000;
        var result = click.button() == 0
            ? FluidUtil.tryEmptyContainerAndStow(
                carried, storage, inventory, limit, click.player(), true)
            : FluidUtil.tryFillContainerAndStow(
                carried, storage, inventory, limit, click.player(), true);

        if (result.isSuccess()) {
            click.menu().setCarried(result.getResult());
        }
        return true;
    }
}
```

Left-click empties the cursor container into the tank; right-click fills it from the
tank. A normal click transfers up to 1,000 mB, and Shift removes that amount limit.
The helpers handle one cursor container per click, including stowing its result from
a stack of containers. Actual transfer amounts depend on the container's capabilities
and your storage's rules; an indivisible bucket cannot transfer a partial bucket.

The server reads the live cursor and storage, rather than trusting client-supplied
fluid amounts. The storage's change callback handles persistence; MMDLib samples the
new copied `FluidStack` and capacity for the open menu. No custom packet or value codec
is needed. Replacing the transfer helper calls with your own storage operations is
also possible; keep validation and mutation on the server.

Returning no `ResourceBinding` opts out of automatic matching to MMDLib fluid resource
groups. The host validates the custom tank index instead. The element stores only its
definition, not a block entity or live handler. Its current constructor adds no extra
configuration beyond the inherited fields; if you add fields later, include them in
the element's deterministic equality/hash/text representation for screen fingerprints.

## 3. Declare and register the XML tag

`MyElements.java` is common code and contains no client references:

```java
package com.example.demo;

import javax.xml.namespace.QName;
import zone.moddev.mc.lib.gui.ElementType;
import zone.moddev.mc.lib.gui.load.ScreenAttribute;

public final class MyElements {
    public static final ElementType<MyFluidTankElement> FLUID_TANK =
        ElementType.xml(
            MyFluidTankElement.class,
            new QName("https://demo.example/gui", "fluid-tank"),
            xml -> new MyFluidTankElement(xml.common(), xml.integer("tank"))
        )
        .attribute("tank", ScreenAttribute.required("xs:nonNegativeInteger"))
        .build();

    private MyElements() {}

    public static void register() {
        FLUID_TANK.register();
    }
}
```

Call `MyElements.register()` **once from your existing mod constructor, on both
physical sides**, before MMDLib freezes XML declarations during loading. Do not defer
it to client setup. `ElementType` forwards to the existing `ScreenTags` registry.
The namespace URI identifies your XML vocabulary; it is not fetched from the internet.

## 4. Attach client-only rendering

Place `MyClientElements.java` in a client package. The event subscriber below registers
automatically for mod ID `demo`; do not also call it from common code.

```java
package com.example.demo.client;

import com.example.demo.MyElements;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import zone.moddev.mc.lib.gui.client.ElementClient;

@EventBusSubscriber(modid = "demo", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MyClientElements {
    private MyClientElements() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ElementClient.of(MyElements.FLUID_TANK)
            .renderer((element, context) -> {
                var g = context.graphics();
                var r = context.bounds();
                int border = context.hovered() ? 0xffffcc55 : 0xff555555;

                g.fill(r.x(), r.y(), r.right(), r.bottom(), border);
                g.fill(r.x() + 1, r.y() + 1,
                       r.right() - 1, r.bottom() - 1, 0xff181818);

                int filled = (int) Math.round((r.height() - 4) * context.ratio());
                g.fill(r.x() + 2, r.bottom() - 2 - filled,
                       r.right() - 2, r.bottom() - 2, 0xff36bcd4);
            })
            .register());
    }
}
```

This replaces the inherited glass gauge with a solid cyan bar and a gold hover border.
`context.ratio()` calls the element's inherited fluid amount/capacity calculation using
synchronized values. The renderer never reads the machine's live storage.

No `.behavior(...)` attachment is needed: ordinary inherited/default click handling
already sends validated clicks to `interact()`. For custom dragging, scrolling or
keyboard input, attach a fresh behavior factory with `.behavior(MyTankBehavior::new)`.
Omitted behaviors and rendering passes preserve normal inheritance. Rendering and input
still use the existing registries; `ElementClient` only bundles the registrations.

## 5. Add the screen and translations

Create **`src/main/resources/mmdlib/screen/machine.xml`** in the `demo` mod:

```xml
<screen xmlns="https://moddev.zone/mmdlib/screen"
        xmlns:demo="https://demo.example/gui"
        title="demo.gui.machine"
        block-entity="com.example.demo.MyMachineBlockEntity">
    <demo:fluid-tank id="coolant" tank="0"/>
</screen>
```

The file produces screen ID `demo:machine`, which must match the registered
block-entity type ID. It lives under the mod's root `mmdlib/screen/`, not under
`assets/` or `data/`. The `demo:` prefix selects your XML namespace; `tank="0"`
selects `MyTankHost.uiTank(0)`. This custom tag has a fixed vertical length of 54
pixels from its constructor. It needs no `value`, `max` or `interaction` attributes.

Add **`src/main/resources/assets/demo/lang/en_us.json`** (merge with existing keys):

```json
{
  "demo.gui.machine": "Coolant Machine",
  "demo.gui.tank.title": "Coolant",
  "demo.gui.tank.empty": "Empty",
  "demo.gui.tank.contents": "%s / %s mB",
  "demo.gui.tank.accepts": "Accepts water only",
  "demo.gui.tank.controls": "Left: empty container. Right: fill container. Shift: remove transfer limit."
}
```

## Check it in your mod

1. Open the registered machine's screen and verify the custom cyan gauge and tooltip.
2. Pick up a water bucket and left-click the gauge. The tank gains 1,000 mB and the
   cursor/container result updates. Right-click with an empty bucket to retrieve it.
3. Try a lava bucket: storage refuses it. Check full-tank and empty-tank cases too.
4. Save and reopen the world; the tank retains its contents through the NBT hooks.
5. Open the screen from two clients: both should receive the server's updated contents.

Continue with the [element declaration and rendering API](gui-screens.md) for additional
render passes, parent tooltip rows, custom actions and XML container contracts.
