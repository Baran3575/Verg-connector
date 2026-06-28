package net.fabricmc.loader.api.metadata;

import java.util.Optional;

public interface ContactInformation {
    Optional<String> get(String key);
}
