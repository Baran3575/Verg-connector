package net.fabricmc.fabric.api.client.keybinding.v1;

import net.minecraft.client.KeyMapping;
import java.util.List;
import java.util.ArrayList;

public final class KeyBindingHelper {
    
    private static final List<KeyMapping> PENDING_KEYBINDINGS = new ArrayList<>();

    private KeyBindingHelper() {}

    /**
     * Registers the keybinding and adds it to the client.
     * @param keyBinding the keybinding to register
     * @return the registered keybinding
     */
    public static KeyMapping registerKeyBinding(KeyMapping keyBinding) {
        PENDING_KEYBINDINGS.add(keyBinding);
        System.out.println("[Verg Connector API] Queued KeyBinding for registration: " + keyBinding.getName());
        return keyBinding;
    }
    
    public static List<KeyMapping> getPending() {
        return PENDING_KEYBINDINGS;
    }
}
