package com.baran3575.vergconnector;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.fml.loading.FMLPaths;

public class VergConnectorLocator implements IModFileCandidateLocator {

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        System.out.println("[Verg Connector] Scanning mods directory for Fabric and Forge mods...");
        Path modsDir = FMLPaths.MODSDIR.get();
        if (!Files.exists(modsDir)) {
            return;
        }

        try (Stream<Path> stream = Files.list(modsDir)) {
            stream.filter(path -> path.toString().endsWith(".jar"))
                  .forEach(path -> {
                      try {
                          processJar(path, pipeline);
                      } catch (Exception e) {
                          System.err.println("[Verg Connector] Failed to process jar: " + path + ". Error: " + e.getMessage());
                      }
                  });
        } catch (IOException e) {
            System.err.println("[Verg Connector] Failed to list mods directory: " + e.getMessage());
        }
    }

    private void processJar(Path path, IDiscoveryPipeline pipeline) throws Exception {
        cpw.mods.jarhandling.JarContents contents = com.baran3575.vergconnector.jarhandling.FabricJarContentsWrapper.createJarContents(path);
        if (contents != null) {
            System.out.println("[Verg Connector] Found Fabric mod: " + path.getFileName());
            pipeline.addJarContent(contents, ModFileDiscoveryAttributes.DEFAULT.withLocator(this), IncompatibleFileReporting.IGNORE);
        }
    }
}
