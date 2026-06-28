package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class BlockEntityRendererRegistry {
    private static final List<Registration<?>> pending = new ArrayList<>();

    public static class Registration<T extends BlockEntity> {
        public final BlockEntityType<T> type;
        public final BlockEntityRendererProvider<? super T> factory;

        public Registration(BlockEntityType<T> type, BlockEntityRendererProvider<? super T> factory) {
            this.type = type;
            this.factory = factory;
        }
    }

    public static <T extends BlockEntity> void register(BlockEntityType<T> type, BlockEntityRendererProvider<? super T> factory) {
        pending.add(new Registration<>(type, factory));
    }

    public static List<Registration<?>> getPending() {
        return pending;
    }
}
