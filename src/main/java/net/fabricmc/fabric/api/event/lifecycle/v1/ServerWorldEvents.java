package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

/** Fabric shim: world load/unload events bridged from NeoForge LevelEvent.Load/Unload. */
public final class ServerWorldEvents {
    public interface Load {
        void onWorldLoad(MinecraftServer server, ServerLevel world);
    }
    public interface Unload {
        void onWorldUnload(MinecraftServer server, ServerLevel world);
    }

    public static final EventImpl<Load> LOAD = new EventImpl<>();
    public static final EventImpl<Unload> UNLOAD = new EventImpl<>();

    public static class EventImpl<T> {
        private final List<T> handlers = new ArrayList<>();
        public void register(T handler) { handlers.add(handler); }
        public List<T> getHandlers() { return handlers; }
    }

    private ServerWorldEvents() {}
}
