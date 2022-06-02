# High level
-   The async tasks do not have proper synchronization. There isn't
    proper locking to make sure async stuff runs properly, TODO.
    Check filesystem saving sync as well.
-   No real API. Most functionality is attached to the Nodes object
    with intent that this basically acts as API. But not well defined
    and subject to change.
-   Nodes world state is global variable, so only supports single world.
    E.g. no separate nodes map for overworld + nether.
    This is likely not to change since goal is only single map/world.
-   No API for custom node/territory properties.
-   Cannot `/nodes reload` world. Inconvenient. TODO.
-   Get rid of java.io.File, swap to nio


# Nodes
-   Add node tiers with configurable resource boost.
-   Fix moving stuff back into town nodes income inventory
-   Consider attaching node income into income chests/inventories on
    territories instead of global town income storage. Force players
    to build supply networks to gather resources from their territories.
    Adds rp/territory management, potentially cancer to manage tho.
    So configurable setting.
-   Overhaul node resources to be an "Attributes"-style system.
    Resources are just "attribute" modifiers on a territory. This can be
    both resources (ore, farm rate, etc.), multipliers (e.g. Tiers, 1.2x resource),
    and markers (modifier with no effect). These must be sorted by a priority
    parameter so map designers have control over multiplier modifiers.
    This makes resources system more flexible to allow other modifiers
    like "Tiers" (resource multipliers) or "Refinery" marker resources,
    without hard-coding behavior into the Territory itself.
-   Don't store direct object references. Use IDs/handles. This allows
    pointer stability and reduces chance of memory leaks.
    This brings cost of additional reference indirection, but likely does
    not matter since these lookups should not be really frequent
    in hot paths...will need to benchmark.

# Minimap
-   Port indicator on chunk. Would require a minimap api though since ports
    are intended to be a plugin...?
-   Chunks with war flags indicator, e.g. flashing or separate minimap symbol

# War
-   Configurable timer above war claim flag
-   Indicator above war claim flag showing enemy/ally owner whos attacking
-   Claiming incontiguous territories short distance across seazone/wasteland
-   Seazone border claiming penalty only on edge chunks not bordering friendly land
-   Make it take longer to break claims flags so players cant naked greek
    with sheers.
    https://www.spigotmc.org/threads/why-custom-block-breaking-change-block-hardness.531825/#post-4291982
    https://www.spigotmc.org/resources/breaker-2-configurable-breaking-speeds-1-16-1-18.99022/
-   Investigate MultiBlockChange packet to send flag sky beacon blocks?
    Need to do something to reduce fps drop when sky beacon blocks are created.