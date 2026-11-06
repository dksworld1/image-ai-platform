package com.imageaiplatform.auth.domain;

/** auth_role에 대응하는 역할 정의. 시스템 역할 여부는 인가 우회를 의미하지 않습니다. */
public record AuthRole(String roleCode, String displayName, String description, Boolean systemRole) {}
