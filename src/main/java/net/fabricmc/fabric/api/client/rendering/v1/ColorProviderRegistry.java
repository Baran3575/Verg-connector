package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;

public interface ColorProviderRegistry<T, Provider> {
    ColorProviderRegistry<Block, BlockColor> BLOCK = new ColorProviderRegistryImpl<>();
    ColorProviderRegistry<Item, ItemColor> ITEM = new ColorProviderRegistryImpl<>();

    void register(Provider provider, T... objects);
    Provider get(T object);
}
