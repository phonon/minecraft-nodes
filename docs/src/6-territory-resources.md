# Territory + Resource Design Specification

**Date:** 2022-6-19


## Territories
Territories are the core unit in Nodes. These are a group of
chunks with some "resource" properties attached. Territories
also form a graph with adjacent territories. Below is a simplified
high-level example of a territory. **This design document describes
how territories and resources are loaded and created in the Nodes engine.**
```
Territory {
    // fixed structural properties
    id: 420,
    chunks: [(x0, z0), (x1, z1), (x2, z2)]
    neighbors: [TerritoryId(69), TerritoryId(9000)]

    // resource properties
    resources: ["town", "diamond"]
    income: [DIAMOND],
    crops: [WHEAT, CARROT]
    animals: [PIG]
    ore: [DIAMOND, GOLD]
    customProperties: {
        manpower: 5,
    }
}
```

**Properties:**
-   Territory is a set of chunks. Chunks must map to a single territory.
    **No territories should share chunks.**
-   Territories form a graph with edges between territories bordering
    each other. The `neighbors` objects contains just territory ids
    to avoid territory pointer graph dependencies. This makes it easier
    to swap in / recreate territory objects on the fly (so long as overall
    world territory graph unchanged).
-   Territory fixed and resource properties should be immutable after
    territory is created. If these need to change, create a new Territory
    object.
-   Territory resource properties are "fixed function": these are hard-coded
    properties for the most common game behaviors.
-   External addons can add additional functionality using the
    `customProperties: Map<String, Any>`.


## Territory creation process
Territory creation/initialization itself can be expensive (due to
creating potentially expensive data structures during initialization).
Our goals:
-   Territory created is immutable
-   Multiple passes of resource node modification by main plugin
    and external addon plugins before creating final ResourceNode.
-   Territory resource graph neighbor-neighbor interactions.
    (e.g. neighbor bonuses).
```
           [world.json]
                |_____________________________________
                |                                    |
                v                                    v
           Territories                           Resources
              Json                                 Json
                |____________________                |
                |                   |                |
                v                   v                |
    territoriesToBuild:      territoryResources:     |
    Map<Id, FixedProperties>  Map<Id, List<String>>  |
                |                   |           _____v______
                |                   |           | Default  |
                |                   |           | Resource |
                |                   |           |  Loader  |
                |                   |           |__________|
                |                   |                |
                |                   |           _____v______
                |                   |           | External | (Can be
                |                   |           | Resource |  multiple
                |                   |           |  Loaders |  external
                |                   |           |__________|  loaders)
                |                   |                |
                |                   |               ...
                |                   |                |
                |                   |                v
                |                   |   resources: Map<String, ResourceNode>
                |                   |                |
                |                   |                |
                |            _______v_________       |
                |           | Build Territory |      |
                |           | Resource Graph  |<-----|
                |           |_________________|
                |                   |
                |                   v
                |             resourceGraph:
                |            Map<Id, Resources>
                |                   |
                |            _______v_________
                |           | Graph           | (e.g. neighbor-neighbor
                |---------->| Message passing |  modifiers)
                |           |_________________|
                |                   |
                |                   v
                |           finalResourceGraph:
                |           Map<Id, Resources>
                |                   |
                |        ___________v_________________________
                |       | Combine territory fixed properties | 
                |------>| and resources and compile          | 
                        |____________________________________|
                                    |
                                    v
                                territories:
                             Map<Id, Territory>
```


## Territory Resources Compilation

**Resources are groups of "attribute" functions: `A(T) -> T`**

Resource nodes are composed of a list of `ResourceAttribute` interface 
objects. These are functions applied to a `TerritoryResources` to create a
new `TerritoryResources` (e.g. to add resources, or apply modifiers).
```
TerritoryResources {
    income: List<Item>,
    crops: List<Crop>
    animals: List<Animal>,
    ore: List<Item>,
    customProperties: Map<String, Any>,
}

ResourceAttribute {
    apply(t: TerritoryResources) -> TerritoryResources
}

ResourceNode {
    attributes: List<ResourceAttribute>
    priority: 69
}
```

A single `TerritoryResources`'s "compilation" process is shown below:
1.  Start with a blank `TerritoryResources` (or default properties).
2.  Sort a list of resource nodes by priority (e.g. so that modifiers like bonus
    ore percent is applied after base ore rates are added).
