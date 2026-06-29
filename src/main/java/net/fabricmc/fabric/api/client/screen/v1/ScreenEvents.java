package net.fabricmc.fabric.api.client.screen.v1;

import net.minecraft.client.gui.screens.Screen;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

public final class ScreenEvents {
    
    private ScreenEvents() {}

    @FunctionalInterface
    public interface AfterInit {
        void afterInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight);
    }
    
    @FunctionalInterface
    public interface AfterRender {
        void afterRender(Screen screen, GuiGraphics drawContext, int mouseX, int mouseY, float tickDelta);
    }

    public static final Event<AfterInit> AFTER_INIT = EventFactory.createArrayBacked(AfterInit.class, listeners -> (client, screen, scaledWidth, scaledHeight) -> {
        System.out.println("[Verg Connector API] Fired ScreenEvents.AFTER_INIT for: " + screen.getClass().getName());
        for (AfterInit listener : listeners) {
            listener.afterInit(client, screen, scaledWidth, scaledHeight);
        }
    });

    public static final Event<AfterRender> AFTER_RENDER = EventFactory.createArrayBacked(AfterRender.class, listeners -> (screen, drawContext, mouseX, mouseY, tickDelta) -> {
        // Suppress massive log spam, uncomment if debugging rendering
        // System.out.println("[Verg Connector API] Fired ScreenEvents.AFTER_RENDER for: " + screen.getClass().getName());
        for (AfterRender listener : listeners) {
            listener.afterRender(screen, drawContext, mouseX, mouseY, tickDelta);
        }
    });
}
