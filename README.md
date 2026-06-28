# Verg Connector

Verg Connector is a high-compatibility bridge mod for **NeoForge 1.21.1** that enables running Fabric and Forge mods together in the same environment.

## 🌐 Mod Compatibility Hub
You can check which mods are supported and find recommendations for renderer-heavy mods on our live compatibility list:
👉 **[Verg Connector Mod Compatibility List](https://Baran3575.github.io/Verg-connector/)**

---

## 🚀 Key Features

* **🛡️ Registry Timing & Order Safety**: Automatically defers Fabric mod initialization until NeoForge's object registry phase is ready, eliminating early-loading crashes.
* **🌐 Hybrid Networking Bridge**: Intercepts Fabric's client-to-server (C2S) and server-to-client (S2C) custom packet payload channels, mapping them directly to NeoForge's event handlers.
* **📦 Dynamic TOML & Mixin Generation**: Automatically reads `fabric.mod.json` files in the mods folder, dynamically compiling them into virtual NeoForge TOMLs and registering their Mixins.
* **🎨 Creative Tab Bridging**: Automatically maps Fabric `ItemGroupEvents` hooks to NeoForge `BuildCreativeModeTabContentsEvent` to populate items in creative tabs.
* **🖥️ Client + Server Execution**: Properly separates entrypoints (`onInitializeClient` runs on client, `onInitializeServer` on server, and `onInitialize` on both).

---

## ⚠️ Renderer-Heavy Mods (Sodium / Iris / Shaders)

Renderer-heavy mods that manipulate the OpenGL pipeline or GLFW window states (e.g., Fabric **Sodium**, **Iris**) conflict directly with NeoForge's custom rendering optimizations and early loading screens.

To prevent OpenGL state bugs, memory leaks, and mixin conflicts:
1. **For performance optimizations**, use **[Embeddium](https://modrinth.com/mod/embeddium)** (the optimized NeoForge port of Sodium) instead of the raw Fabric jar.
2. **For shaders**, use **[Oculus](https://modrinth.com/mod/oculus)** (the optimized NeoForge port of Iris).

These native ports have been built to respect NeoForge's custom rendering pipeline while keeping 100% feature compatibility.
