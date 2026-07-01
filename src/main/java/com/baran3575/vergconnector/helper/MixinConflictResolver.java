package com.baran3575.vergconnector.helper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MixinConflictResolver {
    public static final MixinConflictResolver INSTANCE = new MixinConflictResolver();

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

    public void registerMixin(String targetClass, String mixinClass, String modId) {
        targetMap.computeIfAbsent(targetClass, k -> Collections.synchronizedList(new ArrayList<>()))
                 .add(new MixinEntry(mixinClass, modId));
    }

    public List<String> getConflictingTargets() {
        List<String> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<MixinEntry>> entry : targetMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(conflicts);
    }

    public Map<String, List<MixinEntry>> getAllTargets() {
        return Collections.unmodifiableMap(targetMap);
    }

    public List<MixinEntry> getEntriesFor(String targetClass) {
        return Collections.unmodifiableList(targetMap.getOrDefault(targetClass, List.of()));
    }
}
