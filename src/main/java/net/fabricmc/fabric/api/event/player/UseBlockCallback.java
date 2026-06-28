package net.fabricmc.fabric.api.event.player;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import java.util.ArrayList;
import java.util.List;

/** Fabric shim: right-click block callback, bridged from NeoForge PlayerInteractEvent.RightClickBlock. */
public final class UseBlockCallback {
    public interface Event {
        InteractionResult interact(Player player, Level world, InteractionHand hand, BlockHitResult hitResult);
    }

    public static final EventImpl EVENT = new EventImpl();

    public static class EventImpl {
        private final List<Event> handlers = new ArrayList<>();
        public void register(Event handler) { handlers.add(handler); }
        public List<Event> getHandlers() { return handlers; }
    }

    private UseBlockCallback() {}
}
