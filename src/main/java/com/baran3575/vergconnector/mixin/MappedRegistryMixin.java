package com.baran3575.vergconnector.mixin;

import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MappedRegistry.class, priority = 500)
public class MappedRegistryMixin {

    @Inject(method = "validateWrite", at = @At("HEAD"), cancellable = true)
    private void onValidateWrite(ResourceKey<?> key, CallbackInfo ci) {
        if (RegistryHelper.UNFROZEN.get()) {
            ci.cancel();
        }
    }
}
