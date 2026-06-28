package net.fabricmc.fabric.api.tag.convention.v1;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Conventional Fabric block tags, mapped to the 'c' namespace used in 1.21. */
public final class ConventionalBlockTags {
    public static final TagKey<Block> ORES                = tag("ores");
    public static final TagKey<Block> ORES_IN_GROUND_STONE= tag("ores_in_ground/stone");
    public static final TagKey<Block> ORES_IN_GROUND_DEEPSLATE = tag("ores_in_ground/deepslate");
    public static final TagKey<Block> ORES_IN_GROUND_NETHERRACK = tag("ores_in_ground/netherrack");
    public static final TagKey<Block> CHESTS             = tag("chests");
    public static final TagKey<Block> BARRELS            = tag("barrels");
    public static final TagKey<Block> NEEDS_TOOL_LEVEL_1 = tag("needs_tool_level_1");
    public static final TagKey<Block> NEEDS_TOOL_LEVEL_2 = tag("needs_tool_level_2");
    public static final TagKey<Block> NEEDS_TOOL_LEVEL_3 = tag("needs_tool_level_3");
    public static final TagKey<Block> NEEDS_TOOL_LEVEL_4 = tag("needs_tool_level_4");
    public static final TagKey<Block> GLASS              = tag("glass_blocks");
    public static final TagKey<Block> GLASS_PANES        = tag("glass_panes");
    public static final TagKey<Block> BUDDING_BLOCKS     = tag("budding_blocks");
    public static final TagKey<Block> CLUSTERS           = tag("clusters");
    public static final TagKey<Block> BOOKSHELVES        = tag("bookshelves");
    public static final TagKey<Block> SANDSTONE_BLOCKS   = tag("sandstone_blocks");
    public static final TagKey<Block> DYED_BLOCKS        = tag("dyed_blocks");
    public static final TagKey<Block> CONCRETE           = tag("concrete");
    public static final TagKey<Block> TERRACOTTA         = tag("terracotta");

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private ConventionalBlockTags() {}
}
