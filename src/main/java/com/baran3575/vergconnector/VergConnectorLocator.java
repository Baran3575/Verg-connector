package com.baran3575.vergconnector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VergConnectorLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger("VergConnectorLocator");

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        LOGGER.info("Scanning mods directory for Fabric and Forge mods...");

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
                          LOGGER.error("Failed to process jar: {}. Error: {}", path, e.getMessage());
                      }
                  });
        } catch (IOException e) {
            LOGGER.error("Failed to list mods directory: {}", e.getMessage());
        }
    }

    private void processJar(Path path, IDiscoveryPipeline pipeline) throws Exception {
        boolean isFabric = false;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path.toFile())) {
            if (zip.getEntry("fabric.mod.json") != null) {
                isFabric = true;
            }
        }
        
        Path finalPath = path;
        if (isFabric) {
            LOGGER.info("Found Fabric mod: {}", path.getFileName());
            try {
                Path mappings = com.baran3575.vergconnector.remapper.MappingManager.getMappingsFile();
                Path cacheDir = FMLPaths.GAMEDIR.get().resolve(".vergconnector").resolve("remapped");
                if (!Files.exists(cacheDir)) Files.createDirectories(cacheDir);
                
                Path remappedPath = cacheDir.resolve(path.getFileName().toString());
                boolean needsRemap = !Files.exists(remappedPath);
                
                String currentVer = "v10";
                Path verFile = cacheDir.resolve(".remapper_version");
                
                if (!needsRemap) {
                    long origTime = Files.getLastModifiedTime(path).toMillis();
                    long remapTime = Files.getLastModifiedTime(remappedPath).toMillis();
                    if (origTime > remapTime) {
                        LOGGER.info("Original mod {} has changed. Re-remapping...", path.getFileName());
                        Files.deleteIfExists(remappedPath);
                        needsRemap = true;
                    }
                }
                
                boolean versionMatch = false;
                if (Files.exists(verFile)) {
                    try {
                        String savedVer = Files.readString(verFile).trim();
                        if (currentVer.equals(savedVer)) {
                            versionMatch = true;
                        }
                    } catch (IOException e) {}
                }
                
                if (!versionMatch) {
                    LOGGER.info("Remapper version changed or missing. Invalidating cache...");
                    try (Stream<Path> s = Files.list(cacheDir)) {
                        s.filter(p -> !p.getFileName().toString().equals(".remapper_version")).forEach(p -> {
                            try {
                                if (!Files.isDirectory(p)) {
                                    Files.deleteIfExists(p);
                                }
                            } catch (IOException e) {}
                        });
                    } catch (IOException e) {}
                    try {
                        Files.writeString(verFile, currentVer);
                    } catch (IOException e) {}
                    needsRemap = true;
                }
                
                if (needsRemap) {
                    com.baran3575.vergconnector.remapper.JarRemapper.remapJar(path, remappedPath, mappings);
                }
                finalPath = remappedPath;
            } catch (Exception e) {
                LOGGER.error("Failed to remap Fabric mod {}: {}", path.getFileName(), e.getMessage());
            }
            
            cpw.mods.jarhandling.JarContents contents = com.baran3575.vergconnector.jarhandling.FabricJarContentsWrapper.createJarContents(finalPath);
            if (contents != null) {
                pipeline.addJarContent(contents, ModFileDiscoveryAttributes.DEFAULT.withLocator(this), IncompatibleFileReporting.IGNORE);
            }
        }
    }
}
