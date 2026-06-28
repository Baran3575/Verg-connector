package net.fabricmc.loader.api.metadata;

import java.util.Collection;
import net.fabricmc.loader.api.Version;

public interface ModMetadata {
    String getId();
    Version getVersion();
    String getType();
    String getName();
    String getDescription();
    Collection<Person> getAuthors();
    Collection<Person> getContributors();
    ContactInformation getContact();
    Collection<String> getLicenses();
}
