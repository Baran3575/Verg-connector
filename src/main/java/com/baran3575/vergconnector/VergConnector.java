package com.baran3575.vergconnector;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;


@Mod(VergConnector.MODID)
public class VergConnector {
    public static final String MODID = "vergconnector";
    public static final Logger LOGGER = LogUtils.getLogger();
    
    public VergConnector(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[Verg Connector] Mod constructor invoked — VergConnector is loading!");
        // Run Fabric mod initialization BEFORE events are registered
        initializeFabricMods();

        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            VergConnectorClient.init(modEventBus);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[Verg Connector] Common setup complete!");
    }

    private void initializeFabricMods() {
        LOGGER.info("[Verg Connector] Initializing Fabric mods state and running main entrypoints...");
        discoverAndLoadFabricMods();
        runMainEntrypoints();
        printMixinConflicts();
    }

    private void discoverAndLoadFabricMods() {
        java.nio.file.Path modsDir = net.neoforged.fml.loading.FMLPaths.MODSDIR.get();
        if (!java.nio.file.Files.exists(modsDir)) {
            LOGGER.warn("[Verg Connector] Mods directory not found: {}", modsDir);
            return;
        }

        LOGGER.info("[Verg Connector] Scanning {} for Fabric mods...", modsDir);

        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(modsDir)) {
            stream.filter(path -> path.toString().endsWith(".jar"))
                  .forEach(jarPath -> {
                      try {
                          processFabricJar(jarPath);
                      } catch (Exception e) {
                          LOGGER.error("[Verg Connector] Failed to process {}: {}", jarPath.getFileName(), e.getMessage());
                      }
                  });
        } catch (java.io.IOException e) {
            LOGGER.error("[Verg Connector] Failed to list mods directory: {}", e.getMessage());
        }
    }

    private void addJarToSystemClassloader(java.nio.file.Path jarPath) {
        if (!java.nio.file.Files.exists(jarPath)) return;
        try {
            java.net.URL url = jarPath.toUri().toURL();
            java.lang.ClassLoader cl = java.lang.ClassLoader.getSystemClassLoader();
            while (cl != null) {
                if (cl instanceof java.net.URLClassLoader urlCL) {
                    java.lang.reflect.Method addURL = java.net.URLClassLoader.class.getDeclaredMethod("addURL", java.net.URL.class);
                    addURL.setAccessible(true);
                    addURL.invoke(urlCL, url);
                    LOGGER.info("[Verg Connector] Added {} to classloader: {}", jarPath.getFileName(), cl.getClass().getName());
                    return;
                }
                cl = cl.getParent();
            }
            LOGGER.warn("[Verg Connector] No URLClassLoader found in hierarchy to add {}", jarPath.getFileName());
        } catch (Exception e) {
            LOGGER.error("[Verg Connector] Failed to add {} to classpath: {}", jarPath.getFileName(), e.getMessage());
        }
    }

    private boolean hasFabricModJson(java.nio.file.Path jarPath) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jarPath.toFile())) {
            return zip.getEntry("fabric.mod.json") != null;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private String readZipEntry(java.nio.file.Path jarPath, String entryName) throws java.io.IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jarPath.toFile())) {
            java.util.zip.ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) return null;
            try (java.io.InputStream is = zip.getInputStream(entry)) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
    }

    private void registerMixinConfig(String configName, java.nio.file.Path jarPath) {
        // ponytail: DO NOT call Mixins.addConfiguration here. On NeoForge 1.21.1 the jar is not on
        // the app/system classloader (JPMS module loader, not URLClassLoader), so the mixin JSON
        // resource can't be read -> IllegalArgumentException -> MixinInitialisationError -> mod dies.
        // Mixins are already declared as [[mixins]] in the virtual neoforge.mods.toml
        // (FabricJarContentsWrapper), so SpongeMixin loads them from Jade's own mod resources.
        try {
            String json = readZipEntry(jarPath, configName);
            if (json == null) {
                LOGGER.debug("[Verg Connector] Mixin config '{}' not found in {}", configName, jarPath.getFileName());
                return;
            }
            LOGGER.info("[Verg Connector] Mixin config '{}' from {} will be loaded via virtual TOML [[mixins]]",
                configName, jarPath.getFileName());
        } catch (Exception e) {
            LOGGER.error("[Verg Connector] Failed to read mixin config '{}': {}", configName, e.getMessage());
        }
    }

    private void processFabricJar(java.nio.file.Path jarPath) {
        if (!hasFabricModJson(jarPath)) return;

        LOGGER.info("[Verg Connector] Found Fabric mod: {}", jarPath.getFileName());

        String jsonContent;
        try {
            jsonContent = readZipEntry(jarPath, "fabric.mod.json");
            if (jsonContent == null) return;
        } catch (java.io.IOException e) {
            LOGGER.error("[Verg Connector] Failed to read fabric.mod.json from {}", jarPath.getFileName());
            return;
        }

        String id = parseTopLevelJsonString(jsonContent, "id");
        String version = parseTopLevelJsonString(jsonContent, "version");
        String name = parseTopLevelJsonString(jsonContent, "name");
        String description = parseTopLevelJsonString(jsonContent, "description");

        if (id == null) id = "unknown";
        if (version == null) version = "1.0.0";
        if (name == null) name = id;
        if (description == null) description = "";

        // 1. Remap the JAR (intermediary -> mojmap)
        java.nio.file.Path jarToLoad = remapFabricJar(jarPath);

        // 2. Add to system classloader (makes classes and resources accessible to mixin system)
        addJarToSystemClassloader(jarToLoad);

        // 3. Register mixin configs
        java.util.List<String> mixinConfigs = parseTopLevelEntrypoints(jsonContent, "mixins");
        for (String mixinConfig : mixinConfigs) {
            registerMixinConfig(mixinConfig, jarToLoad);
        }

        // 4. Register mod and entrypoints in FabricLoaderImpl
        var metadata = new com.baran3575.vergconnector.fabric.ModMetadataImpl(id, version, name, description);
        var container = new com.baran3575.vergconnector.fabric.ModContainerImpl(metadata, jarPath);
        com.baran3575.vergconnector.fabric.FabricLoaderImpl.INSTANCE.registerMod(id, container);

        java.util.Map<String, java.util.List<String>> allEntrypoints = parseAllEntrypoints(jsonContent);
        for (java.util.Map.Entry<String, java.util.List<String>> entry : allEntrypoints.entrySet()) {
            String epKey = entry.getKey();
            for (String className : entry.getValue()) {
                com.baran3575.vergconnector.fabric.FabricLoaderImpl.INSTANCE.registerEntrypoint(epKey, className, container);
                LOGGER.debug("[Verg Connector] Registered entrypoint: key='{}', class='{}'", epKey, className);
            }
        }

        LOGGER.info("[Verg Connector] Loaded Fabric mod: {} ({}) — {} entrypoints, {} mixin configs",
            name, version, allEntrypoints.size(), mixinConfigs.size());
    }

    private java.nio.file.Path remapFabricJar(java.nio.file.Path jarPath) {
        try {
            java.nio.file.Path mappings = com.baran3575.vergconnector.remapper.MappingManager.getMappingsFile();
            java.nio.file.Path cacheDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve(".vergconnector").resolve("remapped");
            if (!java.nio.file.Files.exists(cacheDir)) {
                java.nio.file.Files.createDirectories(cacheDir);
            }

            java.nio.file.Path remappedPath = cacheDir.resolve(jarPath.getFileName().toString());
            boolean needsRemap = !java.nio.file.Files.exists(remappedPath);

            if (!needsRemap) {
                long origTime = java.nio.file.Files.getLastModifiedTime(jarPath).toMillis();
                long remapTime = java.nio.file.Files.getLastModifiedTime(remappedPath).toMillis();
                if (origTime > remapTime) {
                    LOGGER.info("[Verg Connector] {} has changed. Re-remapping...", jarPath.getFileName());
                    java.nio.file.Files.deleteIfExists(remappedPath);
                    needsRemap = true;
                }
            }

            if (needsRemap) {
                LOGGER.info("[Verg Connector] Remapping {}...", jarPath.getFileName());
                com.baran3575.vergconnector.remapper.JarRemapper.remapJar(jarPath, remappedPath, mappings);
                LOGGER.info("[Verg Connector] Remapped {} -> {}", jarPath.getFileName(), remappedPath);
            }

            return remappedPath;
        } catch (Exception e) {
            LOGGER.error("[Verg Connector] Remapping failed for {}, using original: {}", jarPath.getFileName(), e.getMessage());
            return jarPath;
        }
    }

    private void runMainEntrypoints() {
        java.util.List<net.fabricmc.loader.api.EntrypointContainer<net.fabricmc.api.ModInitializer>> mainContainers =
            com.baran3575.vergconnector.fabric.FabricLoaderImpl.INSTANCE.getEntrypointContainers("main", net.fabricmc.api.ModInitializer.class);
        for (var container : mainContainers) {
            try {
                LOGGER.info("[Verg Connector] Loading main entrypoint from mod: {}", container.getProvider().getMetadata().getId());
                com.baran3575.vergconnector.helper.RegistryHelper.UNFROZEN.set(true);
                container.getEntrypoint().onInitialize();
            } catch (Exception e) {
                LOGGER.error("[Verg Connector] Failed to initialize main entrypoint from mod: {}", container.getProvider().getMetadata().getId(), e);
            } finally {
                com.baran3575.vergconnector.helper.RegistryHelper.UNFROZEN.set(false);
            }
        }

        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.DEDICATED_SERVER) {
            java.util.List<net.fabricmc.loader.api.EntrypointContainer<net.fabricmc.api.DedicatedServerModInitializer>> serverContainers =
                com.baran3575.vergconnector.fabric.FabricLoaderImpl.INSTANCE.getEntrypointContainers("server", net.fabricmc.api.DedicatedServerModInitializer.class);
            for (var container : serverContainers) {
                try {
                    LOGGER.info("[Verg Connector] Loading server entrypoint from mod: {}", container.getProvider().getMetadata().getId());
                    com.baran3575.vergconnector.helper.RegistryHelper.UNFROZEN.set(true);
                    container.getEntrypoint().onInitializeServer();
                } catch (Exception e) {
                    LOGGER.error("[Verg Connector] Failed to initialize server entrypoint from mod: {}", container.getProvider().getMetadata().getId(), e);
                } finally {
                    com.baran3575.vergconnector.helper.RegistryHelper.UNFROZEN.set(false);
                }
            }
        }
    }

    private void printMixinConflicts() {
        java.util.List<String> conflicts = com.baran3575.vergconnector.helper.MixinConflictResolver.INSTANCE.getConflictingTargets();
        if (!conflicts.isEmpty()) {
            LOGGER.warn("[Verg Connector] ⚠ Detected mixin target conflicts across loaded Fabric mods:");
            for (String conflict : conflicts) {
                java.util.List<com.baran3575.vergconnector.helper.MixinConflictResolver.MixinEntry> entries =
                    com.baran3575.vergconnector.helper.MixinConflictResolver.INSTANCE.getEntriesFor(conflict);
                LOGGER.warn("  - Class '{}' is targeted by multiple mixins: {}", conflict, entries);
            }
        }
    }

    public static java.util.Map<String, java.util.List<String>> parseAllEntrypoints(String json) {
        java.util.Map<String, java.util.List<String>> result = new java.util.LinkedHashMap<>();
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
                        i++;
                        while (i < json.length() && json.charAt(i) != '{') i++;
                        if (i >= json.length()) break;
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

    private static void parseEntrypointBlock(String block, java.util.Map<String, java.util.List<String>> result) {
        int i = 0;
        while (i < block.length()) {
            int keyStart = block.indexOf('"', i);
            if (keyStart == -1) break;
            int keyEnd = block.indexOf('"', keyStart + 1);
            if (keyEnd == -1) break;
            String key = block.substring(keyStart + 1, keyEnd);
            i = keyEnd + 1;
            int colon = block.indexOf(':', i);
            if (colon == -1) break;
            i = colon + 1;
            while (i < block.length() && Character.isWhitespace(block.charAt(i))) i++;
            if (i >= block.length() || block.charAt(i) != '[') continue;
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

    private static java.util.List<String> parseEntrypointArray(String arr) {
        java.util.List<String> list = new java.util.ArrayList<>();
        int i = 0;
        while (i < arr.length()) {
            while (i < arr.length() && (Character.isWhitespace(arr.charAt(i)) || arr.charAt(i) == ',')) i++;
            if (i >= arr.length()) break;
            char c = arr.charAt(i);
            if (c == '"') {
                int end = arr.indexOf('"', i + 1);
                if (end != -1) {
                    list.add(arr.substring(i + 1, end));
                    i = end + 1;
                } else {
                    break;
                }
            } else if (c == '{') {
                int close = arr.indexOf('}', i);
                if (close != -1) {
                    String obj = arr.substring(i + 1, close);
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
}
