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
                
        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outputJar).build()) {
            outputConsumer.addNonClassFiles(inputJar, NonClassCopyMode.FIX_META_INF, remapper);
            remapper.readInputs(inputJar);
            remapper.apply(outputConsumer);
        } finally {
            remapper.finish();
        }
        
        System.out.println("[Verg Connector] Remapping complete for: " + outputJar.getFileName());
    }
}
