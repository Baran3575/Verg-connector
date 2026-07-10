package com.baran3575.vergconnector.fabric;

import java.nio.file.Path;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.EntrypointContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLEnvironment;
import com.baran3575.vergconnector.VergConnector;

public class FabricLoaderImpl implements FabricLoader {
    public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();

    private final Map<String, ModContainer> mods = new ConcurrentHashMap<>();

    /**
     * Map of entrypoint key -> List of (className, provider) pairs.
     * Populated during mod initialization by VergConnector.initializeFabricMods().
     */
    private final Map<String, List<EntrypointEntry>> entrypointDefinitions = new ConcurrentHashMap<>();

    // Cache of instantiated entrypoints; cleared per key on first access
    private final Map<String, List<Object>> instantiatedEntrypoints = new ConcurrentHashMap<>();
    private final Map<String, List<EntrypointContainer<?>>> instantiatedContainers = new ConcurrentHashMap<>();

    public static class EntrypointEntry {
        public final String className;
        public final ModContainer provider;

        public EntrypointEntry(String className, ModContainer provider) {
            this.className = className;
            this.provider = provider;
        }
    }

    private FabricLoaderImpl() {
        // ponytail: pre-populate common Fabric API virtual mod containers to bypass mod dependency checks
        registerVirtualMod("fabric");
        registerVirtualMod("fabric-api");
        registerVirtualMod("fabric-api-base");
        registerVirtualMod("fabric-networking-api-v1");
        registerVirtualMod("fabric-rendering-v1");
        registerVirtualMod("fabric-rendering-data-attachment-v1");
        registerVirtualMod("fabric-lifecycle-events-v1");
        registerVirtualMod("fabric-events-interaction-v0");
        registerVirtualMod("fabric-resource-loader-v0");
        registerVirtualMod("fabric-screen-api-v1");
        registerVirtualMod("fabric-keybinding-api-v1");
        registerVirtualMod("fabric-registry-sync-v0");
        registerVirtualMod("fabric-command-api-v2");
        registerVirtualMod("fabric-item-group-api-v1");
        registerVirtualMod("fabric-loot-api-v2");
        registerVirtualMod("fabric-content-registries-v0");
        registerVirtualMod("fabric-transitive-access-wideners-v1");
        registerVirtualMod("minecraft");
    }

    private void registerVirtualMod(String id) {
        var metadata = new ModMetadataImpl(id, "1.0.0", id, "Virtual Fabric API Module");
        var container = new ModContainerImpl(metadata, java.nio.file.Paths.get("."));
        mods.put(id, container);
    }

    // ─── Registration API (called from VergConnector) ─────────────────────────

    public void registerMod(String id, ModContainer container) {
        mods.put(id, container);
    }

    /**
     * Register a custom entrypoint class under the given key, associated with the
     * declaring mod container. Called for every key found in fabric.mod.json's
     * "entrypoints" object (including "jade", "main", "client", "server", etc.).
     */
     public void registerEntrypoint(String key, String className, ModContainer provider) {
         entrypointDefinitions.computeIfAbsent(key, k -> new ArrayList<>())
                              .add(new EntrypointEntry(className, provider));
     }

     // ─── FabricLoader API ─────────────────────────────────────────────────────

     @Override
     public boolean isModLoaded(String id) {
         if (id.equals("fabric") || id.equals("fabric-api") || id.startsWith("fabric-")) {
             return true;
         }
         return mods.containsKey(id) || ModList.get().isLoaded(id);
     }

     @Override
     public Optional<ModContainer> getModContainer(String id) {
         ModContainer container = mods.get(id);
         if (container != null) {
             return Optional.of(container);
         }
         if (id.equals("fabric") || id.equals("fabric-api") || id.startsWith("fabric-")) {
             var metadata = new ModMetadataImpl(id, "1.0.0", id, "Virtual Fabric API Module");
             var virtualContainer = new ModContainerImpl(metadata, java.nio.file.Paths.get("."));
             mods.put(id, virtualContainer);
             return Optional.of(virtualContainer);
         }
         if (ModList.get().isLoaded(id)) {
             var metadata = new ModMetadataImpl(id, "1.0.0", id, "Wrapped NeoForge Mod");
             var wrappedContainer = new ModContainerImpl(metadata, java.nio.file.Paths.get("."));
             mods.put(id, wrappedContainer);
             return Optional.of(wrappedContainer);
         }
         return Optional.empty();
     }

