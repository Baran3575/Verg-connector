package com.baran3575.vergconnector.fabric;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLEnvironment;

public class FabricLoaderImpl implements FabricLoader {
    public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();

    private final Map<String, ModContainer> mods = new HashMap<>();

    private FabricLoaderImpl() {
        // Populated dynamically
    }

    public void registerMod(String id, ModContainer container) {
        mods.put(id, container);
    }

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
}
