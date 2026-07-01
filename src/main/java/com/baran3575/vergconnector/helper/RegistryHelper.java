package com.baran3575.vergconnector.helper;

public class RegistryHelper {
    public static final ThreadLocal<Boolean> UNFROZEN = ThreadLocal.withInitial(() -> false);
}
