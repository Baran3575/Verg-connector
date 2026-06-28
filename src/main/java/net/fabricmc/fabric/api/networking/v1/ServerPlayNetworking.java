package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPlayNetworking {
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return false;
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
    }
}
