# JavaBridge
Minecraft Fabric Mod to write Minecraft mods in different programming languages (like C#)

Works by opening a TCP socket that listens for different types of events/actions.

It automatically loads in all mods that come with a *.bridgeStartup file in the mods folder. (Modrinth profiles are supported)

### Available languages
* C# - [ModHost.CSharp](https://github.com/chiouyazo/ModHost.CSharp)


## Getting Started
The official JavaBridge ModHosts are actively being documented at [javabridge.chiouya.cloud](https://javabridge.chiouya.cloud).

This documentation is a fork of the fabric documentation, changed to use the modhost code examples instead. (But with  the same structure of the fabric docs)

### Goal
Reflect every feature in the [fabric docs](https://docs.fabricmc.net/develop)

Currently supported features:
* Commands
  * Command executes
  * Command context (limited (includes player info/context))
  * Sub commands
  * Command feedback
  * Command requirements (pre check)
  * Client only commands
  * Command arguments
  * ~~Command redirects~~
  * Optional arguments
  * ~~Custom argument types~~
  * Command suggestions
  * Command suggestion context

* Screens
  * VERY Basic display/push/delete/list functionality