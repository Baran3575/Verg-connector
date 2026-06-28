package net.fabricmc.loader.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.metadata.ModMetadata;

public interface ModContainer {
    ModMetadata getMetadata();
    List<Path> getRootPaths();
    default Path getRootPath() {
        return getRootPaths().get(0);
    }
    default Path getPath(String path) {
        return getRootPath().resolve(path);
    }
    Optional<ModContainer> getContainingMod();
}
