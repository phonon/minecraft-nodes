# Prefer Handles/IDs over References

**tl;dr prefer storing handles like `TerritoryId` instead of direct
`Territory` pointer references. Avoid forming pointer dependencies.**

## Problem: Cancerous dependency web
Territories in particular are intended to be swapped out in real time
in order to easily modify resource attributes. e.g. Players build
structures in their territory which rewards new resource node.

To handle swapping out or modifying territories we have two options:
1.  **Make territories fully mutable, modify or re-build resource
    properties in real time.**
    - **Pros:** "Non-invasive". Pointer stability.
    - **Cons:** Need to enforce that only certain systems actually modify.
        Can potentially become difficult to enforce.
2.  **Keep territories mostly immutable. Re-create entire territory
    when resources need to be updated.**
    - **Pros:** Systems cannot modify territory properties.
        Easier to reduce surface area for unknown state modifications
        (not guaranteed, but easier). References to object have
        stable properties (though not critical since codebase is
        currently single-threaded).
    - **Cons:** Re-creating objects requires updating all pointer
        dependencies in other objects. More difficult to enforce
        memory safety.

We opt for 2nd option of mainly immutable objects and re-creating the
object when properties need to change. This architecture however makes
holding direct references difficult since all these need to be updated
when the object is re-created. This creates bigger surface area for
memory leaks:
```
Town {
    capital: Territory
    territories: ArrayList<Territory>
}

Territory {
    owner: Town
    neighbors: List<Territory>
}
```
If a territory is swapped out, all references to old `Territory` object
must be replaced in the `Town` and `Territory`. The alternative
representation is central storage of objects, while enforcing only
handle references:
```
TownId = int

Town {
    capital: TerritoryId
    territories: ArrayList<TerritoryId>
}

TerritoryId = int

Territory {
    owner: TownId
    neighbors: List<TerritoryId>
}

// central storages
towns = Map<TownId, Town>
territories = Map<TerritoryId, Territory>
```

## Handles over Pointers
-   **Pointer stability through indirection.** This adds a layer of 
    indirection to provide pointer stability. A territory can be
    swapped in without affecting any other state since all
    access must use the central storage. This assumes overall world
    structure is unchanged and ids remain the same. Client must
    ensure these invariants are upheld.
-   **Additional indirection cost.** However, indirection adds
    a map lookup to get the underlying object. This may not be
    great in a hot path. Need to profile/benchmark if this
    becomes a concern. Likely not an issue since Nodes does not
    run big tasks each server tick.


## When Handles
Prefer handles for
- Long lived storage with interlocked dependencies (e.g. Town-Territory
  object dependencies). In this case, Territories are intended to be
  re-created in real time, while Towns are not, so use TerritoryId,
  but TownId not as necessary.

Ok direct references:
- Hot paths (getting Territory from TerritoryChunk, very common)
- Short lived objects that are not stored (e.g. events)

## Type safe handles
Kotlin allows type safe wrappers around basic identifiers with [value
classes](https://kotlinlang.org/docs/inline-classes.html):
```kotlin
@JvmInline
value class TerritoryId(private val id: Int)
```
- <https://kotlinlang.org/docs/inline-classes.html>
- <https://typealias.com/guides/inline-classes-and-autoboxing/>

## See Also
<https://floooh.github.io/2018/06/17/handles-vs-pointers.html>
