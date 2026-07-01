package com.baran3575.vergconnector.helper;

import com.baran3575.vergconnector.VergConnector;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MixinConfigHandler {

    public static void processMixinConfigs(String modId, Path modFilePath, List<String> mixinConfigs) {
        for (String configName : mixinConfigs) {
            try {
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
                    processMixinJson(modId, configName, json, modFilePath);
                }
            } catch (Exception e) {
                VergConnector.LOGGER.debug(
                    "[Verg Connector] Could not read mixin config '{}' for mod '{}': {}",
                    configName, modId, e.getMessage());
            }
        }
    }

    private static void processMixinJson(String modId, String configName, String json, Path modFilePath) {
        String pkg = parseJsonString(json, "package");
        if (pkg == null) pkg = "";

        List<String> allMixins = new ArrayList<>();
        allMixins.addAll(parseMixinArray(json, "mixins"));
        allMixins.addAll(parseMixinArray(json, "client"));
        allMixins.addAll(parseMixinArray(json, "server"));

        for (String mixinName : allMixins) {
            String fullName = pkg.isEmpty() ? mixinName : pkg + "." + mixinName;

            List<String> targets = readMixinTargets(modFilePath, fullName);
            if (targets.isEmpty()) {
                MixinConflictResolver.INSTANCE.registerMixin(fullName, fullName, modId);
            } else {
                for (String target : targets) {
                    MixinConflictResolver.INSTANCE.registerMixin(target, fullName, modId);
                }
            }
        }

        VergConnector.LOGGER.debug(
            "[Verg Connector] Processed mixin config '{}' for mod '{}': {} mixins registered",
            configName, modId, allMixins.size());
    }

    private static List<String> readMixinTargets(Path modFilePath, String mixinFullName) {
        List<String> targets = new ArrayList<>();
        try {
            String classPath = mixinFullName.replace('.', '/') + ".class";
            byte[] bytes = null;

            if (Files.isDirectory(modFilePath)) {
                Path file = modFilePath.resolve(classPath);
                if (Files.exists(file)) {
                    bytes = Files.readAllBytes(file);
                }
            } else {
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(modFilePath.toFile())) {
                    java.util.zip.ZipEntry entry = zip.getEntry(classPath);
                    if (entry != null) {
                        try (InputStream is = zip.getInputStream(entry)) {
                            bytes = is.readAllBytes();
                        }
                    }
                }
            }

            if (bytes != null) {
                org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(bytes);
                org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
                reader.accept(node, org.objectweb.asm.ClassReader.SKIP_CODE | org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);

                if (node.visibleAnnotations != null) {
                    for (org.objectweb.asm.tree.AnnotationNode ann : node.visibleAnnotations) {
                        if (ann.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                            List<Object> values = ann.values;
                            if (values != null) {
                                for (int i = 0; i < values.size(); i += 2) {
                                    String name = (String) values.get(i);
                                    Object val = values.get(i + 1);
                                    if (name.equals("value")) {
                                        if (val instanceof List) {
                                            for (Object o : (List<?>) val) {
                                                if (o instanceof org.objectweb.asm.Type) {
                                                    targets.add(((org.objectweb.asm.Type) o).getClassName());
                                                }
                                            }
                                        }
                                    } else if (name.equals("targets")) {
                                        if (val instanceof List) {
                                            for (Object o : (List<?>) val) {
                                                if (o instanceof String) {
                                                    targets.add((String) o);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        return targets;
    }

    private static List<String> parseMixinArray(String json, String key) {
        List<String> list = new ArrayList<>();
        int keyIdx = 0;
        while (true) {
            keyIdx = json.indexOf('"' + key + '"', keyIdx);
            if (keyIdx == -1) break;
            int colon = json.indexOf(':', keyIdx + key.length() + 2);
            if (colon == -1) break;
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
                int close = findMatchingBracket(arr, i, '{', '}');
                if (close == -1) break;
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
