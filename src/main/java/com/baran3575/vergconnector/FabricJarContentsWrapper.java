package com.baran3575.vergconnector;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        if (name.equals("META-INF/neoforge.mods.toml") || name.equals("neoforge.mods.toml") ||
            name.equals("META-INF/mods.toml") || name.equals("mods.toml")) {
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

        String id = VergConnector.parseTopLevelJsonString(jsonContent, "id");
        String version = VergConnector.parseTopLevelJsonString(jsonContent, "version");
        String name = VergConnector.parseTopLevelJsonString(jsonContent, "name");
        String description = VergConnector.parseTopLevelJsonString(jsonContent, "description");
        String license = VergConnector.parseTopLevelJsonString(jsonContent, "license");

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

        List<String> mixins = VergConnector.parseTopLevelEntrypoints(jsonContent, "mixins");
        for (String mixin : mixins) {
            tomlBuilder.append("[[mixins]]\n")
                       .append("config=\"").append(mixin).append("\"\n\n");
        }

        Path tempFile = Files.createTempFile("neoforge_mods_", ".toml");
        Files.writeString(tempFile, tomlBuilder.toString());
        tempFile.toFile().deleteOnExit();
        return tempFile;
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
