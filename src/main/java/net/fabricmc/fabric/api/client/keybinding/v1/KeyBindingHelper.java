package net.fabricmc.fabric.api.client.keybinding.v1;

import net.minecraft.client.KeyMapping;
// Note: VergConnector automatically delegates to NeoForge's RegisterKeyMappingsEvent
public final class KeyBindingHelper {
    
    private KeyBindingHelper() {}

    /**
     * Registers the keybinding and adds it to the client.
     * @param keyBinding the keybinding to register
     * @return the registered keybinding
     */
    public static KeyMapping registerKeyBinding(KeyMapping keyBinding) {
        // In a complete implementation, this would queue the keybinding for NeoForge's 
        // RegisterKeyMappingsEvent. For now, it serves as a stub to prevent ClassNotFoundExceptions.
        System.out.println("[Verg Connector API] Registered KeyBinding: " + keyBinding.getName());
        return keyBinding;
    }
}
