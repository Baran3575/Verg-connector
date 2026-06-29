package net.fabricmc.fabric.api.client.screen.v1;

import net.minecraft.client.gui.screens.Screen;

public final class ScreenEvents {
    
    private ScreenEvents() {}

    // A stub API to satisfy mods looking for Fabric ScreenEvents.
    // In a complete implementation, this would map to NeoForge's ScreenEvent.Init and ScreenEvent.Render.
    
    public static void register() {
        System.out.println("[Verg Connector API] ScreenEvents loaded.");
    }
}
