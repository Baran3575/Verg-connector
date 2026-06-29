package com.baran3575.vergconnector.remapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URL;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import java.util.Map;

public class MappingManager {
    
    private static final String INTERMEDIARY_URL = "https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/1.21.1.tiny";
    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String MAPPING_FILE_NAME = "intermediary-mojmap-1.21.1.tiny";

    public static Path getMappingsFile() throws IOException {
        Path cacheDir = FMLPaths.GAMEDIR.get().resolve(".vergconnector");
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
        
        Path mappingFile = cacheDir.resolve(MAPPING_FILE_NAME);
        if (Files.exists(mappingFile)) {
            return mappingFile;
        }

        System.out.println("[Verg Connector] Generating merged intermediary-mojmap mappings...");
        Path intermediaryFile = cacheDir.resolve("intermediary-1.21.1.tiny");
        Path mojmapFile = cacheDir.resolve("mojmap-1.21.1.txt");

        try {
            if (!Files.exists(intermediaryFile)) {
                System.out.println("[Verg Connector] Downloading Intermediary mappings...");
                downloadFile(INTERMEDIARY_URL, intermediaryFile);
            }

            if (!Files.exists(mojmapFile)) {
                System.out.println("[Verg Connector] Fetching Mojang mappings URL...");
                String mojmapUrl = getMojmapUrl("1.21.1");
                System.out.println("[Verg Connector] Downloading Mojang mappings...");
                downloadFile(mojmapUrl, mojmapFile);
            }

            System.out.println("[Verg Connector] Merging mappings using mapping-io...");
            MemoryMappingTree tree = new MemoryMappingTree();
            
            // Read Intermediary
            MappingReader.read(intermediaryFile, tree);

            // Read Mojmap and merge. 
            // ProGuard reader produces `source` and `target` default namespaces.
            // We need `target` to map to `official` and `source` to map to `named`.
            // Then we can switch the source namespace to `official`.
            MappingReader.read(mojmapFile, new MappingNsRenamer(new MappingSourceNsSwitch(tree, "official", true), Map.of("source", "named", "target", "official")));
            
            try (MappingWriter writer = MappingWriter.create(mappingFile, MappingFormat.TINY_2_FILE)) {
                tree.accept(writer);
            }
            System.out.println("[Verg Connector] Successfully generated mappings at " + mappingFile);
            return mappingFile;
        } catch (Exception e) {
            Files.deleteIfExists(mappingFile);
            throw new IOException("Failed to generate mappings: " + e.getMessage(), e);
        }
    }

    private static String getMojmapUrl(String version) throws IOException {
        try (Reader reader = new InputStreamReader(new URL(MANIFEST_URL).openStream())) {
            JsonObject manifest = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement elem : manifest.getAsJsonArray("versions")) {
                JsonObject v = elem.getAsJsonObject();
                if (version.equals(v.get("id").getAsString())) {
                    String versionJsonUrl = v.get("url").getAsString();
                    try (Reader vReader = new InputStreamReader(new URL(versionJsonUrl).openStream())) {
                        JsonObject vJson = JsonParser.parseReader(vReader).getAsJsonObject();
                        return vJson.getAsJsonObject("downloads").getAsJsonObject("client_mappings").get("url").getAsString();
                    }
                }
            }
        }
        throw new IOException("Could not find mojmap URL for version " + version);
    }

    private static void downloadFile(String urlString, Path destination) throws IOException {
        try (InputStream in = new URL(urlString).openStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
