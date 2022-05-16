# Quick Nodes Guide for Brainlets

## What is Nodes?
- Map is divided into different **territories** (see dynmap viewer)
- Different territories have different resources:
    - **Income**: items/blocks you get over time
    - **Ore**: item drops when mining stone (gold ore, coal, ...)
    - **Crops**: farm crops that can grow in a region (wheat, potatos, sugarcane, ...)
    - **Animals**: animals that can breed in a region (sheep, cows, ...)
- You and other players form **towns** and claim territories to use their resources
- During war, you can capture territories from other towns


## Commands
- **/town** or **/t** for short is the main command.
- **/nation** or **/n** for short is for nation management.
- **/war** and **/peace** for declaring war, offering peace. 
- **/nodes** or **/nd** for short has commands with info about the Nodes world.
- **/gc**, **/tc**, **/nc**, **/ac** for changing your chat channel (who can see your messages)


## How to Join a Town
Request to join someone's town with **/town join name**, where **name** is
their town name.


## How to Form a Town
1. Go to the territory location you want to create your town in
2. Type **/t create name**, replace **name** with the name you want
3. Invite players with **/t invite playername**
4. Ally other towns using **/ally townname**


## How to Claim Territories
- See ingame territory map using **/t map** or **/t minimap**
- View a territory's resources with **/nd territory**
- Go into the territory you want to claim and use **/t claim**
- Use **/t unclaim** to remove a territory you no longer want 

Claiming territories costs town **power**. You get more power by
having more players and total player play time on the server.
Territories with important resources cost more power.


## How to get Town Income
- Use **/t income** to view your town's income chest
- You may get income over time from territories, which gets put in this chest
- Taxes from territories captured during war also go into this chest


## How to Create a Nation
1. Type **/n create name**, replace **name** with the name you want
2. Invite other towns with **/n invite townname**


## How to Fight War
- Declare war on a town or nation with **/war enemyname**
- Attack enemy's territory by putting down a **fence block** in a chunk (fence is a "flag")
    - You must attack from the outside of a territory
    - Or attack from a captured chunk or ally territory
    - The "flag" must see the sky (cannot place underground)
    - Break the wool block on a flag to stop an enemy attack
    - If the flag is not broken, after some time you will capture the chunk
- Turn on minimap to see where to attack: use **/t minimap**
- Capture a territory's home chunk ("H" symbol on the minimap) to capture the territory

## Captured Territories
- Two ways to capture territory:
    - **Occupation**: (Default) The territory is still owned by original town,
    but the occupier gets taxes: a portion of the territory's income
    and any ores mined, crops harvested, or animals bred in this territory
    by players goes to the occupier.
    - **Annexation**: The territory is added to your town as a normal
    claimed territory.

## Peace Treaties
- Negotiate peace treaties with other towns/nations using **/peace [town/nation]**
- This opens a Peace Treaty GUI inventory window
- After accepting peace, towns enter a **truce period** where they cannot war each other

## Truce Periods
- Default truce periods are 48 hours
- Use **/truce** to view your current truces with other towns

## Alliances
- Use **/ally name** to offer/accept an alliance with another town/nation
- Allied towns/nations cannot declare war on each other
- Use **/unally name** to break the alliance. You will enter a **truce period**
after breaking the alliance.
