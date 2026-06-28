package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;

public interface PayloadTypeRegistry {
    PayloadTypeRegistry PLAY_C2S = net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.PLAY_C2S;
    PayloadTypeRegistry PLAY_S2C = net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.PLAY_S2C;

    static PayloadTypeRegistry playC2S() {
        return PLAY_C2S;
    }

    static PayloadTypeRegistry playS2C() {
        return PLAY_S2C;
    }

    <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec);
}
