package net.fabricmc.loader.api;

public interface EntrypointContainer<T> {
    T getEntrypoint();
    ModContainer getProvider();
}
