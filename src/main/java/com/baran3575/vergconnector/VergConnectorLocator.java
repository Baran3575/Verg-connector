package com.baran3575.vergconnector;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import net.neoforged.neoforgespi.locating.IModLocator;
import net.neoforged.neoforgespi.locating.IModFile;

public class VergConnectorLocator implements IModLocator {
    @Override
    public List<IModFile> scanMods() {
        System.out.println("[Verg Connector] Scanning for Fabric and Forge mods...");
        return List.of();
    }

    @Override
    public String name() {
        return "Verg Connector Locator";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
        // No-op
    }

    @Override
    public Path findPath(IModFile modFile, String... path) {
        return null;
    }

    @Override
    public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {
        // No-op
    }

    @Override
    public Optional<Manifest> findManifest(Path file) {
        return Optional.empty();
    }
}
