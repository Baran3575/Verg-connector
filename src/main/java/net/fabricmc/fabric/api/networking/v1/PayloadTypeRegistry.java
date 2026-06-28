package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;

public interface PayloadTypeRegistry {
    PayloadTypeRegistry PLAY_C2S = new DummyRegistry();
    PayloadTypeRegistry PLAY_S2C = new DummyRegistry();

    static PayloadTypeRegistry playC2S() {
        return PLAY_C2S;
    }

    static PayloadTypeRegistry playS2C() {
        return PLAY_S2C;
    }

    class DummyRegistry implements PayloadTypeRegistry {
        @Override
        public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
        }
    }

    <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec);
}
