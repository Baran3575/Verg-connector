package net.fabricmc.fabric.api.itemgroup.v1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.resources.ResourceKey;

public final class ItemGroupEvents {
    private static final Map<ResourceKey<CreativeModeTab>, Event> EVENTS = new HashMap<>();

    public static Event modifyEntriesEvent(ResourceKey<CreativeModeTab> tab) {
        return EVENTS.computeIfAbsent(tab, k -> new Event());
    }

    public static Map<ResourceKey<CreativeModeTab>, Event> getEvents() {
        return EVENTS;
    }

    public static class Event {
        private final List<Consumer<Entries>> listeners = new ArrayList<>();

        public void register(Consumer<Entries> listener) {
            listeners.add(listener);
        }

        public List<Consumer<Entries>> getListeners() {
            return listeners;
        }
    }

    public interface Entries {
        void add(ItemStack stack);
        
        default void add(ItemLike item) {
            add(new ItemStack(item));
        }
    }
}
