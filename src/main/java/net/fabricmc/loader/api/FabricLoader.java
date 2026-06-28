package net.fabricmc.loader.api;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import net.fabricmc.api.EnvType;

public interface FabricLoader {
    static FabricLoader getInstance() {
        return com.baran3575.vergconnector.fabric.FabricLoaderImpl.INSTANCE;
    }

    boolean isModLoaded(String id);

    Optional<ModContainer> getModContainer(String id);

    Collection<ModContainer> getAllMods();

    Path getGameDir();

    Path getConfigDir();

    EnvType getEnvironmentType();

    boolean isDevelopmentEnvironment();
}
