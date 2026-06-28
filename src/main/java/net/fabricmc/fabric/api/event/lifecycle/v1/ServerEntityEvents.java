package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

/** Fabric shim: entity load/unload events bridged from NeoForge EntityJoinLevelEvent. */
public final class ServerEntityEvents {
    public interface EntityLoad {
        void onLoad(Entity entity, ServerLevel world);
    }
    public interface EntityUnload {
        void onUnload(Entity entity, ServerLevel world);
    }

    public static final EventImpl<EntityLoad> ENTITY_LOAD = new EventImpl<>();
    public static final EventImpl<EntityUnload> ENTITY_UNLOAD = new EventImpl<>();

    public static class EventImpl<T> {
        private final List<T> handlers = new ArrayList<>();
        public void register(T handler) { handlers.add(handler); }
        public List<T> getHandlers() { return handlers; }
    }

    private ServerEntityEvents() {}
}
