package com.baran3575.vergconnector;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import cpw.mods.modlauncher.api.ILaunchContext;

public class VergConnectorLocator implements IModFileCandidateLocator {
    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        System.out.println("[Verg Connector] Finding Fabric and Forge mods...");
    }

    @Override
    public String name() {
        return "Verg Connector Locator";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
        // No-op
    }
}
