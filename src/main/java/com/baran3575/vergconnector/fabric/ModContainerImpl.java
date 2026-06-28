package com.baran3575.vergconnector.fabric;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

public class ModContainerImpl implements ModContainer {
    private final ModMetadata metadata;
    private final Path rootPath;

    public ModContainerImpl(ModMetadata metadata, Path rootPath) {
        this.metadata = metadata;
        this.rootPath = rootPath;
    }

    @Override
    public ModMetadata getMetadata() {
        return metadata;
    }

    @Override
    public List<Path> getRootPaths() {
        return Collections.singletonList(rootPath);
    }

    @Override
    public Optional<ModContainer> getContainingMod() {
        return Optional.empty();
    }
}
