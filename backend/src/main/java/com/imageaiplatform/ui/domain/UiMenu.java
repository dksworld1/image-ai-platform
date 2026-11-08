package com.imageaiplatform.ui.domain;

public record UiMenu(String menuCode, String parentMenuCode, String nodeType,
        String displayName, String screenCode, Integer sortOrder, Boolean enabled) {}
