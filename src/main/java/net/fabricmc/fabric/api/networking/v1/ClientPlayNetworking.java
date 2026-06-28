package net.fabricmc.fabric.api.networking.v1;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class ClientPlayNetworking {
    private static final Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> RECEIVERS = new HashMap<>();

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        LocalPlayer player();
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        RECEIVERS.put(type, handler);
    }

    public static Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> getReceivers() {
        return RECEIVERS;
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return true;
    }

    public static void send(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().send(payload);
        }
    }
}
