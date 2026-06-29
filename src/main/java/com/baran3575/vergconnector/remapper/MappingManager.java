package com.baran3575.vergconnector.remapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URL;
import java.io.InputStream;
import java.io.IOException;
import net.neoforged.fml.loading.FMLPaths;

public class MappingManager {
    
    private static final String MAPPING_URL = "https://example.com/mappings/1.21.1/intermediary-mojmap.tiny"; // TODO: Use real URL
    private static final String MAPPING_FILE_NAME = "intermediary-mojmap-1.21.1.tiny";

    /**
     * Ensures the mapping file is downloaded and available locally.
     * @return The path to the local mappings file.
     */
    public static Path getMappingsFile() throws IOException {
        Path cacheDir = FMLPaths.GAMEDIR.get().resolve(".vergconnector");
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
        
        Path mappingFile = cacheDir.resolve(MAPPING_FILE_NAME);
        if (!Files.exists(mappingFile)) {
            System.out.println("[Verg Connector] Downloading mappings to " + mappingFile);
            downloadFile(MAPPING_URL, mappingFile);
        }
        return mappingFile;
    }

    private static void downloadFile(String urlString, Path destination) throws IOException {
        try (InputStream in = new URL(urlString).openStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
