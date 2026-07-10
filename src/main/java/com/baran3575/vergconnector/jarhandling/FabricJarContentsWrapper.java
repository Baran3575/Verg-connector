package com.baran3575.vergconnector.jarhandling;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContentsBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Parses a Fabric mod's fabric.mod.json and returns a JarContents instance
 * which overlays a generated neoforge.mods.toml file onto the original JAR.
 *
 * CRITICAL: This class runs in the BOOTSTRAP classloader phase — Minecraft game
 * classes are NOT yet available. Do NOT reference ANY net.minecraft.* classes here.
 */
public class FabricJarContentsWrapper {

    public static JarContents createJarContents(Path remappedJar) throws IOException {
        String jsonContent = null;
        try (ZipFile zip = new ZipFile(remappedJar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                return null;
            }
            try (InputStream is = zip.getInputStream(entry)) {
                jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        String tomlString = generateVirtualToml(jsonContent);

        // Build a SINGLE real jar that contains the remapped mod's classes AND a generated
        // META-INF/neoforge.mods.toml. SecureJar.from() (called by FML's reader) requires the
        // JarContents to be produced by a standard JarFileContents, so we must ship one physical
        // file rather than overlaying a separate meta-jar. We copy the remapped jar verbatim and
        // inject the toml as the only extra entry.
        Path combined = Files.createTempFile("vergconn_combined_", ".jar");
        combined.toFile().deleteOnExit();
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(remappedJar));
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(combined))) {
            ZipEntry in;
            while ((in = zin.getNextEntry()) != null) {
                String name = in.getName();
                if (name.equals("META-INF/neoforge.mods.toml")) {
                    continue;
                }
                ZipEntry out = new ZipEntry(name);
                if (in.getMethod() == ZipEntry.STORED) {
                    out.setMethod(ZipEntry.STORED);
                    out.setSize(in.getSize());
                    out.setCompressedSize(in.getCompressedSize());
                    out.setCrc(in.getCrc());
                }
                zos.putNextEntry(out);
                zin.transferTo(zos);
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));
            zos.write(tomlString.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        // NeoForge 21.1.234: a real JarContents is produced by JarContentsBuilder.
        // The combined jar carries the remapped Fabric classes plus the generated
        // META-INF/neoforge.mods.toml as its only extra entry, so FML's reader accepts
        // the mod (the earlier overlay approach failed with a cast/ClassCastException).
        return new JarContentsBuilder().paths(combined).build();
    }

