package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class EntityRendererRegistry {
    private static final List<Registration<?>> pending = new ArrayList<>();

    public static class Registration<T extends Entity> {
        public final EntityType<T> type;
        public final EntityRendererProvider<T> provider;

        public Registration(EntityType<T> type, EntityRendererProvider<T> provider) {
            this.type = type;
            this.provider = provider;
        }
    }

    public static <T extends Entity> void register(EntityType<T> type, EntityRendererProvider<T> provider) {
        pending.add(new Registration<>(type, provider));
    }

    public static List<Registration<?>> getPending() {
        return pending;
    }
}
