# fast edit
allow editing many blocks in mineman in single transaction

included as separate lib because requires nms .jar dependencies, which has conflicting
dependencies (gson) with main Nodes library

```
Fast set blocks
Because default world.setBlock or block.setType is very slow (lighting + packets)

See: https://www.spigotmc.org/threads/how-to-set-blocks-incredibly-fast.476097/


This is code I actually use.
The code itself is very clear.
What I do here is just simple 3 steps:
1. modify block data in memory
2. update lighting
3. unload & load chunk data

Minecraft server contains data as of what chunks are loaded for players. However, it's not visible and we have to check whether a modified chunk is in view distance or not.

There are several cons that you have to take care of.
1. You can modify blocks in unloaded chunks. However, it's slower than usual.
2. Block update does not happen.
3. Light update is not perfect when you edit unloaded chunks
4. Light update doesn't work well when you have Sodium or some other lighting mod installed, because lighting is cached and ignore lighting update packet

...

- Toshimichi
```