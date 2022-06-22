# Resource Nodes

Territory resources are defined by **resource nodes**. This allows
unique resources in different regions of the world (to facilitate
trade and economic differentiation). Territories have four main
resource components:
- **Income**: items/blocks given on a periodic income schedule
- **Ore**: item drop rates when mining stone
- **Crops**: farm crops that can grow in a region and their growth speed
- **Animals**: animals that can breed in a region and their breed success rate

Territories contain multiple **Resource nodes** which contribute to
the final territory resource components.

Each resource node must have a unique name to identify it (e.g. **wheat** or **iron**).


## Format
Resource nodes are described by JSON objects. Below is an example **with all possible fields.** Only `priority` and `cost` are required, other field are all optional. The next sections detail each component.
```json
{
    "icon": "gold_ingot",
    "priority": 50,
    "cost": {
        "scale": 1.4,
        "constant": 10
    },

    "income": {
        "gold_ingot": 64,
        "wheat": 32,
        "diamond": 0.5,
        "spawn_egg_cow": 1,
        "spawn_egg_sheep": 0.5
    },
    "ore": {
        "coal": 0.5,
        "gold_ore": [0.2, 2, 4]
    },
    "crops": {
        "wheat": 1.0,
        "potato": 0.5,
        "pumpkin": 0.8
    },
    "animals": {
        "sheep": 1.0,
        "cow": 1.0,
        "horse": 0.5
    },

    "income_total_multiplier": 1.25,
    "income_multiplier": {
        "diamond": 1.5,
        "spawn_egg_cow": 2.0
    },
    "ore_total_multiplier": 1.25,
    "ore_multiplier": {
        "gold_ore": 1.5
    },
    "crops_total_multiplier": 1.25,
    "crops_multiplier": {
        "wheat": 1.5,
        "potato": 1.5
    },
    "animals_total_multiplier": 1.25,
    "animals_multiplier": {
        "cow": 1.5,
        "horse": 2.0
    },

    "neighbor_income": {
        "gold_ingot": 64,
        "diamond": 0.5,
        "spawn_egg_cow": 1,
    },
    "neighbor_ore": {
        "coal": 0.5,
        "gold_ore": [0.2, 2, 4]
    },
    "neighbor_crops": {
        "wheat": 1.0,
        "potato": 0.5,
        "pumpkin": 0.8
    },
    "neighbor_animals": {
        "sheep": 1.0,
        "cow": 1.0,
        "horse": 0.5
    },

    "neighbor_income_total_multiplier": 1.25,
    "neighbor_income_multiplier": {
        "diamond": 1.5,
        "spawn_egg_cow": 2.0
    },
    "neighbor_ore_total_multiplier": 1.25,
    "neighbor_ore_multiplier": {
        "gold_ore": 1.5
    },
    "neighbor_crops_total_multiplier": 1.25,
    "neighbor_crops_multiplier": {
        "wheat": 1.5,
        "potato": 1.5
    },
    "neighbor_animals_total_multiplier": 1.25,
    "neighbor_animals_multiplier": {
        "cow": 1.5,
        "horse": 2.0
    }
}
```

All Minecraft item names (used in `income` and `ore` keys) must be in all
lowercase with spaces replaced by _.


## icon
**Format**: `"icon": "icon_name"`

Name of the icon displayed in the dynmap editor. Most icons are Minecraft
item names. Animal icons are in format `mob_name`, such as `mob_cow` or `mob_sheep`.
This has no in-game behavior, this is purely for web editor/viewer.


## priority (required)
**Format**: `"priority": Number`

This is order that resource node properties are applied to a territory.
Lower priority resource nodes are applied first


