package net.fabricmc.fabric.api.event.registry;

import net.minecraft.core.Registry;

public interface RegistryAttributeHolder {
    RegistryAttributeHolder INSTANCE = new RegistryAttributeHolder() {
        @Override
        public RegistryAttributeHolder addAttribute(RegistryAttribute attribute) {
            return this;
        }

        @Override
        public boolean hasAttribute(RegistryAttribute attribute) {
            return false;
        }
    };

    static RegistryAttributeHolder get(Registry<?> registry) {
        return INSTANCE;
    }

    RegistryAttributeHolder addAttribute(RegistryAttribute attribute);
    boolean hasAttribute(RegistryAttribute attribute);
}
