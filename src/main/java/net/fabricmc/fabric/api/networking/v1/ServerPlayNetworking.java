package net.fabricmc.fabric.api.networking.v1;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPlayNetworking {
    private static final Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> RECEIVERS = new HashMap<>();

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        ServerPlayer player();
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        RECEIVERS.put(type, handler);
    }

    public static Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> getReceivers() {
        return RECEIVERS;
    }

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return true;
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        player.connection.send(payload);
    }
}
