package com.baran3575.vergconnector.mixin;

public class RegistryHelper {
    public static final ThreadLocal<Boolean> UNFROZEN = ThreadLocal.withInitial(() -> false);
}
