package net.fabricmc.fabric.api.event.player;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Fabric shim: attack entity callback, bridged from NeoForge AttackEntityEvent. */
public final class AttackEntityCallback {
    public interface Event {
        InteractionResult interact(Player player, Level world, InteractionHand hand, Entity entity, Optional<EntityHitResult> hitResult);
    }

    public static final EventImpl EVENT = new EventImpl();

    public static class EventImpl {
        private final List<Event> handlers = new ArrayList<>();
        public void register(Event handler) { handlers.add(handler); }
        public List<Event> getHandlers() { return handlers; }
    }

    private AttackEntityCallback() {}
}
