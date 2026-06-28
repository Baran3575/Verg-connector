package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ClientPlayNetworking {
    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return false;
    }

    public static void send(CustomPacketPayload payload) {
    }
}
