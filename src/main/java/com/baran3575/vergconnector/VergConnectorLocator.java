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

public class VergConnectorLocator implements IModFileCandidateLocator {

    static {
        System.out.println("[VergConnectorLocator] Class loaded!");
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        System.out.println("[VergConnectorLocator] findCandidates called!");

        Path modsDir = FMLPaths.MODSDIR.get();
        System.out.println("[VergConnectorLocator] MODSDIR = " + modsDir);
        if (!Files.exists(modsDir)) {
            System.out.println("[VergConnectorLocator] MODSDIR does not exist, returning");
            return;
        }

        try (Stream<Path> stream = Files.list(modsDir)) {
            stream.filter(path -> path.toString().endsWith(".jar"))
                  .forEach(path -> {
                      try {
                          processJar(path, pipeline);
                      } catch (Exception e) {
                          System.out.println("[VergConnectorLocator] ERROR processing " + path.getFileName() + ": " + e.getMessage());
                          e.printStackTrace(System.out);
                      }
                  });
        } catch (IOException e) {
            System.out.println("[VergConnectorLocator] ERROR listing mods directory: " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    private void processJar(Path path, IDiscoveryPipeline pipeline) throws Exception {
        boolean isFabric = false;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path.toFile())) {
            if (zip.getEntry("fabric.mod.json") != null) {
                isFabric = true;
            }
        }

        if (!isFabric) return;

        System.out.println("[VergConnectorLocator] Found Fabric mod: " + path.getFileName());

        Path finalPath = path;
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
                    System.out.println("[VergConnectorLocator] Original mod " + path.getFileName() + " has changed. Re-remapping...");
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
                System.out.println("[VergConnectorLocator] Remapper version changed or missing. Invalidating cache...");
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
                System.out.println("[VergConnectorLocator] Remapping " + path.getFileName() + "...");
                com.baran3575.vergconnector.remapper.JarRemapper.remapJar(path, remappedPath, mappings);
                System.out.println("[VergConnectorLocator] Remapping complete for " + path.getFileName());
            }
            finalPath = remappedPath;
        } catch (Exception e) {
            System.out.println("[VergConnectorLocator] WARNING: Failed to remap " + path.getFileName() + ": " + e.getMessage() + " — using original jar");
            finalPath = path;
        }

        cpw.mods.jarhandling.JarContents contents = com.baran3575.vergconnector.jarhandling.FabricJarContentsWrapper.createJarContents(finalPath);
        if (contents != null) {
            System.out.println("[VergConnectorLocator] Adding " + path.getFileName() + " to NeoForge pipeline");
            pipeline.addJarContent(contents, ModFileDiscoveryAttributes.DEFAULT.withLocator(this), IncompatibleFileReporting.IGNORE);
            System.out.println("[VergConnectorLocator] Successfully added " + path.getFileName() + " to pipeline");
        } else {
            System.out.println("[VergConnectorLocator] ERROR: FabricJarContentsWrapper returned null for " + path.getFileName());
        }
    }
}
