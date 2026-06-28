package net.fabricmc.fabric.api.event.player;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

/** Fabric shim: right-click item callback, bridged from NeoForge PlayerInteractEvent.RightClickItem. */
public final class UseItemCallback {
    public interface Event {
        InteractionResultHolder<ItemStack> interact(Player player, Level world, InteractionHand hand);
    }

    public static final EventImpl EVENT = new EventImpl();

    public static class EventImpl {
        private final List<Event> handlers = new ArrayList<>();
        public void register(Event handler) { handlers.add(handler); }
        public List<Event> getHandlers() { return handlers; }
    }

    private UseItemCallback() {}
}
