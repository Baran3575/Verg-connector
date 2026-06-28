package com.baran3575.vergconnector;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.SecureJar;

public class FabricJarContentsWrapper implements JarContents {
    private final JarContents delegate;
    private Path tempToml;

    public FabricJarContentsWrapper(JarContents delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<URI> findFile(String name) {
        if (name.equals("META-INF/neoforge.mods.toml") || name.equals("neoforge.mods.toml")) {
            if (tempToml == null) {
                try {
                    tempToml = generateVirtualToml();
                } catch (IOException e) {
                    System.err.println("[Verg Connector] Failed to generate virtual toml: " + e.getMessage());
                    return Optional.empty();
                }
            }
            return Optional.of(tempToml.toUri());
        }
        return delegate.findFile(name);
    }

    private Path generateVirtualToml() throws IOException {
        Optional<URI> fabricJsonUri = delegate.findFile("fabric.mod.json");
        if (fabricJsonUri.isEmpty()) {
            throw new IOException("fabric.mod.json not found in delegate");
        }

        String jsonContent;
        try (InputStream is = fabricJsonUri.get().toURL().openStream()) {
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

        StringBuilder tomlBuilder = new StringBuilder();
        tomlBuilder.append("modLoader=\"javafml\"\n")
                   .append("loaderVersion=\"[4,)\"\n")
                   .append("license=\"").append(license).append("\"\n\n")
                   .append("[[mods]]\n")
                   .append("modId=\"").append(id).append("\"\n")
                   .append("version=\"").append(version).append("\"\n")
                   .append("displayName=\"").append(name).append("\"\n")
                   .append("description=\"").append(description).append("\"\n\n");

        List<String> mixins = parseMixins(jsonContent);
        for (String mixin : mixins) {
            tomlBuilder.append("[[mixins]]\n")
                       .append("config=\"").append(mixin).append("\"\n\n");
        }

        Path tempFile = Files.createTempFile("neoforge_mods_", ".toml");
        Files.writeString(tempFile, tomlBuilder.toString());
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    private String parseJsonString(String json, String key) {
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

    private List<String> parseMixins(String json) {
        List<String> list = new ArrayList<>();
        int idx = json.indexOf("\"mixins\"");
        if (idx == -1) return list;
        int colonIdx = json.indexOf(":", idx);
        if (colonIdx == -1) return list;
        int arrayStart = json.indexOf("[", colonIdx);
        int arrayEnd = json.indexOf("]", colonIdx);
        if (arrayStart == -1 || arrayEnd == -1 || arrayStart > arrayEnd) {
            return list;
        }

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        String[] elements = arrayContent.split(",");
        for (String element : elements) {
            element = element.trim();
            if (element.startsWith("{")) {
                int configIdx = element.indexOf("\"config\"");
                if (configIdx != -1) {
                    int valColon = element.indexOf(":", configIdx);
                    if (valColon != -1) {
                        int qStart = element.indexOf("\"", valColon);
                        if (qStart != -1) {
                            int qEnd = element.indexOf("\"", qStart + 1);
                            if (qEnd != -1) {
                                list.add(element.substring(qStart + 1, qEnd));
                            }
                        }
                    }
                }
            } else if (element.startsWith("\"") && element.endsWith("\"")) {
                list.add(element.substring(1, element.length() - 1));
            }
        }
        return list;
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
    public List<SecureJar.Provider> getMetaInfServices() {
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