## cost (required)
**Format 1**: `"scale": Number`  
**Format 2**: `"const": Number`
```
"cost": {
    "scale": 1.4,
    "constant": 10
}
```
This defines the resource power cost applied to the territory.
[See territory cost model.](./4-1-territories.md#territory-cost)

"scale" (or **rs**) is the power cost per chunk. "const" (or **rc**) is
the constant power cost for having this resource.

**total cost = base + rc + rs * a * chunks**

- **base** = base cost
- **chunks** = size of territory
- **a** = fixed scale factor per chunk (so larger territories cost more)
- **rs** = resource scale factor (e.g. shitty resource wheat rs = 1, rare resource diamond rs = 2)
- **rc** = resource constant factor

For territories with multiple resources, **rs** and **rc** are the total
from all resources: **rs = rs1 * rs2 * ...** and **rc = rc1 + rc2 + ...**.

Resource scale factors enforce that a large territory with a rare resource
is more expensive than small territory with same resource


## income
**Format 1**: `"item_name": amount`  
**Format 2**: `"spawn_egg_monster": amount`
```
"income": {
    "gold_ingot": 64,
    "spawn_egg_cow": 1
}
```
These define items/blocks given to towns during every income cycle
(found in town income chest using `/town income`). Each `item_name` is a
normal Minecraft item name. To give spawn eggs, the name must be
`"spawn_egg_monster"` (e.g. `"spawn_egg_sheep"`).

The amount can either be a `Double` <1.0 or `Integer` >1, with following effects on income:
- **Amount <1.0**: Performs a random roll, gives 1 item if `random() < amount`
- **Amount >1.0**: Gives the amount as an integer (casts to `Integer`)

For occupation taxes, fractional amounts given to the occupier
are always rounded up using `Math.ceil()`.
So if the income amount <= 1, for taxes it will always go to the occupier.


## ore
**Format 1**: `"item_name": drop_rate`  
**Format 2**: `"item_name": [drop_rate, min_amount, max_amount]`
```
"ore": {
    "coal": 0.5,
    "gold_ore": [0.2, 2, 4]
}
```
These define probabilities for items to drop when mining stone or
other blocks set in config (often referred to as
["hidden ore"](https://github.com/DevotedMC/HiddenOre)). 

`drop_rate` is the probability that the item will drop. `min_amount`
and `max_amount` are the min and max items that drop when the event
triggers. The shorthand Format 1 `"item_name": drop_rate` defaults
both `min_amount = max_amount = 1`.


## crops
**Format**: `"crop_type": growth_rate`
```
"crops": {
    "wheat": 1.0,
    "potato": 0.5
}
```
By default, no crops will grow in a territory without adding specific crops
here. An entry here enables crops of that type to grow. The `growth_rate` sets
the probability the crop will grow during normal crop tick. Setting
`growth_rate = 1.0` means normal growth rate. Setting `< 1.0` means
crops will grow slower.

Crops can be any growable block, e.g. wheat, sugar cane, pumpkin, melon, cactus, etc...


## animals
**Format**: `"animal_type": breed_success_rate`
```
"animals": {
    "sheep": 1.0,
    "horse": 0.5
}
```
By default, when animals breed, there will be no child in territories without
animal settings here. An entry here enables animals of that type to breed.
The `breed_success_rate` sets the probability that a child will be created.
Setting `breed_success_rate = 1.0` means normal breeding. Setting `< 1.0` means
some breed events will fail to produce offspring.


## income_total_multiplier
**Format**: `"income_total_multiplier": Number`

Multiplies all territory income values by this number.


## income_multiplier
**Format**: `"item_name": Number`
```
"income_multiplier": {
    "diamond": 1.5,
    "iron_ore": 1.2
}
```
Multiplies specific territory income items by value specified.
e.g. above, `diamond` income gets 1.5x multiplier, `iron_ore` income
gets 1.2x multiplier.


## ore_total_multiplier
**Format**: `"ore_total_multiplier": Number`

Multiplies all territory hidden ore drop probability by this number.


## ore_multiplier
**Format**: `"item_name": Number`
```
"ore_multiplier": {
    "gold_ore": 1.5,
    "coal": 1.25
}
```
Multiplies specific territory hidden ore drop probability by value specified.
e.g. above, `gold_ore` probability gets 1.5x multiplier, `coal` probability
gets 1.25x multiplier. **This does not affect min/max item drop count.**


## crops_total_multiplier
**Format**: `"crops_total_multiplier": Number`

Multiplies all territory crop growth probability by this number.


## crops_multiplier
**Format**: `"crop_type": Number`
```
"crops_multiplier": {
    "wheat": 1.5,
    "potato": 1.25
}
```
Multiplies specific territory crop growth probability by value specified.
e.g. above, `wheat` growth probability gets 1.5x multiplier,
`potato` growth probability gets 1.25x multiplier.


## animals_total_multiplier
**Format**: `"animals_total_multiplier": Number`

Multiplies all territory animal breed success probability by this number.


## animals_multiplier
**Format**: `"animal_type": Number`
```
"animals_multiplier": {
    "cow": 1.5,
    "horse": 2.0
}
```
Multiplies specific territory animal breed success probability by value specified.
e.g. above, `cow` breed success probability gets 1.5x multiplier,
`horse` breed success probability gets 2.0x multiplier.


## neighbor_income
**Format 1**: `"item_name": amount`  
**Format 2**: `"spawn_egg_monster": amount`
```
"neighbor_income": {
    "gold_ingot": 4,
    "spawn_egg_cow": 1,
}
```
Same format as `income`, except this adds values to the territory's neighbor
territories (NOT the territory itself).


## neighbor_ore
**Format 1**: `"item_name": drop_rate`  
**Format 2**: `"item_name": [drop_rate, min_amount, max_amount]`
```
"neighbor_ore": {
    "coal": 0.5,
    "gold_ore": [0.2, 2, 4]
}
```
Same format as `ore`, except this adds values to the territory's neighbor
territories (NOT the territory itself).


## neighbor_crops
**Format**: `"crop_type": growth_rate`
```
"neighbor_crops": {
    "wheat": 0.5,
    "potato": 0.5
}
```
Same format as `crops`, except this adds values to the territory's neighbor
territories (NOT the territory itself).


## neighbor_animals
**Format**: `"animal_type": breed_success_rate`
```
"neighbor_animals": {
    "sheep": 1.0,
    "horse": 0.5
}
```
Same format as `animals`, except this adds values to the territory's neighbor
territories (NOT the territory itself).


## neighbor_income_total_multiplier
**Format**: `"neighbor_income_total_multiplier": Number`

Multiplies all neighbor territory income values by this number.
Only affects neighbor, NOT territory itself.


## neighbor_income_multiplier
```
"neighbor_income_multiplier": {
    "diamond": 1.5,
    "spawn_egg_cow": 2.0
}
```
Multiplies specific neighbor territory income items by value specified.
Same as `income_multiplier` except applies to neighbor territories, NOT
the territory itself.


## neighbor_ore_total_multiplier
**Format**: `"neighbor_ore_total_multiplier": Number`

Multiplies all neighbor territory hidden ore drop probability by this number.
Only affects neighbor, NOT territory itself.


## neighbor_ore_multiplier
**Format**: `"item_name": Number`
```
"neighbor_ore_multiplier": {
    "gold_ore": 1.5,
    "coal": 1.25
}
```
Multiplies specific neighbor territory hidden ore drop probability by
value specified. Same as `ore_multiplier` except applies to neighbor
territories, NOT the territory itself. Only affects probability,
not item drop min/max count.


## neighbor_crops_total_multiplier
**Format**: `"neighbor_crops_total_multiplier": Number`

Multiplies all neighbor territory crop growth probability by this number.
Only affects neighbor, NOT territory itself.


## neighbor_crops_multiplier
```
"neighbor_crops_multiplier": {
    "wheat": 1.5,
    "potato": 1.5
}
```
Multiplies specific neighbor territory crops growth probability by value.
Same as `crops_multiplier` except applies to neighbor territories, NOT
the territory itself.


## neighbor_animals_total_multiplier
**Format**: `"neighbor_animals_total_multiplier": Number`

Multiplies all neighbor territory animal breed success probability by this number.
Only affects neighbor, NOT territory itself.


## neighbor_animals_multiplier
```
"neighbor_animals_multiplier": {
    "cow": 1.5,
    "horse": 2.0
}
```
Multiplies specific neighbor territory animal breed success probability
by value specified. Same as `animals_multiplier` except applies to neighbor
territories, NOT the territory itself.


## Intended Usage
Resource nodes are intended as *composable units.* This means that instead
of making very specific resource nodes for each territory, it's best to make
generic nodes such as a **wheat**, **coal**, **horses**, or **iron** and attach
them to many different territories. Territories are intended to have multiple
resource nodes. Examples of intended usage:

- **Agriculture territory**: this could have **wheat**, **potato** and **sheep** nodes
- **Iron territory**: this could have an **iron** node
- **Mongolian horse steppe**: this could have a **horses** nodes
- **Coal-rich territory**: because resource node names are unique, have several coal nodes: **coal**, **coal2**, **coal3** with similar properties attached to this territory (can have **coal** and **coal2** set ore mining rates while **coal3** simply provides additional fixed income)


## Combined Resource Node Rate Calculations
-   Rates for the same property are summed together.
-   Properties are added together sequentially in order of priority (low to high)
-   Multipliers are applied to properties that exist when it is
    applied. So in general, make resource nodes with multipliers separate
    and with higher priority value.

**Example:**
```
resources = {
    "iron": {
        "priority": 0,
        "ore": { "iron_ore": 0.5 }
    },
    "gold": {
        "priority": 0,
        "ore": { "gold_ore": 0.5 }
    },
    "diamond": {
        "priority": 100,
        "ore:" { "diamond_ore": 0.5 }
    },
    "bonus_ore": {
        "priority": 50,
        "ore_total_multiplier": 1.5
    }
}
```

The sorted order will be `["iron", "gold", "bonus_ore", "diamond"]`
(from sorting by priority low to high). The territory resources is
calculated as:
1.  Apply "iron":
```
resources = {
    ore = {
        iron_ore: 0.5,
    }
}
```
2.  Apply "gold":
```
resources = {
    ore = {
        iron_ore: 0.5,
        gold_ore: 0.5,
    }
}
```
3.  Apply "bonus_ore" (1.5x multipler on all ore):
```
resources = {
    ore = {
        iron_ore: 0.75,
        gold_ore: 0.75,
    }
}
```
4.  Apply "diamond":
```
resources = {
    ore = {
        iron_ore: 0.75,
        gold_ore: 0.75,
        diamond_ore: 0.5,
    }
}
```