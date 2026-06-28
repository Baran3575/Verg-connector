package com.baran3575.vergconnector;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = VergConnector.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = VergConnector.MODID, value = Dist.CLIENT)
public class VergConnectorClient {
    public VergConnectorClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        initializeClientFabricMods();
    }

    private void initializeClientFabricMods() {
        VergConnector.LOGGER.info("[Verg Connector Client] Running client-side Fabric entrypoints...");
        for (net.neoforged.fml.loading.moddiscovery.ModFile modFile : net.neoforged.fml.ModList.get().getModFiles()) {
            java.nio.file.Path fabricModJson = modFile.findResource("fabric.mod.json");
            if (java.nio.file.Files.exists(fabricModJson)) {
                try {
                    String jsonContent = java.nio.file.Files.readString(fabricModJson, java.nio.charset.StandardCharsets.UTF_8);
                    java.util.List<String> clientEntrypoints = VergConnector.parseEntrypoints(jsonContent, "client");
                    for (String entrypoint : clientEntrypoints) {
                        try {
                            VergConnector.LOGGER.info("[Verg Connector Client] Loading client entrypoint: {}", entrypoint);
                            Class<?> clazz = Class.forName(entrypoint);
                            Object instance = clazz.getDeclaredConstructor().newInstance();
                            if (instance instanceof net.fabricmc.api.ClientModInitializer initializer) {
                                initializer.onInitializeClient();
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

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        VergConnector.LOGGER.info("Verg Connector Client Setup initialized!");
    }
}
