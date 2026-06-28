package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import java.util.ArrayList;
import java.util.List;

/** Fabric shim: command registration callback, bridged from NeoForge RegisterCommandsEvent. */
public final class CommandRegistrationCallback {
    public interface Event {
        void register(CommandDispatcher<CommandSourceStack> dispatcher,
                      CommandBuildContext registryAccess,
                      Commands.CommandSelection environment);
    }

    public static final EventImpl EVENT = new EventImpl();

    public static class EventImpl {
        private final List<Event> handlers = new ArrayList<>();
        public void register(Event handler) { handlers.add(handler); }
        public List<Event> getHandlers() { return handlers; }
    }

    private CommandRegistrationCallback() {}
}
