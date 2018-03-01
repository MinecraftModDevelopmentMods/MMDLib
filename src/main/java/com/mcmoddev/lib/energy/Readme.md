# The Energy System Wrappers

## Main Concepts

#### IEnergySystem

{needs java doc link}

A wrapper interface that allows one to manipulate various energy
systems in a generic way.

Its main goal is to provide a way to wrap a TileEntity or an ItemStack
into such a wrapper. Called an **IEnergyAdapter**.

#### IEnergyAdapter

{needs java doc link}

This one allows direct manipulation of a TileEntity or ItemStack that
contains, provides and/or consumes energy.

The energy is put/taken from a storage using a generic value wrapper
called **IEnergyValue**.

#### IEnergyValue

{needs java doc link}

This one holds an exact amount of energy and also provides ways to
add, subtract and compare with other energy value instances.

#### IGenericEnergyStorage

{needs java doc link}

This is the interface used by generic energy storages. An energy
container that can store energy and provide capabilities for
manipulation from various compatible energy systems. 

#### EnergySystemRegistry

{needs java doc link}

The main registry of all the available energy systems registered
with MMDLib.


## Usage Scenarios

#### Handle energy interactions with a TileEntity

Assuming that your energy system of choice is Forge Energy.
And assuming you are interested in the TOP side of the target tile entity.
 
```java
// input: TileEntity tile;
// this will try to create an adapter from any energy system compatible with the base one provided.
IEnergyAdapter adapter = EnergySystemRegistry.findAdapter(EnergySystemRegistry.FORGE_ENERGY, tile, EnumFacing.UP);
if (adapter != null) {
    // an adapter was found!
    // .. use the adapter to take/store energy from the tile entity's storage
} 
```

#### Handle energy interactions with an ItemStack

Assuming that your energy system of choice is Forge Energy.
 
```java
// input: ItemStack stack;
// this will try to create an adapter from any energy system compatible with the base one provided.
IEnergyAdapter adapter = EnergySystemRegistry.findAdapter(EnergySystemRegistry.FORGE_ENERGY, stack);
if (adapter != null) {
    // an adapter was found!
    // .. use the adapter to take/store energy from the item stack's storage
} 
```

#### Store energy inside your TileEntity and provide capabilities for interactions with other tile entities.

```java
class MyTileEntity extends TileEntity implements ICapabilityProvider {
    private ForgeEnergyStorage storage = new ForgeEnergyStorage(50000);
    
    public boolean hasCapability(final Capability<?> capability, @Nullable final EnumFacing facing) {
        return this.storage.hasCapability(capability, facing);
    }
    
    public <T> T getCapability(final Capability<T> capability, @Nullable final EnumFacing facing) {
        return this.storage.getCapability(capability, facing);
    }
}
``` 

This will ensure capability compatibility with all compatible energy systems registered with MMDLib.
