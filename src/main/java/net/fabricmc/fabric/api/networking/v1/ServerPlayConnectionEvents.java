package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.ArrayList;
import java.util.List;

/** Fabric shim: player join/disconnect events on the play phase, bridged from NeoForge PlayerLoggedIn/Out events. */
public final class ServerPlayConnectionEvents {
    public interface Join {
        void onPlayReady(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }
    public interface Disconnect {
        void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }

    public static final EventImpl<Join> JOIN = new EventImpl<>();
    public static final EventImpl<Disconnect> DISCONNECT = new EventImpl<>();

    public static class EventImpl<T> {
        private final List<T> handlers = new ArrayList<>();
        public void register(T handler) { handlers.add(handler); }
        public List<T> getHandlers() { return handlers; }
    }

    private ServerPlayConnectionEvents() {}
}
