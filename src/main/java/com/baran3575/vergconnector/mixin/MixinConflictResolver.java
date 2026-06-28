package com.baran3575.vergconnector.mixin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks mixin targets across all loaded Fabric mods to detect conflicts.
 * When two mods apply mixins to the same class, we log a warning so the
 * developer knows - they're applied sequentially, not crashing.
 */
public class MixinConflictResolver {
    public static final MixinConflictResolver INSTANCE = new MixinConflictResolver();

    /** target class name → list of mixin entries claiming it */
    private final Map<String, List<MixinEntry>> targetMap = new ConcurrentHashMap<>();

    public static class MixinEntry {
        public final String mixinClass;
        public final String modId;

        public MixinEntry(String mixinClass, String modId) {
            this.mixinClass = mixinClass;
            this.modId = modId;
        }

        @Override
        public String toString() {
            return modId + ":" + mixinClass;
        }
    }

    private MixinConflictResolver() {}

    /**
     * Register a mixin class and the target it modifies.
     * Called from MixinConfigHandler during mod loading.
     */
    public void registerMixin(String targetClass, String mixinClass, String modId) {
        targetMap.computeIfAbsent(targetClass, k -> Collections.synchronizedList(new ArrayList<>()))
                 .add(new MixinEntry(mixinClass, modId));
    }

    /**
     * Returns a list of target class names where more than one mixin applies.
     */
    public List<String> getConflictingTargets() {
        List<String> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<MixinEntry>> entry : targetMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(conflicts);
    }

    /** Returns all registered mixin targets and their entries, read-only. */
    public Map<String, List<MixinEntry>> getAllTargets() {
        return Collections.unmodifiableMap(targetMap);
    }

    /** Returns all entries registered for a specific target. */
    public List<MixinEntry> getEntriesFor(String targetClass) {
        return Collections.unmodifiableList(targetMap.getOrDefault(targetClass, List.of()));
    }
}
