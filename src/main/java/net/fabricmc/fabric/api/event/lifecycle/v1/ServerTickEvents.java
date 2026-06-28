package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

/** Fabric shim: server and world tick events bridged from NeoForge ServerTickEvent / LevelTickEvent. */
public final class ServerTickEvents {
    public interface StartTick {
        void onStartTick(MinecraftServer server);
    }
    public interface EndTick {
        void onEndTick(MinecraftServer server);
    }
    public interface StartWorldTick {
        void onStartTick(ServerLevel world);
    }
    public interface EndWorldTick {
        void onEndTick(ServerLevel world);
    }

    public static final EventImpl<StartTick> START_SERVER_TICK = new EventImpl<>();
    public static final EventImpl<EndTick> END_SERVER_TICK = new EventImpl<>();
    public static final EventImpl<StartWorldTick> START_WORLD_TICK = new EventImpl<>();
    public static final EventImpl<EndWorldTick> END_WORLD_TICK = new EventImpl<>();

    public static class EventImpl<T> {
        private final List<T> handlers = new ArrayList<>();
        public void register(T handler) { handlers.add(handler); }
        public List<T> getHandlers() { return handlers; }
    }

    private ServerTickEvents() {}
}
