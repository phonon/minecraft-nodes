Nodes Chat Commands
==============================

## Chat Settings
These change your chat channel so that when you type in chat, only members of your town, nation, or allies will see the message.
- **/globalchat (or /gc)**: Set chat to global (default, everyone sees).
   - **/gc mute**: Mute global chat
   - **/gc unmute**: Unmute global chat
- **/townchat (or /tc)**: Only town members see your chat messages.
- **/nationchat (or /nc)**: Only nation members see your chat messages.
- **/allychat (or /ac)**: Only town or nation allies see your chat messages.


## /town (or /t)
For managing a player's town, intended to only be used ingame by players.
- **/town create [name]**: Create a new town with the specified name at location.
- **/town delete**: Delete your town. Town leaders only.
- **/town promote [name]**: Makes player a town officer.
- **/town demote [name]**: Removes player from town officers.
- **/town leader [name]**: Give town leadership to another player in town.
- **/town apply [town]**: Ask to join a town.
- **/town invite [player]**: Invite a player to join your town. Town leader and officers only.
- **/town leave**: Abandon membership in your town.
- **/town kick [player]**: Remove another player from your town. Town leader and officers only.
- **/town spawn**: Teleport to your town's main spawnpoint.
   - **/town spawn [outpost]**: Teleport to an outpost's spawn point.
- **/town setspawn**: Change your town's spawnpoint to another location in the home territory. Town leader only.
- **/town list**: View list of all established towns
- **/town info**: View your town's name, leader, officers, residents, and claims.
   - **/town info [town]**: View details of another town
- **/town online**: View your town's online players
   - **/town online [town]**: View another town's online players
- **/town color [r] [g] [b]**: Set town territory color for dynmap. Town leader only.
- **/town claim**: Claim a contiguous territory for your town. Town leader and officers only.
- **/town unclaim**: Abandon your town's claim over a territory
- **/town income**: Collect income from territory bonuses. Town leader and officers only.
- **/town prefix [prefix]**: Set personal chat prefix
   - **/town prefix [player] [prefix]**: Set a player's prefix (leader and officers only)
- **/town suffix [suffix]**: Set personal chat suffix
   - **/town suffix [player] [suffix]**: Set a player's suffix (leader and officers only)
- **/town rename [new name]**: Rename your town. Town leader only.
- **/town map**: Prints territory map into chat for player
- **/town minimap [3|4|5]**: Turns on/off territory chunks minimap on sidebar. Optionally specify size value: 3, 4, or 5.
- **/town permissions [type] [group] [allow/deny]**: Set permissions for interacting in town territory. [type] can be: interact, build, destroy, chests, items [group] can be: nation, ally, outsider. Last entry is either "allow" or "deny"
- **/town protect**: Toggle protecting/unprotecting chests with mouse click.
   - **/town protect show**: Shows protected chests with particles
- **/town trust [name]**: Mark player in town as trusted. Leader and officers only.
- **/town untrust [name]**: Mark player in town as untrusted. Leader and officers only.
- **/town capital**: Move town home territory to your current player location. (This also changes town spawn location.)
- **/town annex**: Annex an occupied territory and add it to your town
- **/town outpost**: Commands to manage town outposts.
   - **/town outpost list**: Print list of town's outposts.
   - **/town outpost setspawn**: Set an outpost's spawn point. Player must be in the outpost territory.



## /nation (or /n)
For managing a player's nation, intended to only be used ingame by players.
- **/nation create [name]**: Create a new nation with your town as capital.
- **/nation delete**: Delete your nation. Leader of capital town only.
- **/nation leave**: Leave your nation. Used by town leaders only.
- **/nation capital [town]**: Set another town in your nation as its capital
- **/nation invite [town]**: Invite another town to join your nation. Leader of capital town only.
- **/nation list**: View list of all established nations and their towns
- **/nation color [r] [g] [b]**: Set territory color on dynmap for all towns in nation. Leader of capital town only.
- **/nation rename [name]**: Renames your nation. Leader of capital town only.
- **/nation online**: View your nation's online players
   - **/nation online [nation]**: View another nation's online players
- **/nation info [nation]**: View nation's info
- **/nation spawn [town]**: Teleport to town inside your nation. May cost items to use.



## /ally [name]
Offer/accept alliance with another town or nation.
- **/ally [town]**: Offer/accept alliance with a town.
- **/ally [nation]**: Offer/accept alliance with a nation.


## /unally [name]
Break alliance with another town or nation. Towns will enter a truce period.
- **/unally [town]**: Break alliance with a town.
- **/unally [nation]**: Break alliance with a nation.


## /war [name]
Declare war on other towns or nations.
- **/war [town]**: Declares war on a town.
- **/war [nation]**: Declares war on a nation.


## /peace [name]
Opens peace treaty window with another town or nation.
- **/peace [town]**: Negotiate a peace treaty with a town.
- **/peace [nation]**: Negotiate a peace treaty with a nation.


## /truce
View your town's remaining truce times with other towns.
- **/truce [town]**: View other town's remaining truce times.


