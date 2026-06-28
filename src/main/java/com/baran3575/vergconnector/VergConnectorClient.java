package com.baran3575.vergconnector;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.packs.PackType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistryImpl;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;

public class VergConnectorClient {

    public static void init(IEventBus modEventBus) {
        initializeClientFabricMods();

        // Register Mod Bus Events
        modEventBus.addListener(VergConnectorClient::onRegisterBlockColors);
        modEventBus.addListener(VergConnectorClient::onRegisterItemColors);
        modEventBus.addListener(VergConnectorClient::onRegisterRenderers);
        modEventBus.addListener(VergConnectorClient::onRegisterReloadListeners);
    }

    private static void initializeClientFabricMods() {
        VergConnector.LOGGER.info("[Verg Connector Client] Running client-side Fabric entrypoints...");
        for (net.neoforged.neoforgespi.language.IModFileInfo modFileInfo : net.neoforged.fml.ModList.get().getModFiles()) {
            net.neoforged.neoforgespi.locating.IModFile modFile = modFileInfo.getFile();
            java.nio.file.Path fabricModJson = modFile.findResource("fabric.mod.json");
            if (java.nio.file.Files.exists(fabricModJson)) {
                try {
                    String jsonContent = java.nio.file.Files.readString(fabricModJson, java.nio.charset.StandardCharsets.UTF_8);
                    java.util.List<String> clientEntrypoints = VergConnector.parseTopLevelEntrypoints(jsonContent, "client");
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

    private static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        VergConnector.LOGGER.info("[Verg Connector Client] Registering Fabric block color providers...");
        ColorProviderRegistryImpl<Block, BlockColor> blockRegistry =
            (ColorProviderRegistryImpl<Block, BlockColor>) ColorProviderRegistry.BLOCK;
        for (ColorProviderRegistryImpl.Registration<Block, BlockColor> reg : blockRegistry.getPending()) {
            event.register(reg.provider, reg.objects);
        }
    }

    private static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        VergConnector.LOGGER.info("[Verg Connector Client] Registering Fabric item color providers...");
        ColorProviderRegistryImpl<Item, ItemColor> itemRegistry =
            (ColorProviderRegistryImpl<Item, ItemColor>) ColorProviderRegistry.ITEM;
        for (ColorProviderRegistryImpl.Registration<Item, ItemColor> reg : itemRegistry.getPending()) {
            event.register(reg.provider, reg.objects);
        }
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        VergConnector.LOGGER.info("[Verg Connector Client] Registering Fabric block entity and entity renderers...");
        for (BlockEntityRendererRegistry.Registration<?> reg : BlockEntityRendererRegistry.getPending()) {
            registerBlockEntityHelper(event, reg);
        }
        for (EntityRendererRegistry.Registration<?> reg : EntityRendererRegistry.getPending()) {
            registerEntityHelper(event, reg);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void registerBlockEntityHelper(EntityRenderersEvent.RegisterRenderers event, BlockEntityRendererRegistry.Registration<T> reg) {
        event.registerBlockEntityRenderer(reg.type, reg.factory);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> void registerEntityHelper(EntityRenderersEvent.RegisterRenderers event, EntityRendererRegistry.Registration<T> reg) {
        event.registerEntityRenderer(reg.type, (net.minecraft.client.renderer.entity.EntityRendererProvider<T>) reg.provider);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        VergConnector.LOGGER.info("[Verg Connector Client] Registering Fabric client resource reload listeners...");
        ResourceManagerHelper clientHelper = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        for (IdentifiableResourceReloadListener listener : clientHelper.getListeners()) {
            event.registerReloadListener(listener);
        }
    }
}
