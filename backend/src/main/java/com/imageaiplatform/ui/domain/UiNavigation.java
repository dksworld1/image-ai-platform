package com.imageaiplatform.ui.domain;

import java.util.List;

/** 메뉴는 parentMenuCode로 계층을 표현하는 정렬된 목록입니다. */
public record UiNavigation(List<UiScreen> screens, List<UiMenu> menus) {}
