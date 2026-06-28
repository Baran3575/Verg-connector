# Verg-connector Memory

## Project Goal
Create a bridge mod for **NeoForge 1.21.1** that enables running both Fabric and Forge mods.
*   **First Target:** Set up a proper system for NeoForge and make it possible to run the Fabric version of **Sodium** on NeoForge 1.21.1.

## Environment Details
*   **Minecraft Version:** 1.21.1
*   **NeoForge Version:** 21.1.x
*   **Java version:** 21

## Current State
*   **Fabric Loader & Environment Shims:** Created clean shims for `net.fabricmc.api` and `net.fabricmc.loader.api` interfaces, entrypoint interfaces, and loader shims.
*   **Dynamic Mod Discovery:** Implemented `ModDiscoverer` that scans the mods folder for Fabric jars, wraps them in `FabricJarContentsWrapper` implementing the `JarContents` interface, and registers them directly into FML's classpath.
*   **Virtual TOML Generation:** Automatically generates virtual `neoforge.mods.toml` files in memory from `fabric.mod.json` data, including mod ID, name, version, and license.
*   **Mixins Support:** Dynamically parses mixin configurations from `fabric.mod.json` and generates the corresponding `[[mixins]]` declarations in the virtual NeoForge TOML so FML registers the Fabric mod's Mixins onto the system.
*   **Lifecycle Execution:** Triggers Fabric entrypoints (`onInitialize`, `onInitializeClient`, `onInitializeServer`) during appropriate NeoForge FML mod loading phases on both client and server side.
*   **Fabric API Shims & Bridging:**
    *   **Colors API:** Bridges Fabric `ColorProviderRegistry` to NeoForge's block and item color registration events.
    *   **Creative Tabs API:** Bridges Fabric `ItemGroupEvents` to NeoForge's creative tab contents event.
    *   **Rendering API:** Bridges Fabric `BlockEntityRendererRegistry` and `EntityRendererRegistry` to NeoForge's block entity/entity renderer registration events.
    *   **Resource Loader API:** Bridges Fabric `ResourceManagerHelper` client/server reloading registries to NeoForge's reload listener events.
    *   **Lifecycle Events API:** Bridges Fabric `ServerLifecycleEvents` (server starting, started, stopping, stopped) to NeoForge's corresponding server lifecycle events.
    *   **Networking API:** Stubs Fabric `PayloadTypeRegistry`, `ServerPlayNetworking`, and `ClientPlayNetworking`.
    *   **Registry Events API:** Stubs Fabric `RegistryAttributeHolder`.
*   **GitHub Actions Build:** The compile pipeline has been fully configured and successfully builds the bridge mod on GitHub Actions (Runs #10, #12, #13 passed).
