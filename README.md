# JavaBridge
Minecraft Fabric Mod to write Minecraft mods in different programming languages (like C#)

Works by opening a TCP socket that listens for different types of events/actions.

It automatically loads in all mods that come with a *.bridgeStartup file in the mods folder. (Modrinth profiles are supported)

### Available languages
* C# - [ModHost.CSharp](https://github.com/chiouyazo/ModHost.CSharp)


### Goal
Reflect every feature in the [fabric docs](https://docs.fabricmc.net/develop)

Currently supported features:
* Commands
  * Command executes
  * Command feedback
  * Command requirements (pre check)
  * Client commands
  * Command arguments
  * ~~Command redirects~~
  * Optional arguments
  * ~~Custom argument types~~
  * ~~Command suggestions~~