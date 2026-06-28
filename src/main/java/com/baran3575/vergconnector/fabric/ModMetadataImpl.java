package com.baran3575.vergconnector.fabric;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

public class ModMetadataImpl implements ModMetadata {
    private final String id;
    private final Version version;
    private final String name;
    private final String description;

    public ModMetadataImpl(String id, String version, String name, String description) {
        this.id = id;
        this.version = new VersionImpl(version != null ? version : "1.0.0");
        this.name = name;
        this.description = description;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public String getType() {
        return "fabric";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Collection<Person> getAuthors() {
        return Collections.emptyList();
    }

    @Override
    public Collection<Person> getContributors() {
        return Collections.emptyList();
    }

    @Override
    public ContactInformation getContact() {
        return key -> Optional.empty();
    }

    @Override
    public Collection<String> getLicenses() {
        return Collections.emptyList();
    }
}
