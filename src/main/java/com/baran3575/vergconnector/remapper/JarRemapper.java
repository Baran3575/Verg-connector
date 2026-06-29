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

        IMappingProvider provider = TinyUtils.createTinyMappingProvider(mappingsFile, "intermediary", "named");

        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(provider)
                .ignoreConflicts(true)
                .extension(new MixinExtension())
                .build();
                
        List<Path> classpathJars = findClasspathJars();
        System.out.println("[Verg Connector] Providing " + classpathJars.size() + " classpath jars to TinyRemapper.");
        remapper.readClassPath(classpathJars.toArray(new Path[0]));
                
        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outputJar).build()) {
            outputConsumer.addNonClassFiles(inputJar, NonClassCopyMode.FIX_META_INF, remapper);
            remapper.readInputs(inputJar);
            remapper.apply(outputConsumer);
        } finally {
            remapper.finish();
        }
        
        System.out.println("[Verg Connector] Remapping complete for: " + outputJar.getFileName());
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

        // 2. Scan Libraries folder as fallback
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path libs = gameDir.resolve("libraries");
        if (!java.nio.file.Files.exists(libs) && gameDir.getParent() != null) {
            libs = gameDir.getParent().resolve("libraries");
            if (!java.nio.file.Files.exists(libs) && gameDir.getParent().getParent() != null) {
                libs = gameDir.getParent().getParent().resolve("libraries");
            }
        }
        
        if (java.nio.file.Files.exists(libs)) {
            try (Stream<Path> stream = java.nio.file.Files.walk(libs)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                      .filter(p -> {
                          String name = p.getFileName().toString().toLowerCase();
                          // Only add core minecraft and neoforge jars to avoid loading too many unnecessary jars
                          return (name.contains("minecraft") && name.contains("client")) || name.contains("neoforge-21");
                      })
                      .forEach(jars::add);
            } catch (Exception e) {
                System.err.println("[Verg Connector] Failed to scan libraries: " + e.getMessage());
            }
        }
        
        // Remove duplicates and ensure they exist
        return jars.stream().distinct().filter(java.nio.file.Files::exists).collect(Collectors.toList());
    }
}
