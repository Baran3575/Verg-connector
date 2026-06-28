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
    default Optional<Path> findPath(String path) {
        try {
            Path p = getRootPath().resolve(path.startsWith("/") ? path.substring(1) : path);
            return java.nio.file.Files.exists(p) ? Optional.of(p) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    Optional<ModContainer> getContainingMod();
}
