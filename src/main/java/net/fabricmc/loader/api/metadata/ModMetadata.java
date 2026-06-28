package net.fabricmc.loader.api.metadata;

import java.util.Collection;

public interface ModMetadata {
    String getId();
    String getVersion();
    String getType();
    String getName();
    String getDescription();
    Collection<Person> getAuthors();
    Collection<Person> getContributors();
    ContactInformation getContact();
    Collection<String> getLicenses();
}
