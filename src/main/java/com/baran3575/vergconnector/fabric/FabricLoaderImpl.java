package com.baran3575.vergconnector.fabric;

import java.nio.file.Path;
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

    private FabricLoaderImpl() {}

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
        return mods.containsKey(id) || ModList.get().isLoaded(id);
    }

    @Override
    public Optional<ModContainer> getModContainer(String id) {
        return Optional.ofNullable(mods.get(id));
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
                Class<?> clazz = Class.forName(def.className);
                Object obj;
                try {
                    com.baran3575.vergconnector.mixin.RegistryHelper.UNFROZEN.set(true);
                    obj = clazz.getDeclaredConstructor().newInstance();
                } finally {
                    com.baran3575.vergconnector.mixin.RegistryHelper.UNFROZEN.set(false);
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
}
