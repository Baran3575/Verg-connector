package com.baran3575.vergconnector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;
import cpw.mods.jarhandling.JarContents;

public class FabricJarContentsWrapper extends JarContents {
    private final JarContents delegate;
    private Path tempToml;

    public FabricJarContentsWrapper(JarContents delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<Path> findFile(String name) {
        if (name.equals("META-INF/neoforge.mods.toml") || name.equals("neoforge.mods.toml")) {
            if (tempToml == null) {
                try {
                    tempToml = generateVirtualToml();
                } catch (IOException e) {
                    System.err.println("[Verg Connector] Failed to generate virtual toml: " + e.getMessage());
                    return Optional.empty();
                }
            }
            return Optional.of(tempToml);
        }
        return delegate.findFile(name);
    }

    private Path generateVirtualToml() throws IOException {
        Optional<Path> fabricJsonPath = delegate.findFile("fabric.mod.json");
        if (fabricJsonPath.isEmpty()) {
            throw new IOException("fabric.mod.json not found in delegate");
        }

        String jsonContent;
        try (InputStream is = Files.newInputStream(fabricJsonPath.get())) {
            jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Minimal JSON parsing without external dependencies
        String id = parseJsonString(jsonContent, "id");
        String version = parseJsonString(jsonContent, "version");
        String name = parseJsonString(jsonContent, "name");
        String description = parseJsonString(jsonContent, "description");
        String license = parseJsonString(jsonContent, "license");

        if (id == null) id = "unknown";
        if (version == null) version = "1.0.0";
        if (name == null) name = id;
        if (description == null) description = "";
        if (license == null) license = "All Rights Reserved";

        // Escape values for TOML
        description = description.replace("\"", "\\\"");

        String toml = "modLoader=\"javafml\"\n" +
                "loaderVersion=\"[4,)\"\n" +
                "license=\"" + license + "\"\n" +
                "\n" +
                "[[mods]]\n" +
                "modId=\"" + id + "\"\n" +
                "version=\"" + version + "\"\n" +
                "displayName=\"" + name + "\"\n" +
                "description=\"" + description + "\"\n";

        Path tempFile = Files.createTempFile("neoforge_mods_", ".toml");
        Files.writeString(tempFile, toml);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    private String parseJsonString(String json, String key) {
        // Extremely simple key search for basic metadata
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        int colonIdx = json.indexOf(":", idx);
        if (colonIdx == -1) return null;
        int quoteStart = json.indexOf("\"", colonIdx);
        if (quoteStart == -1) return null;
        int quoteEnd = json.indexOf("\"", quoteStart + 1);
        if (quoteEnd == -1) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    @Override
    public Path getPrimaryPath() {
        return delegate.getPrimaryPath();
    }

    @Override
    public Manifest getManifest() {
        return delegate.getManifest();
    }

    @Override
    public Set<String> getPackages() {
        return delegate.getPackages();
    }

    @Override
    public Set<String> getPackagesExcluding(String[] excludes) {
        return delegate.getPackagesExcluding(excludes);
    }

    @Override
    public List<String> getMetaInfServices() {
        return delegate.getMetaInfServices();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        if (tempToml != null) {
            try {
                Files.deleteIfExists(tempToml);
            } catch (IOException ignored) {}
        }
    }
}
