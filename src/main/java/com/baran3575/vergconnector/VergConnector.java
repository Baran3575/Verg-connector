package com.baran3575.vergconnector;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ClientPlayNetworking;

@Mod(VergConnector.MODID)
public class VergConnector {
    public static final String MODID = "vergconnector";
    public static final Logger LOGGER = LogUtils.getLogger();
    
    public VergConnector(IEventBus modEventBus, ModContainer modContainer) {
        // Run Fabric mod initialization BEFORE events are registered or registered payloads are collected
        initializeFabricMods();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onBuildCreativeModeTabContents);
        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            VergConnectorClient.init(modEventBus);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        LOGGER.info("[Verg Connector] Registering Fabric custom payload networking channels to NeoForge...");
        PayloadRegistrar registrar = event.registrar(MODID);

        // Register Server Receivers (C2S)
        net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl playC2S = net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.PLAY_C2S;
        for (Map.Entry<CustomPacketPayload.Type<?>, StreamCodec<? super FriendlyByteBuf, ?>> entry : playC2S.getCodecs().entrySet()) {
            registerServerChannel(registrar, (CustomPacketPayload.Type) entry.getKey(), (StreamCodec) entry.getValue());
        }

        // Register Client Receivers (S2C)
        net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl playS2C = net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.PLAY_S2C;
        for (Map.Entry<CustomPacketPayload.Type<?>, StreamCodec<? super FriendlyByteBuf, ?>> entry : playS2C.getCodecs().entrySet()) {
            registerClientChannel(registrar, (CustomPacketPayload.Type) entry.getKey(), (StreamCodec) entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends CustomPacketPayload> void registerServerChannel(PayloadRegistrar registrar, CustomPacketPayload.Type<T> type, StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> codec) {
        registrar.playToServer(type, codec, (payload, context) -> {
            ServerPlayNetworking.PlayPayloadHandler<T> handler = (ServerPlayNetworking.PlayPayloadHandler<T>) ServerPlayNetworking.getReceivers().get(type);
            if (handler != null) {
                context.enqueueWork(() -> handler.receive(payload, new ServerPlayNetworking.Context() {
                    @Override
                    public ServerPlayer player() {
                        return (ServerPlayer) context.player();
                    }
                }));
            }
        });
        LOGGER.info("[Verg Connector] Registered Fabric C2S channel: {}", type.id());
    }

    @SuppressWarnings("unchecked")
    private <T extends CustomPacketPayload> void registerClientChannel(PayloadRegistrar registrar, CustomPacketPayload.Type<T> type, StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> codec) {
        registrar.playToClient(type, codec, (payload, context) -> {
            ClientPlayNetworking.PlayPayloadHandler<T> handler = (ClientPlayNetworking.PlayPayloadHandler<T>) ClientPlayNetworking.getReceivers().get(type);
            if (handler != null) {
                context.enqueueWork(() -> handler.receive(payload, new ClientPlayNetworking.Context() {
                    @Override
                    public LocalPlayer player() {
                        return (LocalPlayer) context.player();
                    }
                }));
            }
        });
        LOGGER.info("[Verg Connector] Registered Fabric S2C channel: {}", type.id());
    }

    private void onBuildCreativeModeTabContents(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.Event fabricEvent = 
            net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.getEvents().get(event.getTabKey());
        if (fabricEvent != null) {
            net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.Entries entries = event::accept;
            for (java.util.function.Consumer<net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.Entries> listener : fabricEvent.getListeners()) {
                listener.accept(entries);
            }
        }
    }

    private void initializeFabricMods() {
        LOGGER.info("[Verg Connector] Initializing Fabric mods state and running main entrypoints...");

        for (net.neoforged.neoforgespi.language.IModFileInfo modFileInfo : net.neoforged.fml.ModList.get().getModFiles()) {
            net.neoforged.neoforgespi.locating.IModFile modFile = modFileInfo.getFile();
            java.nio.file.Path fabricModJson = modFile.findResource("fabric.mod.json");
            if (java.nio.file.Files.exists(fabricModJson)) {
                try {
                    String jsonContent = java.nio.file.Files.readString(fabricModJson, java.nio.charset.StandardCharsets.UTF_8);
                    String id = parseTopLevelJsonString(jsonContent, "id");
                    String version = parseTopLevelJsonString(jsonContent, "version");
                    String name = parseTopLevelJsonString(jsonContent, "name");
                    String description = parseTopLevelJsonString(jsonContent, "description");

                    if (id == null) id = "unknown";
                    if (version == null) version = "1.0.0";
                    if (name == null) name = id;
                    if (description == null) description = "";

                    var metadata = new com.baran3575.vergconnector.fabric.ModMetadataImpl(id, version, name, description);
                    var container = new com.baran3575.vergconnector.fabric.ModContainerImpl(metadata, modFile.getFilePath());
                    com.baran3575.vergconnector.fabric.FabricLoaderImpl.INSTANCE.registerMod(id, container);
                    LOGGER.info("[Verg Connector] Registered Fabric mod: {} ({})", name, version);

                    // ─── Mixin conflict detection ──────────────────────────────────────
                    java.util.List<String> mixinConfigs = parseTopLevelEntrypoints(jsonContent, "mixins");
                    if (!mixinConfigs.isEmpty()) {
                        com.baran3575.vergconnector.mixin.MixinConfigHandler.processMixinConfigs(
                            id, modFile.getFilePath(), mixinConfigs);
                    }

                    // ─── Register ALL entrypoint keys (main, client, server, jade, modmenu, …) ───
                    // Extract the raw "entrypoints" object from the JSON
                    java.util.Map<String, java.util.List<String>> allEntrypoints = parseAllEntrypoints(jsonContent);
                    for (java.util.Map.Entry<String, java.util.List<String>> entry : allEntrypoints.entrySet()) {
                        String epKey = entry.getKey();
                        for (String className : entry.getValue()) {
                            com.baran3575.vergconnector.fabric.FabricLoaderImpl.INSTANCE.registerEntrypoint(epKey, className, container);
                            LOGGER.debug("[Verg Connector] Registered entrypoint: key='{}', class='{}'", epKey, className);
                        }
                    }

                    // Execute main entrypoints immediately
                    java.util.List<String> mainEntrypoints = allEntrypoints.getOrDefault("main", java.util.List.of());
                    for (String entrypoint : mainEntrypoints) {
                        try {
                            LOGGER.info("[Verg Connector] Loading main entrypoint: {}", entrypoint);
                            Class<?> clazz = Class.forName(entrypoint);
                            Object instance = clazz.getDeclaredConstructor().newInstance();
                            if (instance instanceof net.fabricmc.api.ModInitializer initializer) {
                                try {
                                    com.baran3575.vergconnector.mixin.RegistryHelper.UNFROZEN.set(true);
                                    initializer.onInitialize();
                                } finally {
                                    com.baran3575.vergconnector.mixin.RegistryHelper.UNFROZEN.set(false);
                                }
                                LOGGER.info("[Verg Connector] Successfully initialized main entrypoint: {}", entrypoint);
                            }
                        } catch (Exception e) {
                            LOGGER.error("[Verg Connector] Failed to initialize main entrypoint: {}", entrypoint, e);
                        }
                    }

                    // Execute server entrypoints if on dedicated server
                    if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.DEDICATED_SERVER) {
                        java.util.List<String> serverEntrypoints = allEntrypoints.getOrDefault("server", java.util.List.of());
                        for (String entrypoint : serverEntrypoints) {
                            try {
                                LOGGER.info("[Verg Connector] Loading server entrypoint: {}", entrypoint);
                                Class<?> clazz = Class.forName(entrypoint);
                                Object instance = clazz.getDeclaredConstructor().newInstance();
                                if (instance instanceof net.fabricmc.api.DedicatedServerModInitializer initializer) {
                                    try {
                                        com.baran3575.vergconnector.mixin.RegistryHelper.UNFROZEN.set(true);
                                        initializer.onInitializeServer();
                                    } finally {
                                        com.baran3575.vergconnector.mixin.RegistryHelper.UNFROZEN.set(false);
                                    }
                                    LOGGER.info("[Verg Connector] Successfully initialized server entrypoint: {}", entrypoint);
                                }
                            } catch (Exception e) {
                                LOGGER.error("[Verg Connector] Failed to initialize server entrypoint: {}", entrypoint, e);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("[Verg Connector] Failed to process Fabric mod file: {}", modFile.getFileName(), e);
                }
            }
        }
    }

    /**
     * Parse all entrypoint keys and their class lists from the full fabric.mod.json.
     * Handles the "entrypoints" object which maps keys to arrays of strings or
     * adapter objects like {"adapter":"kotlin","value":"com.example.MyClass"}.
     */
    public static java.util.Map<String, java.util.List<String>> parseAllEntrypoints(String json) {
        java.util.Map<String, java.util.List<String>> result = new java.util.LinkedHashMap<>();
        // Find the "entrypoints" key at depth 1
        int depth = 0;
        boolean inQuote = false;
        StringBuilder keyBuilder = new StringBuilder();
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && inQuote) {
                i += 2;
                continue;
            }
            if (c == '"') {
                inQuote = !inQuote;
                if (inQuote) {
                    keyBuilder.setLength(0);
                } else {
                    if (depth == 1 && keyBuilder.toString().equals("entrypoints")) {
                        // Skip past the colon
                        i++;
                        while (i < json.length() && json.charAt(i) != '{') i++;
                        if (i >= json.length()) break;
                        // Now parse the entire entrypoints object
                        int epStart = i;
                        int braceDepth = 0;
                        for (int j = epStart; j < json.length(); j++) {
                            char d = json.charAt(j);
                            if (d == '{') braceDepth++;
                            else if (d == '}') {
                                braceDepth--;
                                if (braceDepth == 0) {
                                    String epBlock = json.substring(epStart + 1, j);
                                    parseEntrypointBlock(epBlock, result);
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
            } else if (inQuote) {
                keyBuilder.append(c);
            } else {
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }
            i++;
        }
        return result;
    }

    /**
     * Parse the inner block of the "entrypoints" JSON object.
     * Keys are entrypoint names; values are arrays of string or object entries.
     */
    private static void parseEntrypointBlock(String block, java.util.Map<String, java.util.List<String>> result) {
        int i = 0;
        while (i < block.length()) {
            // Find next key
            int keyStart = block.indexOf('"', i);
            if (keyStart == -1) break;
            int keyEnd = block.indexOf('"', keyStart + 1);
            if (keyEnd == -1) break;
            String key = block.substring(keyStart + 1, keyEnd);
            i = keyEnd + 1;
            // Find the colon then the array
            int colon = block.indexOf(':', i);
            if (colon == -1) break;
            i = colon + 1;
            while (i < block.length() && Character.isWhitespace(block.charAt(i))) i++;
            if (i >= block.length() || block.charAt(i) != '[') continue;
            // Parse the array
            int arrStart = i + 1;
            int bracketDepth = 1;
            int j = arrStart;
            while (j < block.length() && bracketDepth > 0) {
                char c = block.charAt(j);
                if (c == '[') bracketDepth++;
                else if (c == ']') bracketDepth--;
                j++;
            }
            String arrContent = block.substring(arrStart, j - 1);
            java.util.List<String> classes = parseEntrypointArray(arrContent);
            if (!classes.isEmpty()) {
                result.put(key, classes);
            }
            i = j;
        }
    }

    /**
     * Parse an entrypoint array value. Each element can be:
     * - "com.example.MyClass" (plain string)
     * - {"adapter":"kotlin","value":"com.example.MyClass"} (object with value field)
     */
    private static java.util.List<String> parseEntrypointArray(String arr) {
        java.util.List<String> list = new java.util.ArrayList<>();
        int i = 0;
        while (i < arr.length()) {
            while (i < arr.length() && (Character.isWhitespace(arr.charAt(i)) || arr.charAt(i) == ',')) i++;
            if (i >= arr.length()) break;
            char c = arr.charAt(i);
            if (c == '"') {
                // Plain string
                int end = arr.indexOf('"', i + 1);
                if (end != -1) {
                    list.add(arr.substring(i + 1, end));
                    i = end + 1;
                } else {
                    break;
                }
            } else if (c == '{') {
                // Object: find the "value" field
                int close = arr.indexOf('}', i);
                if (close != -1) {
                    String obj = arr.substring(i + 1, close);
                    // Extract the "value" key
                    int valIdx = obj.indexOf("\"value\"");
                    if (valIdx == -1) valIdx = obj.indexOf("\"class\"");
                    if (valIdx != -1) {
                        int colonIdx = obj.indexOf(':', valIdx);
                        if (colonIdx != -1) {
                            int qStart = obj.indexOf('"', colonIdx + 1);
                            if (qStart != -1) {
                                int qEnd = obj.indexOf('"', qStart + 1);
                                if (qEnd != -1) {
                                    list.add(obj.substring(qStart + 1, qEnd));
                                }
                            }
                        }
                    }
                    i = close + 1;
                } else {
                    break;
                }
            } else {
                i++;
            }
        }
        return list;
    }

    public static String parseTopLevelJsonString(String json, String key) {
        int depth = 0;
        boolean inQuote = false;
        StringBuilder keyBuilder = new StringBuilder();
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                if (inQuote) {
                    keyBuilder.setLength(0);
                } else {
                    if (depth == 1 && keyBuilder.toString().equals(key)) {
                        i++;
                        while (i < json.length() && json.charAt(i) != ':') {
                            i++;
                        }
                        i++;
                        while (i < json.length() && json.charAt(i) != '"') {
                            if (json.charAt(i) == '[' || json.charAt(i) == '{') {
                                return null;
                            }
                            i++;
                        }
                        i++;
                        StringBuilder valBuilder = new StringBuilder();
                        while (i < json.length() && json.charAt(i) != '"') {
                            valBuilder.append(json.charAt(i));
                            i++;
                        }
                        return valBuilder.toString();
                    }
                }
            } else if (inQuote) {
                keyBuilder.append(c);
            } else {
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }
            i++;
        }
        return null;
    }

    public static java.util.List<String> parseTopLevelEntrypoints(String json, String key) {
        java.util.List<String> list = new java.util.ArrayList<>();
        int depth = 0;
        boolean inQuote = false;
        StringBuilder keyBuilder = new StringBuilder();
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                if (inQuote) {
                    keyBuilder.setLength(0);
                } else {
                    if (depth == 1 && keyBuilder.toString().equals(key)) {
                        i++;
                        while (i < json.length() && json.charAt(i) != ':') {
                            i++;
                        }
                        i++;
                        while (i < json.length() && json.charAt(i) != '"' && json.charAt(i) != '[' && json.charAt(i) != '{') {
                            i++;
                        }
                        if (json.charAt(i) == '"') {
                            i++;
                            StringBuilder valBuilder = new StringBuilder();
                            while (i < json.length() && json.charAt(i) != '"') {
                                valBuilder.append(json.charAt(i));
                                i++;
                            }
                            list.add(valBuilder.toString());
                            return list;
                        } else if (json.charAt(i) == '[') {
                            i++;
                            int arrayDepth = 1;
                            StringBuilder elementBuilder = new StringBuilder();
                            while (i < json.length() && arrayDepth > 0) {
                                char ac = json.charAt(i);
                                if (ac == '[') arrayDepth++;
                                else if (ac == ']') {
                                    arrayDepth--;
                                    if (arrayDepth == 0) {
                                        String elem = elementBuilder.toString().trim();
                                        if (!elem.isEmpty()) {
                                            processEntrypointElement(elem, list);
                                        }
                                        break;
                                    }
                                } else if (ac == ',' && arrayDepth == 1) {
                                    String elem = elementBuilder.toString().trim();
                                    processEntrypointElement(elem, list);
                                    elementBuilder.setLength(0);
                                    i++;
                                    continue;
                                }
                                elementBuilder.append(ac);
                                i++;
                            }
                            return list;
                        }
                    }
                }
            } else if (inQuote) {
                keyBuilder.append(c);
            } else {
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }
            i++;
        }
        return list;
    }

    private static void processEntrypointElement(String elem, java.util.List<String> list) {
        if (elem.startsWith("{")) {
            int valIdx = elem.indexOf("\"value\"");
            if (valIdx == -1) {
                valIdx = elem.indexOf("\"config\"");
            }
            if (valIdx != -1) {
                int colon = elem.indexOf(":", valIdx);
                if (colon != -1) {
                    int qStart = elem.indexOf("\"", colon);
                    if (qStart != -1) {
                        int qEnd = elem.indexOf("\"", qStart + 1);
                        if (qEnd != -1) {
                            list.add(elem.substring(qStart + 1, qEnd));
                        }
                    }
                }
            }
        } else if (elem.startsWith("\"") && elem.endsWith("\"")) {
            list.add(elem.substring(1, elem.length() - 1));
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Verg Connector initialized!");
    }

    @SubscribeEvent
    public void onServerAboutToStart(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) {
        LOGGER.info("[Verg Connector] Triggering Fabric SERVER_STARTING lifecycle event");
        for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting handler : net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.getHandlers()) {
            handler.onServerStarting(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        LOGGER.info("[Verg Connector] Triggering Fabric SERVER_STARTED lifecycle event");
        for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted handler : net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.getHandlers()) {
            handler.onServerStarted(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        LOGGER.info("[Verg Connector] Triggering Fabric SERVER_STOPPING lifecycle event");
        for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping handler : net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.getHandlers()) {
            handler.onServerStopping(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        LOGGER.info("[Verg Connector] Triggering Fabric SERVER_STOPPED lifecycle event");
        for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped handler : net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.getHandlers()) {
            handler.onServerStopped(event.getServer());
        }
    }

    @SubscribeEvent
    public void onAddReloadListener(net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        LOGGER.info("[Verg Connector] Registering Fabric server resource reload listeners");
        net.fabricmc.fabric.api.resource.ResourceManagerHelper serverHelper = net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.SERVER_DATA);
        for (net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener listener : serverHelper.getListeners()) {
            event.addListener(listener);
        }
    }

    // ─── Fabric ServerTickEvents bridge ───────────────────────────────────────
    @SubscribeEvent
    public void onServerTickStart(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) {
        for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.StartTick h : net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_SERVER_TICK.getHandlers()) {
            try { h.onStartTick(event.getServer()); } catch (Exception e) { LOGGER.warn("[Verg] START_SERVER_TICK handler error", e); }
        }
    }

    @SubscribeEvent
    public void onServerTickEnd(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick h : net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.getHandlers()) {
            try { h.onEndTick(event.getServer()); } catch (Exception e) { LOGGER.warn("[Verg] END_SERVER_TICK handler error", e); }
        }
    }

    @SubscribeEvent
    public void onLevelTickStart(net.neoforged.neoforge.event.tick.LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.StartWorldTick h : net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_WORLD_TICK.getHandlers()) {
                try { h.onStartTick(sl); } catch (Exception e) { LOGGER.warn("[Verg] START_WORLD_TICK handler error", e); }
            }
        }
    }

    @SubscribeEvent
    public void onLevelTickEnd(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndWorldTick h : net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.getHandlers()) {
                try { h.onEndTick(sl); } catch (Exception e) { LOGGER.warn("[Verg] END_WORLD_TICK handler error", e); }
            }
        }
    }

    // ─── Fabric ServerWorldEvents bridge ──────────────────────────────────────
    @SubscribeEvent
    public void onLevelLoad(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            net.minecraft.server.MinecraftServer srv = sl.getServer();
            for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.Load h : net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.getHandlers()) {
                try { h.onWorldLoad(srv, sl); } catch (Exception e) { LOGGER.warn("[Verg] WORLD_LOAD handler error", e); }
            }
        }
    }

    @SubscribeEvent
    public void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            net.minecraft.server.MinecraftServer srv = sl.getServer();
            for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.Unload h : net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.getHandlers()) {
                try { h.onWorldUnload(srv, sl); } catch (Exception e) { LOGGER.warn("[Verg] WORLD_UNLOAD handler error", e); }
            }
        }
    }

    // ─── Fabric ServerEntityEvents bridge ─────────────────────────────────────
    @SubscribeEvent
    public void onEntityJoinLevel(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.EntityLoad h : net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.getHandlers()) {
                try { h.onLoad(event.getEntity(), sl); } catch (Exception e) { LOGGER.warn("[Verg] ENTITY_LOAD handler error", e); }
            }
        }
    }

    @SubscribeEvent
    public void onEntityLeaveLevel(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.EntityUnload h : net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.getHandlers()) {
                try { h.onUnload(event.getEntity(), sl); } catch (Exception e) { LOGGER.warn("[Verg] ENTITY_UNLOAD handler error", e); }
            }
        }
    }

    // ─── Fabric ServerPlayConnectionEvents bridge ──────────────────────────────
    @SubscribeEvent
    public void onPlayerJoin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp && sp.getServer() != null) {
            for (net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join h : net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.getHandlers()) {
                try { h.onPlayReady(sp.connection, sp.getServer()); } catch (Exception e) { LOGGER.warn("[Verg] PLAY_JOIN handler error", e); }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp && sp.getServer() != null) {
            for (net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect h : net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.getHandlers()) {
                try { h.onPlayDisconnect(sp.connection, sp.getServer()); } catch (Exception e) { LOGGER.warn("[Verg] PLAY_DISCONNECT handler error", e); }
            }
        }
    }

    // ─── Fabric Player Interaction Callbacks bridge ────────────────────────────
    @SubscribeEvent
    public void onPlayerRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        for (net.fabricmc.fabric.api.event.player.UseBlockCallback.Event h : net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.getHandlers()) {
            try {
                net.minecraft.world.InteractionResult result = h.interact(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
                if (result != net.minecraft.world.InteractionResult.PASS) {
                    event.setCanceled(true);
                    event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
                    break;
                }
            } catch (Exception e) { LOGGER.warn("[Verg] USE_BLOCK handler error", e); }
        }
    }

    @SubscribeEvent
    public void onPlayerRightClickItem(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        for (net.fabricmc.fabric.api.event.player.UseItemCallback.Event h : net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.getHandlers()) {
            try {
                net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> result =
                    h.interact(event.getEntity(), event.getLevel(), event.getHand());
                if (result.getResult() != net.minecraft.world.InteractionResult.PASS) {
                    event.setCanceled(true);
                    break;
                }
            } catch (Exception e) { LOGGER.warn("[Verg] USE_ITEM handler error", e); }
        }
    }

    @SubscribeEvent
    public void onPlayerAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        for (net.fabricmc.fabric.api.event.player.AttackEntityCallback.Event h : net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.getHandlers()) {
            try {
                net.minecraft.world.InteractionResult result = h.interact(
                    event.getEntity(), event.getEntity().level(),
                    net.minecraft.world.InteractionHand.MAIN_HAND, event.getTarget(), java.util.Optional.empty());
                if (result == net.minecraft.world.InteractionResult.FAIL) {
                    event.setCanceled(true);
                    break;
                }
            } catch (Exception e) { LOGGER.warn("[Verg] ATTACK_ENTITY handler error", e); }
        }
    }

    // ─── Fabric CommandRegistrationCallback bridge ─────────────────────────────
    @SubscribeEvent
    public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        for (net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.Event h : net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.getHandlers()) {
            try { h.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()); }
            catch (Exception e) { LOGGER.warn("[Verg] COMMAND_REGISTRATION handler error", e); }
        }
    }
}
