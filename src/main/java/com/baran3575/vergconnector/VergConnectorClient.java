package com.baran3575.vergconnector;

import net.neoforged.bus.api.IEventBus;
import com.baran3575.vergconnector.fabric.FabricLoaderImpl;
import com.baran3575.vergconnector.mixin.RegistryHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.EntrypointContainer;

public class VergConnectorClient {

    public static void init(IEventBus modEventBus) {
        initializeClientFabricMods();
    }

    private static void initializeClientFabricMods() {
        VergConnector.LOGGER.info("[Verg Connector Client] Running client-side Fabric entrypoints...");
        for (EntrypointContainer<ClientModInitializer> container : FabricLoaderImpl.INSTANCE.getEntrypointContainers("client", ClientModInitializer.class)) {
            try {
                VergConnector.LOGGER.info("[Verg Connector Client] Loading client entrypoint: {}", container.getProvider().getMetadata().getId());
                RegistryHelper.UNFROZEN.set(true);
                container.getEntrypoint().onInitializeClient();
            } catch (Exception e) {
                VergConnector.LOGGER.error("[Verg Connector Client] Failed to initialize client entrypoint from mod: {}", container.getProvider().getMetadata().getId(), e);
            } finally {
                RegistryHelper.UNFROZEN.set(false);
            }
        }
    }
}
