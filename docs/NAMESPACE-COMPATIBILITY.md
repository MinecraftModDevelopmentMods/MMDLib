# MMDLib Java namespace compatibility

The canonical implementation and public API are now in `zone.moddev.mc.mmdlib`.
The Maven coordinate is `zone.moddev.mc.mmdlib:MMDLib:1.0.0.112021`.
Existing Forge 1.12.2 mods using `com.mcmoddev.lib` can keep their compiled API
references and share the same library state with new consumers. The `mmdlib`
mod ID, registry IDs, resource IDs, configuration keys and integration IDs are
unchanged.

## How the adapter works

The jar manifest loads
`zone.moddev.mc.mmdlib.compat.LegacyNamespaceBootstrap` before dependent mod
classes link, following [Forge's loading-plugin contract](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.12.x/src/main/java/net/minecraftforge/fml/relauncher/IFMLLoadingPlugin.java).
It installs one class transformer: `LegacyNamespaceTransformer`.
An inventory of 500 exact old-to-new class names limits type remapping to the
library's historical API. The transformer rewrites consumer descriptors,
inheritance, annotations, class literals, method handles and generic signatures
to the canonical types. Old class-name string literals are also translated.
Consumer classes keep their own names, including addon classes situated under
the old package root.

This is necessary for compatibility across enums, static fields, subclassing
and events: ordinary subclass wrappers would create different enum and event
types. Relinking gives both namespaces one `MMDLib.instance`, proxy, `Names`
enum, `IntegrationManager.INSTANCE`, event hierarchy and registry state.

Forge harvests annotations before normal class loading. Integration discovery
therefore accepts both old and new `MMDPlugin` annotation names. Legacy plugin
callbacks run against canonical events, with no second plugin initialization.

The historical `asm.ASMPlugin` and its gameplay transformers remain dormant.
The only active loading plugin is the namespace adapter; it does not install
the old horse-armor or fluid-behavior transformations.

## Old source imports and resource factories

The build generates legacy compiler API exports from the canonical class files.
The exports preserve every frozen old class, hierarchy and public/protected
member descriptor, generic signature and constant value. They are included
in the main and deobf jars, allowing an existing source dependency to retain
its `com.mcmoddev.lib` imports while adopting the new Maven coordinate.
New source should use `zone.moddev.mc.mmdlib`.

These exports are compiler contracts. Forge relinks ordinary consumer bytecode
to the implementation before it executes. Their class, field and method
annotations cannot register a duplicate mod, configuration or event subscriber.
The compatibility inventory excludes the new adapter itself.

Five Forge crafting factory exports additionally forward reflection-based
construction to their canonical implementation. This preserves historical
class names supplied in recipe/condition JSON, where a name arrives externally
rather than in transformed consumer bytecode. MMDLib's own resource class
pointers now use the canonical namespace.

Compatibility is exercised in the Forge loading environment. The exports do
not provide a separate library implementation for an ordinary JVM without
Forge's adapter. Arbitrarily assembled reflection names, type-name string
switches and Java serialized-lambda metadata are outside the tested relinking
contract; new reflective metadata should use canonical names. The externally
supplied legacy recipe condition name is covered explicitly by runtime tests.
The general string/serialized-lambda restrictions are also described in
[ASM's ClassRemapper documentation](https://asm.ow2.io/javadoc/org/objectweb/asm/commons/ClassRemapper.html).

## Build and acceptance guards

- `gradle/verification/legacy-api-baseline.json` freezes 500 pre-relocation
  class APIs. `generateLegacyApiExports` fails on any signature or hierarchy
  drift and is required for normal `classes` and `processResources`.
- `verifySourceCompatibility` checks the original 431 production files after
  normalizing relocation, version metadata, resource class pointers and the
  two annotation-discovery queries. The baseline includes reviewed comment
  corrections; executable gameplay code remains unchanged.
- `compileLegacyRuntimeTestJava` builds a consumer against the checksum-pinned
  published `1.0.0-rc2.36` library, without canonical implementation output on
  the compiler classpath. `compileLegacySourceTestJava` builds that same source
  against the new exports.
- Unit tests check exact alias coverage, consumer remapping, addon classes,
  absent duplicate runtime registration annotations and all five factory
  forwarders. The bootstrap is required to install only the namespace adapter.
- Packaged client/server probes check legacy constructors, parameter and
  return types, static singleton/enum identity, subclassing, interfaces,
  lambdas, old plugin annotations, old event subscribers and an externally
  supplied old condition-factory class name. Acceptance markers require
  `legacy-api=passed` and the canonical namespace.
- The unchanged published Base Metals fixture runs alongside those consumers
  and the new MMDLib jar. Optional integration profiles use original pinned
  runtime jars; none is bundled in the release.

Normal MMDLib development and Eclipse launches explicitly load the bootstrap
using `fml.coreMods.load`; production jars use the manifest. If integrating the
library as loose class directories in a separate Forge development workspace,
load the same bootstrap before legacy consumer classes. The main and deobf
jars contain its alias inventory and legacy compiler contracts.

See the [build guide](../gradle/README.md) for commands and the
[qualification receipt](FG7-MIGRATION.md) for the release candidate's checksums
and runtime results.