    private static String generateVirtualToml(String jsonContent) throws IOException {
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

        // Parse contact info
        String displayURL = null;
        String issueTrackerURL = null;
        String contactObj = parseJsonObject(jsonContent, "contact");
        if (contactObj != null) {
            displayURL = parseJsonString(contactObj, "homepage");
            if (displayURL == null) {
                displayURL = parseJsonString(contactObj, "sources");
            }
            issueTrackerURL = parseJsonString(contactObj, "issues");
        }

        // Parse authors list
        String authors = null;
        String authorsArr = parseJsonArray(jsonContent, "authors");
        if (authorsArr != null) {
            List<String> authList = parseListElements(authorsArr);
            if (!authList.isEmpty()) {
                authors = String.join(", ", authList);
            }
        }

        // Parse icon
        String logoFile = parseIcon(jsonContent);

        StringBuilder toml = new StringBuilder();
        toml.append("modLoader=\"javafml\"\n")
            .append("loaderVersion=\"[4,)\"\n")
            .append("license=\"").append(license).append("\"\n\n")
            .append("[[mods]]\n")
            .append("modId=\"").append(id).append("\"\n")
            .append("version=\"").append(version).append("\"\n")
            .append("displayName=\"").append(name).append("\"\n")
            .append("description=\"").append(description).append("\"\n");

        if (displayURL != null && !displayURL.isEmpty()) {
            toml.append("displayURL=\"").append(escapeTomlString(displayURL)).append("\"\n");
        }
        if (issueTrackerURL != null && !issueTrackerURL.isEmpty()) {
            toml.append("issueTrackerURL=\"").append(escapeTomlString(issueTrackerURL)).append("\"\n");
        }
        if (authors != null && !authors.isEmpty()) {
            toml.append("authors=\"").append(escapeTomlString(authors)).append("\"\n");
        }
        if (logoFile != null && !logoFile.isEmpty()) {
            toml.append("logoFile=\"").append(escapeTomlString(logoFile)).append("\"\n");
        }
        toml.append("\n");

        // Declare mixin configs so SpongeMixin picks them up
        List<String> mixins = parseMixinList(jsonContent);
        for (String mixin : mixins) {
            toml.append("[[mixins]]\n")
                .append("config=\"").append(escapeTomlString(mixin)).append("\"\n\n");
        }

        // Translate dependencies (depends block)
        String dependsObj = parseJsonObject(jsonContent, "depends");
        boolean hasMinecraftDep = false;
        boolean hasNeoForgeDep = false;

        if (dependsObj != null) {
            List<KeyValuePair> depends = parseMapPairs(dependsObj);
            for (KeyValuePair dep : depends) {
                String depId = dep.key.trim();
                String depRange = dep.value;

                if (depId.equals("fabricloader")) {
                    continue;
                }

                if (depId.equals("minecraft")) {
                    hasMinecraftDep = true;
                }
                if (depId.equals("neoforge")) {
                    hasNeoForgeDep = true;
                }

                if (depId.equals("fabric") || depId.equals("fabric-api")) {
                    continue;
                }

                String targetId = sanitizeTomlId(depId);
                String convertedRange = convertSemVerToMaven(depRange);

                toml.append("[[dependencies.\"").append(id).append("\"]]\n")
                    .append("    modId=\"").append(targetId).append("\"\n")
                    .append("    type=\"required\"\n")
                    .append("    versionRange=\"").append(convertedRange).append("\"\n")
                    .append("    ordering=\"NONE\"\n")
                    .append("    side=\"BOTH\"\n\n");
            }
        }

        // Ensure minecraft and neoforge dependencies are declared
        if (!hasMinecraftDep) {
            toml.append("[[dependencies.\"").append(id).append("\"]]\n")
                .append("    modId=\"minecraft\"\n")
                .append("    type=\"required\"\n")
                .append("    versionRange=\"[1.21.1,1.22)\"\n")
                .append("    ordering=\"NONE\"\n")
                .append("    side=\"BOTH\"\n\n");
        }
        if (!hasNeoForgeDep) {
            toml.append("[[dependencies.\"").append(id).append("\"]]\n")
                .append("    modId=\"neoforge\"\n")
                .append("    type=\"required\"\n")
                .append("    versionRange=\"[21.1.0,)\"\n")
                .append("    ordering=\"NONE\"\n")
                .append("    side=\"BOTH\"\n\n");
        }

        // Translate conflicts (breaks block)
        String breaksObj = parseJsonObject(jsonContent, "breaks");
        if (breaksObj != null) {
            List<KeyValuePair> breaks = parseMapPairs(breaksObj);
            for (KeyValuePair brk : breaks) {
                String brkId = sanitizeTomlId(brk.key.trim());
                String convertedRange = convertSemVerToMaven(brk.value);

                toml.append("[[dependencies.\"").append(id).append("\"]]\n")
                    .append("    modId=\"").append(brkId).append("\"\n")
                    .append("    type=\"incompatible\"\n")
                    .append("    versionRange=\"").append(convertedRange).append("\"\n")
                    .append("    ordering=\"NONE\"\n")
                    .append("    side=\"BOTH\"\n\n");
            }
        }

        return toml.toString();
    }

    // ─── Self-contained JSON & SemVer helpers ─────────────────────────────────

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
                        i++;
                        while (i < json.length() && json.charAt(i) != ':') i++;
                        i++;
                        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
                        if (i >= json.length()) return null;
                        char next = json.charAt(i);
                        if (next == '[' || next == '{') return null;
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

