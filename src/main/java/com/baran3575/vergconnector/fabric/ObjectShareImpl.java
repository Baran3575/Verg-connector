package com.baran3575.vergconnector.fabric;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.ObjectShare;

/**
 * Thread-safe ObjectShare implementation that also fires callbacks
 * registered before the value was put in (whenAvailable support).
 */
public class ObjectShareImpl implements ObjectShare {
    public static final ObjectShareImpl INSTANCE = new ObjectShareImpl();

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    // Pending consumers waiting for a key to become available
    private final Map<String, List<BiConsumer<String, Object>>> pendingConsumers = new ConcurrentHashMap<>();

    private ObjectShareImpl() {}

    @Override
    public Object get(String key) {
        return values.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        Object prev = values.put(key, value);
        // Fire any registered whenAvailable callbacks
        List<BiConsumer<String, Object>> consumers = pendingConsumers.remove(key);
        if (consumers != null) {
            for (BiConsumer<String, Object> consumer : consumers) {
                try {
                    consumer.accept(key, value);
                } catch (Exception ignored) {}
            }
        }
        return prev;
    }

    @Override
    public Object putIfAbsent(String key, Object value) {
        Object result = values.putIfAbsent(key, value);
        if (result == null) {
            // A new value was inserted; fire pending callbacks
            List<BiConsumer<String, Object>> consumers = pendingConsumers.remove(key);
            if (consumers != null) {
                for (BiConsumer<String, Object> consumer : consumers) {
                    try {
                        consumer.accept(key, value);
                    } catch (Exception ignored) {}
                }
            }
        }
        return result;
    }

    @Override
    public void whenAvailable(String key, BiConsumer<String, Object> consumer) {
        Object existing = values.get(key);
        if (existing != null) {
            // Already available - call immediately
            try {
                consumer.accept(key, existing);
            } catch (Exception ignored) {}
        } else {
            // Queue for when it becomes available
            pendingConsumers.computeIfAbsent(key, k -> new ArrayList<>()).add(consumer);
        }
    }
}
