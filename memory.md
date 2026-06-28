# Verg-connector Memory

## Project Goal
Create a bridge mod for **NeoForge 1.21.1** that enables running both Fabric and Forge mods.
*   **First Target:** Set up a proper system for NeoForge and make it possible to run the Fabric version of **Sodium** on NeoForge 1.21.1.

## Environment Details
*   **Minecraft Version:** 1.21.1
*   **NeoForge Version:** 21.1.x
*   **Java version:** 21

## Current State
*   **Fabric Loader Shims:** Created clean shims for `net.fabricmc.api` and `net.fabricmc.loader.api` interfaces and entrypoint interfaces so Fabric mods compile and execute safely.
*   **Dynamic Mod Discovery:** Implemented `ModDiscoverer` that hooks into NeoForge's FML startup, scans the mods folder for Fabric jars (containing `fabric.mod.json`), wraps them in `FabricJarContentsWrapper` implementing the `JarContents` interface, and registers them directly into FML's classpath.
*   **Virtual TOML Generation:** Automatically generates virtual `neoforge.mods.toml` files in memory from `fabric.mod.json` data, including mod ID, name, version, and license.
*   **Mixins Support:** Dynamically parses mixin configurations from `fabric.mod.json` and generates the corresponding `[[mixins]]` declarations in the virtual NeoForge TOML so FML registers the Fabric mod's Mixins onto the system.
*   **Lifecycle Execution:** Triggers Fabric entrypoints (`onInitialize`, `onInitializeClient`, `onInitializeServer`) during NeoForge FML mod loading phases.
*   **GitHub Actions Build:** The compile pipeline has been fully configured and successfully builds the bridge mod on GitHub Actions.
