# Territory + Resource Design Specification

**Date:** 2022-6-4

## Territories
Territories are the core unit in Nodes. These are a group of
chunks with some "resource" properties attached. Territories
also form a graph with adjacent territories. Below is a
simplified high-level example of a territory.
**This design document specifies how territories and resources
are defined and loaded into the Nodes world.**
```
Territory {
    // fixed properties
    id: 420,
    chunks: [(x0, z0), (x1, z1), (x2, z2)]
    neighbors: [TerritoryId(69), TerritoryId(9000)]

    // resource-defined properties
    resources: ["town", "diamond"]
    income: [DIAMOND],
    crops: [WHEAT, CARROT]
    animals: [PIG]
    ore: [DIAMOND, GOLD]
    customProperties: {
        manpower: 5,
    }

    // mutable properties
    owner: TownId?,
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
-   Territory fixed properties are immutable properties created in
    world editor. Resource properties in a territory can be changed by
    re-compiling a territory with new resources or after resources are
    modified. Mutable properties are mainly town owner.
    *(TODO: consider removing all mutable properties.)*
-   Territory fixed and resource properties should be immutable after
    territory is created. If these need to change, create a new Territory
    object.
-   Territory resource properties are "fixed function": these are hard-coded
    properties for the most common game behaviors.
-   External addons can add additional functionality using the
    `customProperties: Map<String, Any>`.

## Resources are groups of "attribute" functions: `A(T) -> T`
Resources are composed of a list of `ResourceAttribute` interface 
objects. These are functions applied to a territory to create a
modified territory (e.g. to add resources, or apply modifiers).
```
ResourceAttribute {
    apply(t: Territory) -> Territory
}

Resource {
    attributes: List<ResourceAttribute>
    priority: 69
}
```

A single territory's "compilation" process is shown below:
1.  Start with a blank territory with its fixed world properties.
2.  Sort territory resources by priority (e.g. so that modifiers like bonus
    ore percent is applied after base ore rates are added).
3.  Foreach resource, foreach attribute, apply the attribute function
    to the territory. The `Territory.apply` wrapper will enforce that the
    resource attribute functions do not override territory's fixed world
    properties.
```
Territory {
    ...

    apply(attribute: ResourceAttribute) -> Territory {
        modified = attribute.apply(this)

        Territory {
            // copy fixed properties + mutable properties
            ...
            // use modified territory resource properties
            ...
        }
    }
}

resources = [
    Resource { [BonusOreAttribute], priority: 69 },
    Resource { [IncomeAttribute, OreAttribute], priority: 0 },
]

terr = Territory.create(id, resources)

for resource in sort(resources, key = resource.priority()):
    for attribute in resource.attributes:
        terr = terr.apply(attribute)
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
immutable `Resource` object definitions.
```
ResourceBuilderLibrary {
    resources: Map<String, ResourceBuilder>

    build() -> Map<String, Resource>
}

ResourceLoader {
    apply(resources: ResourceBuilderLibrary, json: Json) -> ResourceBuilderLibrary
}

ResourceBuilder {
    attributes: MutableList<ResourceAttribute>
    ...
    // other mutable resource intermediate state
    ...
    
    build() -> Resource
}

// load resources
loaders: List<ResourceLoader> = [
    ResourceLoaderBuiltin,
    ResourceLoaderAddon1,
    ResourceLoaderAddon2,
]

jsonResourceLibrary = loadJsonResourceSection("world.json")

resources = ResourceBuilderLibrary()

for resourceLoader in loaders:
    resources = resourceLoader.apply(resources, jsonResourceLibrary)
```

## External Resource Loader `.jar` Files
This system must support loading `ResourceLoader` objects from
other `.jar` files in `nodes/addons/resources`. This allow external
addons to write custom resources (e.g. resources with more customized
behavior or that modify territory `customProperites`).


## Resource + Territory Calculation Order:
1.  Load `ResourceLoader`s classes from addon `.jar` files in 
    `nodes/addons/resources`.
2.  Load `world.json` into json resources and territories objects.
3.  For each resource loader
    and add its attributes based on json keys.
4.  For each resource loader, load resource attribute definitions from
    json and compile resources.
5.  Load territories from json with blank resource properties.
6.  For each territory: sort territory resources, run resource attribute
    modifiers. Then insert territories into World.
7.  Apply territory neighbor modifiers onto adjacent territories.
    **Neighbor order is arbitrary so neighbor behaviors must not have any order dependence.**


## Reloading Resources/Territories
-   Reloading resources requires repeating resource loader steps and all
    territory re-calculations. **This is expensive (full map re-calculation).**
-   Reloading territories requires re-calculating a territory, then
    recalculating all neighbors (to propagate neighbor bonuses).
    This is relatively cheap.


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