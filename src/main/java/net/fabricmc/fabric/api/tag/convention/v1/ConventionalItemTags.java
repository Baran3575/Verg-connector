package net.fabricmc.fabric.api.tag.convention.v1;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Conventional Fabric item tags, mapped to the 'c' namespace used in 1.21. */
public final class ConventionalItemTags {
    public static final TagKey<Item> ORES           = tag("ores");
    public static final TagKey<Item> INGOTS         = tag("ingots");
    public static final TagKey<Item> NUGGETS        = tag("nuggets");
    public static final TagKey<Item> GEMS           = tag("gems");
    public static final TagKey<Item> DUSTS          = tag("dusts");
    public static final TagKey<Item> RAW_ORES       = tag("raw_ores");
    public static final TagKey<Item> TOOLS          = tag("tools");
    public static final TagKey<Item> SWORDS         = tag("tools/swords");
    public static final TagKey<Item> PICKAXES       = tag("tools/pickaxes");
    public static final TagKey<Item> AXES           = tag("tools/axes");
    public static final TagKey<Item> SHOVELS        = tag("tools/shovels");
    public static final TagKey<Item> HOES           = tag("tools/hoes");
    public static final TagKey<Item> BOWS           = tag("tools/bows");
    public static final TagKey<Item> ARMOR          = tag("armor");
    public static final TagKey<Item> HELMETS        = tag("armor/helmets");
    public static final TagKey<Item> CHESTPLATES    = tag("armor/chestplates");
    public static final TagKey<Item> LEGGINGS       = tag("armor/leggings");
    public static final TagKey<Item> BOOTS          = tag("armor/boots");
    public static final TagKey<Item> CHESTS         = tag("chests");
    public static final TagKey<Item> STORAGE_BLOCKS = tag("storage_blocks");
    public static final TagKey<Item> SEEDS          = tag("seeds");
    public static final TagKey<Item> CROPS          = tag("crops");
    public static final TagKey<Item> FOODS          = tag("foods");
    public static final TagKey<Item> DYES           = tag("dyes");
    public static final TagKey<Item> GLASS          = tag("glass_blocks");
    public static final TagKey<Item> GLASS_PANES    = tag("glass_panes");
    public static final TagKey<Item> SHULKER_BOXES  = tag("shulker_boxes");

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private ConventionalItemTags() {}
}