3.  Foreach resource node, foreach attribute, apply the attribute function
    to the `TerritoryResources`.
```
t = TerritoryResources.default()

resources = [
    ResourceNode { [BonusOreAttribute], priority: 69 },
    ResourceNode { [IncomeAttribute, OreAttribute], priority: 0 },
]

for r in sort(resources, key = resource.priority()):
    for attribute in r.attributes:
        t = t.apply(attribute)
```


## Resource Attribute Priority
Default in nodes plugin:
- **Regular properties (income, crops, ore, etc.):** 0
- **Bonus modifiers (+10% income, +10% ore, etc.):** 50
- **Neighbor modifiers (+10% neighbor ore, etc.):** 100


## Resource Loaders and Addons
`ResourceLoader` interface loads json resource object tree into
resource attributes. Intermediate `ResourceBuilder` state is passed
through all loader systems before finishing compiling into
immutable `ResourceNode` object definitions.
```
ResourceBuilder {
    attributes: List<ResourceAttribute>

    build() -> ResourceNode
}

ResourceBuilderLibrary {
    resources: Map<String, ResourceBuilder>

    build() -> Map<String, ResourceNode>
}

ResourceLoader {
    apply(resources: ResourceBuilderLibrary, json: Json) -> ResourceBuilderLibrary
}

// load resources
loaders: List<ResourceLoader> = [
    ResourceLoaderBuiltin,
    ResourceLoaderAddon1,
    ResourceLoaderAddon2,
]

jsonResourceLibrary = loadJsonResourceSection("world.json")

resourcesToBuild = ResourceBuilderLibrary()

for resourceLoader in loaders:
    resourcesToBuild = resourceLoader.apply(resourcesToBuild, jsonResourceLibrary)

resources = resourcesToBuild.build()
```

## External Resource Loader `.jar` Files
This system must support loading `ResourceLoader` objects from
other `.jar` files in `nodes/addons/resources`. This allow external
addons to write custom resources (e.g. resources with more customized
behavior or that modify territory `customProperites`).


## Reloading Resources/Territories
-   Reloading resources requires repeating resource loader steps and all
    territory re-calculations. **This is expensive (full map re-calculation).**
-   Reloading territories requires re-calculating a territory, then
    recalculating all neighbors (to propagate neighbor bonuses).
    **This requires recalculating Territory resources for reloaded territories,
    neighbors, AND neighbors' neighbors (two edges away).** This is to make
    sure neighbors' neighbors modifiers are all calculated. But only direct
    neighbors would be updated in this process.


## Why No `TerritoryAttribute`?
Currently territories are fixed functionality with an additional 
`customProperties<String, Any>` map for implementing external features:
```
Territory {
    income: [DIAMOND],
    crops: [WHEAT, CARROT]
    animals: [PIG]
    ore: [DIAMOND, GOLD]
    customProperties: {
        manpower: 5,
    }
}
```

An alternative would be to decompose territory resource properties as
a set of `TerritoryAttribute` properites, similar to resources.
The territory above would become:
```
Territory {
    attributes: [
        IncomeAttribute { [DIAMOND] }
        CropsAttribute { [WHEAT, CARROT] },
        AnimalsAttribute { [PIG] },
        OresAttribute { [DIAMOND, GOLD] },
        ManpowerAttribute { 5 },
    ]
}
```
While this is more flexible, it incurs more development and runtime
cost for checking which attributes exist in a territory. For my 
targeted use case, the majority of required territory functionality
is just income, crops, animals, and ore. The flexibility in
the above "Entity-Component" style of territory did not seem worth it.


### **Fixed Function Territories:**
**Pros:**
-   Development easier for main use cases (less checks for
    attribute existence).
-   Less runtime cost for checking attribute existence in
    main use cases.

**Cons:**
-   External addons must share a `customProperties<String, Any>`
    which can be annoying to use and unsafe.


### **Attribute Based Territories:**
**Pros:**
-   Well defined territory API, allows more external addon
    flexibility.
-   Lower memory usage: most territories do not need data
    structures for all functionalities.

**Cons:**
-   Runtime and development cost for checking which attributes
    exist in territories.