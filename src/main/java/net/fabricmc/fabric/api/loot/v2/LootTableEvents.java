package net.fabricmc.fabric.api.loot.v2;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import java.util.ArrayList;
import java.util.List;

/** Fabric shim: loot table modification callback. Currently a stub — loot modification requires
 *  access via NeoForge LootTableLoadEvent or data packs. Mods using this will have their
 *  handlers registered but not applied until a full NeoForge bridge is added. */
public final class LootTableEvents {
    public enum LootTableSource { BUILT_IN, DATA_PACK, MOD }

    public interface Modify {
        void modifyLootTable(ResourceLocation id, LootTable.Builder tableBuilder, LootTableSource source);
    }

    public static final EventImpl<Modify> MODIFY = new EventImpl<>();

    public static class EventImpl<T> {
        private final List<T> handlers = new ArrayList<>();
        public void register(T handler) { handlers.add(handler); }
        public List<T> getHandlers() { return handlers; }
    }

    private LootTableEvents() {}
}
