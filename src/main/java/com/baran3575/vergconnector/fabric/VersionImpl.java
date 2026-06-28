package com.baran3575.vergconnector.fabric;

import net.fabricmc.loader.api.Version;

public class VersionImpl implements Version {
    private final String version;

    public VersionImpl(String version) {
        this.version = version;
    }

    @Override
    public String getFriendlyString() {
        return version;
    }

    @Override
    public int compareTo(Version o) {
        return this.getFriendlyString().compareTo(o.getFriendlyString());
    }

    @Override
    public String toString() {
        return version;
    }
}
