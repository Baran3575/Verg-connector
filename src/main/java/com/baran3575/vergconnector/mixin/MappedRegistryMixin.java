package com.baran3575.vergconnector.mixin;

import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.baran3575.vergconnector.helper.RegistryHelper;

@Mixin(value = MappedRegistry.class, priority = 500)
public class MappedRegistryMixin {

    @Inject(method = "validateWrite", at = @At("HEAD"), cancellable = true)
    private void onValidateWrite(CallbackInfo ci) {
        if (RegistryHelper.UNFROZEN.get()) {
            ci.cancel();
        }
    }
}