    static String parseJsonObject(String json, String key) {
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
                        i++;
                        while (i < json.length() && json.charAt(i) != ':') i++;
                        i++;
                        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
                        if (i >= json.length()) return null;
                        if (json.charAt(i) == '{') {
                            int end = findMatching(json, i, '{', '}');
                            if (end != -1) {
                                return json.substring(i, end + 1);
                            }
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

    static String parseJsonArray(String json, String key) {
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
                        i++;
                        while (i < json.length() && json.charAt(i) != ':') i++;
                        i++;
                        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
                        if (i >= json.length()) return null;
                        if (json.charAt(i) == '[') {
                            int end = findMatching(json, i, '[', ']');
                            if (end != -1) {
                                return json.substring(i, end + 1);
                            }
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

    static class KeyValuePair {
        public final String key;
        public final String value;
        public KeyValuePair(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    static List<KeyValuePair> parseMapPairs(String obj) {
        List<KeyValuePair> list = new ArrayList<>();
        if (obj == null || !obj.startsWith("{")) return list;
        String inner = obj.substring(1, obj.length() - 1).trim();
        int i = 0;
        while (i < inner.length()) {
            while (i < inner.length() && inner.charAt(i) != '"') i++;
            if (i >= inner.length()) break;
            i++;
            StringBuilder key = new StringBuilder();
            while (i < inner.length() && inner.charAt(i) != '"') {
                key.append(inner.charAt(i));
                i++;
            }
            i++;
            while (i < inner.length() && inner.charAt(i) != ':') i++;
            i++;
            while (i < inner.length() && Character.isWhitespace(inner.charAt(i))) i++;
            if (i >= inner.length()) break;

            if (inner.charAt(i) == '"') {
                i++;
                StringBuilder val = new StringBuilder();
                while (i < inner.length()) {
                    char c = inner.charAt(i);
                    if (c == '\\' && i + 1 < inner.length()) {
                        val.append(inner.charAt(i + 1));
                        i += 2;
                    } else if (c == '"') {
                        break;
                    } else {
                        val.append(c);
                        i++;
                    }
                }
                i++;
                list.add(new KeyValuePair(key.toString(), val.toString()));
            } else if (inner.charAt(i) == '[') {
                int end = findMatching(inner, i, '[', ']');
                if (end != -1) {
                    list.add(new KeyValuePair(key.toString(), inner.substring(i, end + 1)));
                    i = end + 1;
                }
            } else if (inner.charAt(i) == '{') {
                int end = findMatching(inner, i, '{', '}');
                if (end != -1) {
                    list.add(new KeyValuePair(key.toString(), inner.substring(i, end + 1)));
                    i = end + 1;
                }
            } else {
                i++;
            }
            while (i < inner.length() && inner.charAt(i) != ',') i++;
            i++;
        }
        return list;
    }

    static List<String> parseListElements(String arr) {
        List<String> list = new ArrayList<>();
        if (arr == null || !arr.startsWith("[")) return list;
        String inner = arr.substring(1, arr.length() - 1).trim();
        int i = 0;
        while (i < inner.length()) {
            while (i < inner.length() && Character.isWhitespace(inner.charAt(i))) i++;
            if (i >= inner.length()) break;
            char c = inner.charAt(i);
            if (c == '"') {
                i++;
                StringBuilder val = new StringBuilder();
                while (i < inner.length()) {
                    char ch = inner.charAt(i);
                    if (ch == '\\' && i + 1 < inner.length()) {
                        val.append(inner.charAt(i + 1));
                        i += 2;
                    } else if (ch == '"') {
                        break;
                    } else {
                        val.append(ch);
                        i++;
                    }
                }
                i++;
                list.add(val.toString());
            } else if (c == '{') {
                int end = findMatching(inner, i, '{', '}');
                if (end != -1) {
                    String obj = inner.substring(i, end + 1);
                    String name = parseJsonString(obj, "name");
                    if (name != null) list.add(name);
                    i = end + 1;
                }
            } else {
                i++;
            }
            while (i < inner.length() && inner.charAt(i) != ',') i++;
            i++;
        }
        return list;
    }

    static String parseIcon(String json) {
        String icon = parseJsonString(json, "icon");
        if (icon != null) return icon;
        String iconObj = parseJsonObject(json, "icon");
        if (iconObj != null) {
            List<KeyValuePair> pairs = parseMapPairs(iconObj);
            if (!pairs.isEmpty()) {
                return pairs.get(0).value;
            }
        }
        return null;
    }

    static List<String> parseMixinList(String json) {
        List<String> list = new ArrayList<>();
        if (json == null) return list;

        int keyIdx = 0;
        outer:
        while (true) {
            int q = json.indexOf("\"mixins\"", keyIdx);
            if (q == -1) break;
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
        if (!Character.isLetter(sb.charAt(0))) sb.insert(0, "mod_");
        return sb.toString();
    }

    private static String escapeTomlString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static String convertSemVerToMaven(String semver) {
        if (semver == null || semver.equals("*") || semver.isEmpty()) {
            return "";
        }
        semver = semver.trim();
        if (semver.startsWith(">=")) {
            return "[" + semver.substring(2).trim() + ",)";
        } else if (semver.startsWith(">")) {
            return "(" + semver.substring(1).trim() + ",)";
        } else if (semver.startsWith("<=")) {
            return "(," + semver.substring(2).trim() + "]";
        } else if (semver.startsWith("<")) {
            return "(," + semver.substring(1).trim() + ")";
        } else if (semver.startsWith("=")) {
            return "[" + semver.substring(1).trim() + "]";
        } else if (semver.startsWith("^")) {
            String v = semver.substring(1).trim();
            String[] parts = v.split("\\.");
            try {
                int major = Integer.parseInt(parts[0]);
                return "[" + v + "," + (major + 1) + ".0.0)";
            } catch (Exception e) {
                return "[" + v + ",)";
            }
        } else if (semver.startsWith("~")) {
            String v = semver.substring(1).trim();
            String[] parts = v.split("\\.");
            try {
                int major = Integer.parseInt(parts[0]);
                int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                return "[" + v + "," + major + "." + (minor + 1) + ".0)";
            } catch (Exception e) {
                return "[" + v + ",)";
            }
        }
        return "[" + semver + ",)";
    }


}
