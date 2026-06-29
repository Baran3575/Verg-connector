package com.baran3575.vergconnector.remapper;

import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import java.nio.file.Path;
import java.io.IOException;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyUtils;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLPaths;

public class JarRemapper {
    
    /**
     * Remaps a Fabric JAR using Intermediary mappings to Mojang mappings at runtime.
     * 
     * @param inputJar The path to the downloaded Fabric JAR (e.g. mods/Jade.jar)
     * @param outputJar The path to save the remapped JAR (e.g. vergconnector/remapped/Jade.jar)
     * @param mappingsFile The .tiny mappings file bridging Intermediary to Mojang
     */
    public static void remapJar(Path inputJar, Path outputJar, Path mappingsFile) throws IOException {
        System.out.println("[Verg Connector] Remapping Fabric Mod: " + inputJar.getFileName());

        Path cacheDir = mappingsFile.getParent().resolve("classpath_cache");
        if (!java.nio.file.Files.exists(cacheDir)) {
            java.nio.file.Files.createDirectories(cacheDir);
        }

        List<Path> originalClasspathJars = findClasspathJars();
        List<Path> intermediaryClasspathJars = new ArrayList<>();

        for (Path cpJar : originalClasspathJars) {
            String name = cpJar.getFileName().toString().toLowerCase();
            if ((name.contains("minecraft") && name.contains("client")) || name.contains("neoforge-21")) {
                try {
                    intermediaryClasspathJars.add(getOrGenerateIntermediaryJar(cpJar, cacheDir, mappingsFile));
                } catch (Exception e) {
                    System.err.println("[Verg Connector] Failed to generate intermediary jar for " + name + ": " + e.getMessage());
                    intermediaryClasspathJars.add(cpJar);
                }
            } else {
                intermediaryClasspathJars.add(cpJar);
            }
        }

        IMappingProvider provider = TinyUtils.createTinyMappingProvider(mappingsFile, "intermediary", "named");

        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(provider)
                .ignoreConflicts(true)
                .resolveMissing(true)
                .extension(new MixinExtension())
                .build();
                
        System.out.println("[Verg Connector] Providing " + intermediaryClasspathJars.size() + " intermediary classpath jars to TinyRemapper.");
        remapper.readClassPath(intermediaryClasspathJars.toArray(new Path[0]));
                
        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outputJar).build()) {
            outputConsumer.addNonClassFiles(inputJar, NonClassCopyMode.FIX_META_INF, remapper);
            remapper.readInputs(inputJar);
            remapper.apply(outputConsumer);
        } finally {
            remapper.finish();
        }
        
        System.out.println("[Verg Connector] Running ASM String Replacer on " + outputJar.getFileName());
        try {
            Map<String, String> stringMap = MappingManager.getIntermediaryToMojmap();
            ASMStringReplacer.processJar(outputJar, stringMap);
        } catch (Exception e) {
            System.err.println("[Verg Connector] ASM String Replacer failed: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("[Verg Connector] Remapping complete for: " + outputJar.getFileName());
    }

    private static Path getOrGenerateIntermediaryJar(Path originalJar, Path cacheDir, Path mappingsFile) throws IOException {
        String originalName = originalJar.getFileName().toString();
        Path remappedJar = cacheDir.resolve(originalName.replace(".jar", "-intermediary.jar"));
        if (java.nio.file.Files.exists(remappedJar) && java.nio.file.Files.size(remappedJar) > 0) {
            return remappedJar;
        }
        System.out.println("[Verg Connector] Generating intermediary classpath jar: " + remappedJar.getFileName());
        
        IMappingProvider provider = TinyUtils.createTinyMappingProvider(mappingsFile, "named", "intermediary");
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(provider)
                .ignoreConflicts(true)
                .build();
                
        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(remappedJar).build()) {
            remapper.readInputs(originalJar);
            remapper.apply(outputConsumer);
        } finally {
            remapper.finish();
        }
        return remappedJar;
    }

    private static List<Path> findClasspathJars() {
        List<Path> jars = new ArrayList<>();
        
        // 1. Check System Properties (java.class.path and jdk.module.path)
        String[] props = {"java.class.path", "jdk.module.path"};
        for (String prop : props) {
            String val = System.getProperty(prop);
            if (val != null && !val.isEmpty()) {
                for (String p : val.split(File.pathSeparator)) {
                    if (p.endsWith(".jar")) {
                        jars.add(Path.of(p));
                    }
                }
            }
        }

        // 2. Scan entire .minecraft folder (libraries and versions)
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path minecraftDir = gameDir;
        if (gameDir.getParent() != null && gameDir.getParent().getParent() != null) {
            minecraftDir = gameDir.getParent().getParent();
        }
        
        if (java.nio.file.Files.exists(minecraftDir)) {
            try (Stream<Path> stream = java.nio.file.Files.walk(minecraftDir, 8)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                      .filter(p -> {
                          String pathStr = p.toString().replace('\\', '/').toLowerCase();
                          // Exclude mods, cache, and remapper files to prevent loops
                          if (pathStr.contains("/mods/") || pathStr.contains("/cache/") || pathStr.contains("/.vergconnector/")) {
                              return false;
                          }
                          String name = p.getFileName().toString().toLowerCase();
                          return name.contains("client") || name.contains("neoforge") || name.contains("minecraft");
                      })
                      .forEach(jars::add);
            } catch (Exception e) {
                System.err.println("[Verg Connector] Failed to scan minecraft dir: " + e.getMessage());
            }
        }
        
        // Remove duplicates and ensure they exist
        return jars.stream().distinct().filter(java.nio.file.Files::exists).collect(Collectors.toList());
    }
}
