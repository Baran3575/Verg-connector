package net.fabricmc.fabric.api.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.packs.PackType;

public final class ResourceManagerHelper {
    private static final Map<PackType, ResourceManagerHelper> INSTANCES = new HashMap<>();
    private final List<IdentifiableResourceReloadListener> listeners = new ArrayList<>();

    public static ResourceManagerHelper get(PackType type) {
        return INSTANCES.computeIfAbsent(type, k -> new ResourceManagerHelper());
    }

    public void registerReloadListener(IdentifiableResourceReloadListener listener) {
        listeners.add(listener);
    }

    public List<IdentifiableResourceReloadListener> getListeners() {
        return listeners;
    }
}
