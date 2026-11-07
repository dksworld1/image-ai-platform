package com.imageaiplatform.foundation.config;

public final class RuntimeConfig {
    private static final RuntimeSettings SETTINGS = RuntimeSettingsLoader.load();

    private RuntimeConfig() {
    }

    public static RuntimeSettings settings() {
        return SETTINGS;
    }
}
