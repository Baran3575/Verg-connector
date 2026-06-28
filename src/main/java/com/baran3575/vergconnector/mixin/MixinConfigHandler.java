package com.baran3575.vergconnector.mixin;

import com.baran3575.vergconnector.VergConnector;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * Reads mixin JSON configs from Fabric mod JARs and:
 * 1. Registers all mixin classes with MixinConflictResolver to surface conflicts early.
 * 2. Provides a safe list of active mixin configs so they can be de-duplicated on refmap name.
 *
 * Mixin configs are referenced in fabric.mod.json via:
 *   "mixins": ["mymod.mixins.json", {"config":"mymod.client.mixins.json", "environment":"client"}]
 */
public class MixinConfigHandler {

    /**
     * Process mixin config names referenced by a Fabric mod's fabric.mod.json.
     *
     * @param modId       the fabric mod id
     * @param modFilePath path to the JAR or directory root of the mod
     * @param mixinConfigs list of mixin config file names (e.g., "mymod.mixins.json")
     */
    public static void processMixinConfigs(String modId, Path modFilePath, List<String> mixinConfigs) {
        for (String configName : mixinConfigs) {
            try {
                // Try to open the config from the mod JAR's root
                URI jarUri = modFilePath.toUri();
                String scheme = jarUri.getScheme();
                URI configUri;
                if (scheme != null && scheme.equals("jar")) {
                    configUri = URI.create(jarUri.toString() + "!/" + configName);
                } else {
                    configUri = modFilePath.resolve(configName).toUri();
                }

                try (InputStream is = configUri.toURL().openStream()) {
                    String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    processMixinJson(modId, configName, json);
                }
            } catch (Exception e) {
                VergConnector.LOGGER.debug(
                    "[Verg Connector] Could not read mixin config '{}' for mod '{}': {}",
                    configName, modId, e.getMessage());
            }
        }
    }

    private static void processMixinJson(String modId, String configName, String json) {
        String pkg = parseJsonString(json, "package");
        if (pkg == null) pkg = "";

        List<String> allMixins = new ArrayList<>();
        allMixins.addAll(parseMixinArray(json, "mixins"));
        allMixins.addAll(parseMixinArray(json, "client"));
        allMixins.addAll(parseMixinArray(json, "server"));

        for (String mixinName : allMixins) {
            // Mixin names in the JSON are relative to "package", e.g. "MixinBlock" → "com.example.mixin.MixinBlock"
            String fullName = pkg.isEmpty() ? mixinName : pkg + "." + mixinName;
            // Target is the same as mixin class for tracking purposes (we don't resolve @Mixin targets at this stage)
            MixinConflictResolver.INSTANCE.registerMixin(fullName, fullName, modId);
        }

        // Check and log conflicts detected so far
        List<String> conflicts = MixinConflictResolver.INSTANCE.getConflictingTargets();
        if (!conflicts.isEmpty()) {
            for (String conflict : conflicts) {
                List<MixinConflictResolver.MixinEntry> entries =
                    MixinConflictResolver.INSTANCE.getEntriesFor(conflict);
                VergConnector.LOGGER.warn(
                    "[Verg Connector] ⚠ Mixin conflict: '{}' is targeted by multiple mods: {}",
                    conflict,
                    entries.stream()
                        .map(MixinConflictResolver.MixinEntry::toString)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("?"));
            }
        }

        VergConnector.LOGGER.debug(
            "[Verg Connector] Processed mixin config '{}' for mod '{}': {} mixins registered",
            configName, modId, allMixins.size());
    }

    // ─── JSON helpers ──────────────────────────────────────────────────────────

    private static List<String> parseMixinArray(String json, String key) {
        List<String> list = new ArrayList<>();
        // Locate "key" at the object level
        int keyIdx = 0;
        while (true) {
            keyIdx = json.indexOf('"' + key + '"', keyIdx);
            if (keyIdx == -1) break;
            int colon = json.indexOf(':', keyIdx + key.length() + 2);
            if (colon == -1) break;
            // Skip whitespace to find '[' or '"'
            int pos = colon + 1;
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
            if (pos >= json.length()) break;

            if (json.charAt(pos) == '[') {
                int arrEnd = findMatchingBracket(json, pos, '[', ']');
                if (arrEnd == -1) break;
                String arr = json.substring(pos + 1, arrEnd);
                parseStringArray(arr, list);
                break;
            }
            keyIdx++;
        }
        return list;
    }

    private static void parseStringArray(String arr, List<String> list) {
        int i = 0;
        while (i < arr.length()) {
            while (i < arr.length() && (Character.isWhitespace(arr.charAt(i)) || arr.charAt(i) == ',')) i++;
            if (i >= arr.length()) break;
            if (arr.charAt(i) == '"') {
                int end = arr.indexOf('"', i + 1);
                if (end == -1) break;
                list.add(arr.substring(i + 1, end));
                i = end + 1;
            } else if (arr.charAt(i) == '{') {
                // Object entry: {"config":"file.json","environment":"client"}
                // We don't load the inner file here, but track the config name
                int close = findMatchingBracket(arr, i, '{', '}');
                if (close == -1) break;
                // Try to extract the "config" field value as the mixin name
                String obj = arr.substring(i + 1, close);
                String configVal = parseJsonString(obj, "config");
                if (configVal != null) list.add(configVal);
                i = close + 1;
            } else {
                i++;
            }
        }
    }

    private static String parseJsonString(String json, String key) {
        int idx = json.indexOf('"' + key + '"');
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx + key.length() + 2);
        if (colon == -1) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 == -1) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 == -1) return null;
        return json.substring(q1 + 1, q2);
    }

    /** Find the index of the closing bracket/brace that matches the one at {@code start}. */
    private static int findMatchingBracket(String s, int start, char open, char close) {
        int depth = 0;
        boolean inQuote = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && inQuote) { i++; continue; }
            if (c == '"') { inQuote = !inQuote; continue; }
            if (inQuote) continue;
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
