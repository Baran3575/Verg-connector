package com.baran3575.vergconnector;

import net.neoforged.bus.api.IEventBus;

public class VergConnectorClient {

    public static void init(IEventBus modEventBus) {
        initializeClientFabricMods();
    }

    private static void initializeClientFabricMods() {
        VergConnector.LOGGER.info("[Verg Connector Client] Running client-side Fabric entrypoints...");
        for (net.neoforged.neoforgespi.language.IModFileInfo modFileInfo : net.neoforged.fml.ModList.get().getModFiles()) {
            net.neoforged.neoforgespi.locating.IModFile modFile = modFileInfo.getFile();
            java.nio.file.Path fabricModJson = modFile.findResource("fabric.mod.json");
            if (java.nio.file.Files.exists(fabricModJson)) {
                try {
                    String jsonContent = java.nio.file.Files.readString(fabricModJson, java.nio.charset.StandardCharsets.UTF_8);
                    java.util.Map<String, java.util.List<String>> allEntrypoints = VergConnector.parseAllEntrypoints(jsonContent);
                    
                    String modId = VergConnector.parseTopLevelJsonString(jsonContent, "id");
                    net.fabricmc.loader.api.ModContainer container = modId != null
                        ? com.baran3575.vergconnector.fabric.FabricLoaderImpl.INSTANCE.getModContainer(modId).orElse(null)
                        : null;

                    for (java.util.Map.Entry<String, java.util.List<String>> entry : allEntrypoints.entrySet()) {
                        String epKey = entry.getKey();
                        for (String className : entry.getValue()) {
                            if (container != null) {
                                com.baran3575.vergconnector.fabric.FabricLoaderImpl.INSTANCE.registerEntrypoint(epKey, className, container);
                            }
                        }
                    }

                    // Execute client entrypoints
                    java.util.List<String> clientEntrypoints = allEntrypoints.getOrDefault("client", java.util.List.of());
                    for (String entrypoint : clientEntrypoints) {
                        try {
                            VergConnector.LOGGER.info("[Verg Connector Client] Loading client entrypoint: {}", entrypoint);
                            Class<?> clazz = Class.forName(entrypoint);
                            Object instance = clazz.getDeclaredConstructor().newInstance();
                            if (instance instanceof net.fabricmc.api.ClientModInitializer initializer) {
                                try {
                                    com.baran3575.vergconnector.mixin.RegistryHelper.UNFROZEN.set(true);
                                    initializer.onInitializeClient();
                                } finally {
                                    com.baran3575.vergconnector.mixin.RegistryHelper.UNFROZEN.set(false);
                                }
                                VergConnector.LOGGER.info("[Verg Connector Client] Successfully initialized client entrypoint: {}", entrypoint);
                            }
                        } catch (Exception e) {
                            VergConnector.LOGGER.error("[Verg Connector Client] Failed to initialize client entrypoint: {}", entrypoint, e);
                        }
                    }
                } catch (Exception e) {
                    VergConnector.LOGGER.error("[Verg Connector Client] Failed to process client entrypoints for mod file: {}", modFile.getFileName(), e);
                }
            }
        }
    }
}
