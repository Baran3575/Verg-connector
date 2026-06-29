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

/**
 * Wraps a Fabric mod's JarContents to provide a synthetic neoforge.mods.toml
 * so NeoForge's mod discovery pipeline accepts the JAR.
 *
 * CRITICAL: This class runs in the BOOTSTRAP classloader phase — Minecraft game
 * classes are NOT yet available. Do NOT reference ANY net.minecraft.* classes
 * here, not even transitively via imports in referenced classes.
 * All JSON parsing is done inline with zero external dependencies.
 */
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

        // Use self-contained parser — NO VergConnector reference (it would pull MC classes)
        String id          = parseJsonString(jsonContent, "id");
        String version     = parseJsonString(jsonContent, "version");
        String name        = parseJsonString(jsonContent, "name");
        String description = parseJsonString(jsonContent, "description");
        String license     = parseJsonString(jsonContent, "license");

        if (id == null)          id = "unknown";
        if (version == null)     version = "1.0.0";
        if (name == null)        name = id;
        if (description == null) description = "";
        if (license == null)     license = "All Rights Reserved";

        // Sanitize for TOML string values
        id          = sanitizeTomlId(id);
        version     = escapeTomlString(version);
        name        = escapeTomlString(name);
        description = escapeTomlString(description);
        license     = escapeTomlString(license);

        StringBuilder toml = new StringBuilder();
        toml.append("modLoader=\"javafml\"\n")
            .append("loaderVersion=\"[4,)\"\n")
            .append("license=\"").append(license).append("\"\n\n")
            .append("[[mods]]\n")
            .append("modId=\"").append(id).append("\"\n")
            .append("version=\"").append(version).append("\"\n")
            .append("displayName=\"").append(name).append("\"\n")
            .append("description=\"").append(description).append("\"\n\n");

        // Declare mixin configs so SpongeMixin picks them up
        List<String> mixins = parseMixinList(jsonContent);
        for (String mixin : mixins) {
            toml.append("[[mixins]]\n")
                .append("config=\"").append(escapeTomlString(mixin)).append("\"\n\n");
        }

        Path tempFile = Files.createTempFile("vergconn_mods_", ".toml");
        Files.writeString(tempFile, toml.toString());
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    // ─── Self-contained JSON helpers ──────────────────────────────────────────
    // These MUST remain standalone — no VergConnector references allowed here.

    /** Parse a simple top-level string value from JSON. Returns null if not found or not a string. */
    static String parseJsonString(String json, String key) {
        if (json == null) return null;
        int depth = 0;
        boolean inQuote = false;
        StringBuilder keyBuf = new StringBuilder();
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && inQuote) { i += 2; continue; }
            if (c == '"') {
                inQuote = !inQuote;
                if (inQuote) {
                    keyBuf.setLength(0);
                } else {
                    if (depth == 1 && keyBuf.toString().equals(key)) {
                        // Skip whitespace and colon
                        i++;
                        while (i < json.length() && json.charAt(i) != ':') i++;
                        i++;
                        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
                        if (i >= json.length()) return null;
                        char next = json.charAt(i);
                        if (next == '[' || next == '{') return null; // not a string
                        if (next == '"') {
                            i++;
                            StringBuilder val = new StringBuilder();
                            while (i < json.length()) {
                                char v = json.charAt(i);
                                if (v == '\\' && i + 1 < json.length()) {
                                    char esc = json.charAt(i + 1);
                                    val.append(esc == 'n' ? '\n' : esc == 't' ? '\t' : esc);
                                    i += 2;
                                } else if (v == '"') {
                                    break;
                                } else {
                                    val.append(v);
                                    i++;
                                }
                            }
                            return val.toString();
                        }
                        return null;
                    }
                }
            } else if (inQuote) {
                keyBuf.append(c);
            } else {
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }
            i++;
        }
        return null;
    }

    /**
     * Parse the "mixins" array from fabric.mod.json.
     * Each element may be a plain string ("mymod.mixins.json") or an object
     * ({"config":"mymod.mixins.json","environment":"client"}).
     */
    static List<String> parseMixinList(String json) {
        List<String> list = new ArrayList<>();
        if (json == null) return list;

        // Find top-level "mixins" key
        int keyIdx = 0;
        outer:
        while (true) {
            int q = json.indexOf("\"mixins\"", keyIdx);
            if (q == -1) break;
            // Make sure it's at depth 1 (top-level object only)
            int braceDepth = 0;
            boolean inStr = false;
            for (int k = 0; k < q; k++) {
                char c = json.charAt(k);
                if (c == '\\' && inStr) { k++; continue; }
                if (c == '"') inStr = !inStr;
                else if (!inStr) {
                    if (c == '{') braceDepth++;
                    else if (c == '}') braceDepth--;
                }
            }
            if (braceDepth != 1) { keyIdx = q + 1; continue; }

            // Skip to ':'
            int colon = json.indexOf(':', q + 8);
            if (colon == -1) break;
            int pos = colon + 1;
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
            if (pos >= json.length()) break;

            if (json.charAt(pos) == '[') {
                int arrEnd = findMatching(json, pos, '[', ']');
                if (arrEnd == -1) break;
                parseStringOrObjectArray(json.substring(pos + 1, arrEnd), list);
            }
            break;
        }
        return list;
    }

    private static void parseStringOrObjectArray(String arr, List<String> out) {
        int i = 0;
        while (i < arr.length()) {
            while (i < arr.length() && (Character.isWhitespace(arr.charAt(i)) || arr.charAt(i) == ',')) i++;
            if (i >= arr.length()) break;
            char c = arr.charAt(i);
            if (c == '"') {
                int end = arr.indexOf('"', i + 1);
                if (end == -1) break;
                out.add(arr.substring(i + 1, end));
                i = end + 1;
            } else if (c == '{') {
                int close = findMatching(arr, i, '{', '}');
                if (close == -1) break;
                String obj = arr.substring(i + 1, close);
                // Extract "config" field
                String cfg = parseJsonString("{" + obj + "}", "config");
                if (cfg != null) out.add(cfg);
                i = close + 1;
            } else {
                i++;
            }
        }
    }

    private static int findMatching(String s, int start, char open, char close) {
        int depth = 0;
        boolean inQ = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && inQ) { i++; continue; }
            if (c == '"') { inQ = !inQ; continue; }
            if (inQ) continue;
            if (c == open) depth++;
            else if (c == close) { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    // ─── TOML sanitization helpers ───────────────────────────────────────────

    /** Ensure modId is a valid NeoForge mod ID: lowercase alphanumeric + underscore. */
    private static String sanitizeTomlId(String id) {
        if (id == null || id.isEmpty()) return "unknown";
        StringBuilder sb = new StringBuilder();
        for (char c : id.toLowerCase().toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else if (c == '-') {
                sb.append('_');
            }
        }
        if (sb.length() == 0) return "unknown";
        // Must start with a letter
        if (!Character.isLetter(sb.charAt(0))) sb.insert(0, "mod_");
        return sb.toString();
    }

    /** Escape a string value for embedding in a TOML double-quoted string. */
    private static String escapeTomlString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // ─── JarContents delegation ───────────────────────────────────────────────

    @Override
    public Path getPrimaryPath() { return delegate.getPrimaryPath(); }

    @Override
    public Manifest getManifest() { return delegate.getManifest(); }

    @Override
    public Set<String> getPackages() { return delegate.getPackages(); }

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
            try { Files.deleteIfExists(tempToml); } catch (IOException ignored) {}
        }
    }
}
