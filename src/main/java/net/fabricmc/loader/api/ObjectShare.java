package net.fabricmc.loader.api;

import java.util.function.BiConsumer;

/**
 * Shim for Fabric Loader's ObjectShare - an inter-mod key/value store.
 * Mods use this to communicate data without hard dependencies.
 */
public interface ObjectShare {
    Object get(String key);
    Object put(String key, Object value);
    Object putIfAbsent(String key, Object value);
    void whenAvailable(String key, BiConsumer<String, Object> consumer);
}
