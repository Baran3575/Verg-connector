package com.baran3575.vergconnector;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.SubscribeEvent;

@Mod(VergConnector.MODID)
public class VergConnector {
    public static final String MODID = "vergconnector";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VergConnector(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        initializeFabricMods();
    }

    private void initializeFabricMods() {
        LOGGER.info("[Verg Connector] Initializing Fabric mods state and running main entrypoints...");

        for (net.neoforged.neoforgespi.language.IModFileInfo modFileInfo : net.neoforged.fml.ModList.get().getModFiles()) {
            net.neoforged.neoforgespi.locating.IModFile modFile = modFileInfo.getFile();
            java.nio.file.Path fabricModJson = modFile.findResource("fabric.mod.json");
            if (java.nio.file.Files.exists(fabricModJson)) {
                try {
                    String jsonContent = java.nio.file.Files.readString(fabricModJson, java.nio.charset.StandardCharsets.UTF_8);
                    String id = parseJsonString(jsonContent, "id");
                    String version = parseJsonString(jsonContent, "version");
                    String name = parseJsonString(jsonContent, "name");
                    String description = parseJsonString(jsonContent, "description");

                    if (id == null) id = "unknown";
                    if (version == null) version = "1.0.0";
                    if (name == null) name = id;
                    if (description == null) description = "";

                    var metadata = new com.baran3575.vergconnector.fabric.ModMetadataImpl(id, version, name, description);
                    var container = new com.baran3575.vergconnector.fabric.ModContainerImpl(metadata, modFile.getFilePath());
                    com.baran3575.vergconnector.fabric.FabricLoaderImpl.INSTANCE.registerMod(id, container);
                    LOGGER.info("[Verg Connector] Registered Fabric mod: {} ({})", name, version);

                    // Execute main entrypoints
                    java.util.List<String> mainEntrypoints = parseEntrypoints(jsonContent, "main");
                    for (String entrypoint : mainEntrypoints) {
                        try {
                            LOGGER.info("[Verg Connector] Loading main entrypoint: {}", entrypoint);
                            Class<?> clazz = Class.forName(entrypoint);
                            Object instance = clazz.getDeclaredConstructor().newInstance();
                            if (instance instanceof net.fabricmc.api.ModInitializer initializer) {
                                initializer.onInitialize();
                                LOGGER.info("[Verg Connector] Successfully initialized main entrypoint: {}", entrypoint);
                            }
                        } catch (Exception e) {
                            LOGGER.error("[Verg Connector] Failed to initialize main entrypoint: {}", entrypoint, e);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("[Verg Connector] Failed to process Fabric mod file: {}", modFile.getFileName(), e);
                }
            }
        }
    }

    public static String parseJsonString(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        int colonIdx = json.indexOf(":", idx);
        if (colonIdx == -1) return null;
        int quoteStart = json.indexOf("\"", colonIdx);
        if (quoteStart == -1) return null;
        int quoteEnd = json.indexOf("\"", quoteStart + 1);
        if (quoteEnd == -1) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    public static java.util.List<String> parseEntrypoints(String json, String key) {
        java.util.List<String> list = new java.util.ArrayList<>();
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return list;
        int colonIdx = json.indexOf(":", idx);
        if (colonIdx == -1) return list;
        int arrayStart = json.indexOf("[", colonIdx);
        int arrayEnd = json.indexOf("]", colonIdx);
        if (arrayStart == -1 || arrayEnd == -1 || arrayStart > arrayEnd) {
            int quoteStart = json.indexOf("\"", colonIdx);
            if (quoteStart != -1 && (arrayStart == -1 || quoteStart < arrayStart)) {
                int quoteEnd = json.indexOf("\"", quoteStart + 1);
                if (quoteEnd != -1) {
                    list.add(json.substring(quoteStart + 1, quoteEnd));
                }
            }
            return list;
        }

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        String[] elements = arrayContent.split(",");
        for (String element : elements) {
            element = element.trim();
            if (element.startsWith("{")) {
                int valueIdx = element.indexOf("\"value\"");
                if (valueIdx != -1) {
                    int valColon = element.indexOf(":", valueIdx);
                    if (valColon != -1) {
                        int qStart = element.indexOf("\"", valColon);
                        if (qStart != -1) {
                            int qEnd = element.indexOf("\"", qStart + 1);
                            if (qEnd != -1) {
                                list.add(element.substring(qStart + 1, qEnd));
                            }
                        }
                    }
                }
            } else if (element.startsWith("\"") && element.endsWith("\"")) {
                list.add(element.substring(1, element.length() - 1));
            }
        }
        return list;
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Verg Connector initialized!");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Verg Connector server starting...");
    }
}