## /nodes (or /nd)
For printing general info about the world (e.g. resource nodes, territories, towns, nations, players). Can be used by players ingame or in console.
- **/nodes help**: Prints list of commands
- **/nodes resource**: Prints list of all resource nodes
   - **/nodes resource [name]**: Print detailed stats of a resource node type (income, crops, animals, ore)
- **/nodes territory**: In console, just prints total territory count. Ingame, prints info about territory player is standing in.
   - **/nodes territory [id]**: Prints info about territory from id
- **/nodes town**: Prints list of all towns, their player count and territory count
   - **/nodes town [name]**: Prints detailed info about town from [name] (territories, players, etc...)
- **/nodes nation**: Prints list of all nations
   - **/nodes nation [name]**: Prints detailed info about nation from [name] (towns, allies, enemies, etc...)
- **/nodes player [name]**: Prints player info (their town and nation)
- **/nodes war**: Print if war enabled/disabled



## /nodesadmin (or /nda)
Admin command for manually modifying the world state. Intended for admin use ingame or in console. Do things like manually creating/deleting towns, or turning on war.
- **/nodesadmin reload [config|listeners|world]**: Reload components of Nodes engine
- **/nodesadmin war [enable|disable]**: Enables/disables war
- **/nodesadmin town create [name] [id1] [id2] ...**: Creates a town from name and list of territory ids. The first id is required and becomes the home territory. This town is created without any residents or leader.
- **/nodesadmin town delete [name]**: Deletes town with given name.
- **/nodesadmin town addplayer [name] [player1] [player2] ...**: Add list of player names [player] to town from [name].
- **/nodesadmin town removeplayer [name] [player1] [player2] ...**: Remove list of player names [player] from town from [name].
- **/nodesadmin town addterritory [name] [id1] [id2] ...**: Add list of territory from their ids to town from [name]. This ignores chunk claiming limits for a town.
- **/nodesadmin town removeterritory [name] [id1] [id2] ...**: Remove list of territory from their ids to town from [name].
- **/nodesadmin town captureterritory [town] [id1] [id2] ...**: Makes town capture list of territory from their ids
- **/nodesadmin town releaseterritory [id1] [id2] ...**: Releases list of territory ids from their current occupier
- **/nodesadmin town claimsbonus [town] [#]**: Set town bonus claims.
- **/nodesadmin town claimspenalty [town] [#]**: Set town penalty claims.
- **/nodesadmin town claimsannex [town] [#]**: Set town bonus claims.
- **/nodesadmin town addofficer [town] [player]**: Add officer to town
- **/nodesadmin town removeofficer [town] [player]**: Remove officer from town
- **/nodesadmin town leader [town] [player]**: Set town resident as new town leader
- **/nodesadmin town removeleader [town]**: Remove town's leader (makes town leaderless)
- **/nodesadmin town open [town]**: Toggle whether town is open to join
- **/nodesadmin town income [town]**: View a town's income inventory gui (must run ingame).
- **/nodesadmin town setspawn [town]**: Set town spawn to player location (must run ingame).
- **/nodesadmin town spawn [town]**: Spawn at a town's spawn
- **/nodesadmin town sethome [town] [id]**: Set a town's home territory
- **/nodesadmin town setHomeCooldown [town] [int]**: Set a town's move home cooldown
- **/nodesadmin town addoutpost [town] [name] [id]**: Add an outpost with name to town at territory id
- **/nodesadmin town removeoutpost [town] [name]**: Remove outpost from town with given name
- **/nodesadmin nation create [name] [town1] [town2] ...**: Creates a new nation with [name] with list of towns. [town1] required (cannot have empty nations).
- **/nodesadmin nation delete [name]**: Deletes nation [name].
- **/nodesadmin nation addtown [name] [town1] [town2] ...**: Add list of towns to nation [name].
- **/nodesadmin nation removetown [name] [town1] [town2] ...**: Remove list of towns from nation [name].
- **/nodesadmin nation capital [nation] [town]**: Make input town the new capital of nation. Town must already be part of the nation.
- **/nodesadmin enemy [name1] [name2]**: Sets enemy status between [name1] and [name2] (either town or nation names)
- **/nodesadmin peace [name1] [name2]**: Removes enemy status between [name1] and [name2] (either town or nation names)
- **/nodesadmin ally [name1] [name2]**: Makes [name1] and [name2] allies (either town or nation names).
- **/nodesadmin allyremove [name1] [name2]**: Removes alliance between [name1] and [name2] (either town or nation names).
- **/nodesadmin truce [name1] [name2]**: Sets a truce between [name1] and [name2] (either town or nation names).
- **/nodesadmin truceremove [name1] [name2]**: Removes any truce between [name1] and [name2] (either town or nation names).
- **/nodesadmin debug**: Console command to debug runtime world object state. General usage is /nodesadmin debug [type] [name/id] [field]
   - **/nodesadmin debug resource [name] [field]**:
   - **/nodesadmin debug chunk [x,z] [field]**: To input coord, format is "x,z" with no spaces
   - **/nodesadmin debug territory [id] [field]**:
   - **/nodesadmin debug resident [name] [field]**:
   - **/nodesadmin debug town [name] [field]**:
   - **/nodesadmin debug nation [name] [field]**:

