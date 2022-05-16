# Mechanics

This is a summary of the Nodes world structure and game mechanics for players.
See the subchapters under **Mechanics** for more details on those topics.

## Territories
- The world is divided into regions of chunks called **territories**.
- Each territory has unique resources:
    - **Income**: items/blocks you get over time
    - **Ore**: item drops when mining stone (gold ore, coal, ...)
    - **Crops**: farm crops that can grow in a region (wheat, potatos, sugarcane, ...)
    - **Animals**: animals that can breed in a region (sheep, cows, ...)
- Use **/t map** or **/t minimap** ingame to see the territory map.
- Use **/nd territory** to see the resources in the region you are standing in.

For example, a **gold territory** may have a high gold ore drop rate when mining stone,
but no crops will grow in it (so no wheat or potato farming).
Then there could be **wheat territories** with wheat growth, but no ores, and no
other crops like potatos.
There can also be good wheat territories, with full growth rate, and shitty
wheat territories with half growth rate.

Territories may have any mix of ores, crops, and animals (depends on map designer).
The Nodes web viewer displays territories on top of dynmap,
letting you view different territories and their resources.

[Click for more details](./4-1-territories.md)

## Towns and Nations
- Players form groups of **towns**.
- Towns can group together to form **nations**.
- If your town is in a nation, if someone declares war on your town,
your entire nation will also go to war.
- Create a new town using **/t create [name]** in your current location.
The territory you are standing in will become the town's first territory.
- Create a new nation using **/n create [name]**.

Towns can also choose one territory to be their **home territory**.
This territory cannot be attacked by enemies until all the other
town territories are captured. Furthermore, the home territory can
only be captured, but not annexed (see **War**).

## Territory Claiming
- Towns have **"power"** which are points they spend to claim territories.
- More players in a town increases the town's power.
- Each player's power contribution to their town increases with play time (to a maximum value).
- A territory's power cost depends on its size and resources.
- After making a town, claim territories using **/claim territory** in the region you are standing in.

[Click for more details](./4-1-territories.md)

## War
- During war, towns conquer or annex territories from other towns.
- Declare war on an enemy with **/war [town/nation]**.
If you or your enemy is part of a nation, all towns in the nation
will go to war together.
- Attack enemy's territory by putting down a **fence block** in a chunk (fence is a "flag")
    - You must attack from the outside of a territory
    - Or attack from a captured chunk or ally territory
    - The "flag" must see the sky (cannot place underground)
    - Break the wool or fence block in a flag to stop an enemy attack
- Use the Nodes minimap **/t minimap** to see what chunk you are in and where
you can attack.
- Use **/peace [town/nation]** to negotiate peace treaties.
- After accepting peace, towns/nations will enter a **truce period** where they
cannot declare war on each other

[Click for more details](./4-2-diplomacy-war.md)

## Capturing Territories
- Each territory has a **home chunk** indicated by an "H" symbol on the map
(**/t map** or **/t minimap**).
- After capturing the home chunk, the entire territory is now **occupied** by the attacking town.
- You can conquer territories in two ways:
    - **Occupation**: (Default) The territory is still owned by original town,
    but the occupier gets taxes: a portion of the territory's income
    and any ores mined, crops harvested, or animals bred in this territory
    by players goes to the occupier's **/t income** chest.
    - **Annexation**: You can add the occupied territory to your town as a normal
    claimed territory. But you no longer get taxes (have to work land yourself).

[Click for more details](./4-2-diplomacy-war.md#occupation)
