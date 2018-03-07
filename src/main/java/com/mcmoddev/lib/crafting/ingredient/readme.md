#Ingredients

####Common types of ingredients:
- item (by registration name, by ore dictionary, by ItemStack etc)
- fluid (by name, by FluidStack)
- energy (by system + amount, by IEnergyStack)

###Uses for ingredients
- define a recipe's inputs
- match a recipe's inputs with a machine's inventory
- extracting from a machine's inventory

###Extracting ingredients from inventories (*only special scenarios*)

#####*Fluids* from *Item* inventories
This should use the fluid caps to extract the fluid from the corresponding ItemStack. If required it will leave an empty fluid container behind.

#####*Energy* from *Item* inventories
This should use the energy system's cap to extract energy from the corresponding ItemStack.

#####*Items* from *Fluid* / *Energy* inventories
This will probably not be supported.
This would require some fake item wrappers that could only be used in recipes.
Or else it would create items (*fluid containers*) out of thin air.
 