     @Override
     public Collection<ModContainer> getAllMods() {
         return mods.values();
     }

    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public EnvType getEnvironmentType() {
        return FMLEnvironment.dist.isClient() ? EnvType.CLIENT : EnvType.SERVER;
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.production;
    }

    @Override
    public ObjectShare getObjectShare() {
        return ObjectShareImpl.INSTANCE;
    }

    // ─── Entrypoint discovery ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        List<Object> instances = instantiatedEntrypoints.computeIfAbsent(key, k -> buildInstances(key));
        List<T> filtered = new ArrayList<>();
        for (Object inst : instances) {
            if (type.isInstance(inst)) {
                filtered.add((T) inst);
            }
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        // Ensure instances are built first so containers share same objects
        instantiatedEntrypoints.computeIfAbsent(key, k -> buildInstances(key));

        List<EntrypointContainer<?>> containers = instantiatedContainers.computeIfAbsent(key, k -> {
            List<EntrypointContainer<?>> list = new ArrayList<>();
            List<EntrypointEntry> defs = entrypointDefinitions.getOrDefault(k, List.of());
            List<Object> instances = instantiatedEntrypoints.get(k);
            for (int i = 0; i < defs.size(); i++) {
                final EntrypointEntry def = defs.get(i);
                final Object inst = (instances != null && i < instances.size()) ? instances.get(i) : null;
                if (inst != null) {
                    list.add(new EntrypointContainer<Object>() {
                        @Override public Object getEntrypoint() { return inst; }
                        @Override public ModContainer getProvider() { return def.provider; }
                    });
                }
            }
            return list;
        });

        List<EntrypointContainer<T>> filtered = new ArrayList<>();
        for (EntrypointContainer<?> c : containers) {
            if (type.isInstance(c.getEntrypoint())) {
                filtered.add((EntrypointContainer<T>) c);
            }
        }
        return filtered;
    }

    // ─── Internal helpers ──────────────────────────────────────────────────────

    private List<Object> buildInstances(String key) {
        List<Object> list = new ArrayList<>();
        List<EntrypointEntry> defs = entrypointDefinitions.getOrDefault(key, List.of());
        for (EntrypointEntry def : defs) {
            try {
                // ponytail: the Fabric mod is a real NeoForge mod added by the locator with its own
                // module classloader. Load its entrypoint via that classloader (resolved from ModList
                // by mod id), NOT the context classloader — otherwise Jade's classes are invisible.
                ClassLoader cl = resolveClassLoader(def.provider.getMetadata().getId());
                Class<?> clazz = Class.forName(def.className, true, cl);
                Object obj;
                try {
                    com.baran3575.vergconnector.helper.RegistryHelper.UNFROZEN.set(true);
                    obj = clazz.getDeclaredConstructor().newInstance();
                } finally {
                    com.baran3575.vergconnector.helper.RegistryHelper.UNFROZEN.set(false);
                }
                list.add(obj);
            } catch (Exception e) {
                VergConnector.LOGGER.error(
                    "[Verg Connector] Failed to instantiate entrypoint '{}' for key '{}': {}",
                    def.className, key, e.getMessage(), e);
            }
        }
        return list;
    }

    /**
     * Resolve the classloader for a Fabric mod. The mod is loaded as a real NeoForge mod (via
     * VergConnectorLocator), so its classes live on its own ModFile classloader, obtainable from
     * ModList. Falls back to the context / defining loader when not found.
     */
    private ClassLoader resolveClassLoader(String modId) {
        try {
            var modFileInfo = net.neoforged.fml.ModList.get().getModFileById(modId);
            if (modFileInfo != null) {
                var jarContents = modFileInfo.getFile().getSecureJar();
                // SecureJar implements JarContents; the remapped Fabric jar is its primary path.
                if (jarContents != null) {
                    Path jarPath = jarContents.getPrimaryPath();
                    URL url = jarPath.toUri().toURL();
                    ClassLoader parent = Thread.currentThread().getContextClassLoader();
                    if (parent == null) {
                        parent = net.minecraft.core.Registry.class.getClassLoader();
                    }
                    return new java.net.URLClassLoader(new URL[]{url}, parent);
                }
            }
        } catch (Exception ignored) {
            // fall through to fallback
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        return ctx != null ? ctx : FabricLoaderImpl.class.getClassLoader();
    }
}
