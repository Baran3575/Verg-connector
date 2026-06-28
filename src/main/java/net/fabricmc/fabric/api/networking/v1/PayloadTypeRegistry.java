package net.fabricmc.fabric.api.networking.v1;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;

public interface PayloadTypeRegistry {
    PayloadTypeRegistry PLAY_C2S = new Impl();
    PayloadTypeRegistry PLAY_S2C = new Impl();

    static PayloadTypeRegistry playC2S() {
        return PLAY_C2S;
    }

    static PayloadTypeRegistry playS2C() {
        return PLAY_S2C;
    }

    class Impl implements PayloadTypeRegistry {
        private final Map<CustomPacketPayload.Type<?>, StreamCodec<? super FriendlyByteBuf, ?>> codecs = new HashMap<>();

        @Override
        public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
            codecs.put(type, codec);
        }

        public Map<CustomPacketPayload.Type<?>, StreamCodec<? super FriendlyByteBuf, ?>> getCodecs() {
            return codecs;
        }
    }

    <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec);
}
