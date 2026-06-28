package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ColorProviderRegistryImpl<T, Provider> implements ColorProviderRegistry<T, Provider> {
    private final Map<T, Provider> providers = new HashMap<>();
    private final List<Registration<T, Provider>> pending = new ArrayList<>();

    public static class Registration<T, Provider> {
        public final Provider provider;
        public final T[] objects;

        public Registration(Provider provider, T[] objects) {
            this.provider = provider;
            this.objects = objects;
        }
    }

    @Override
    public void register(Provider provider, T... objects) {
        for (T object : objects) {
            providers.put(object, provider);
        }
        pending.add(new Registration<>(provider, objects));
    }

    @Override
    public Provider get(T object) {
        return providers.get(object);
    }

    public List<Registration<T, Provider>> getPending() {
        return pending;
    }
}
