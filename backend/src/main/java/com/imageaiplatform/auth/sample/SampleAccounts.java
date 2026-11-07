package com.imageaiplatform.auth.sample;

import com.imageaiplatform.auth.domain.AuthUser;
import com.imageaiplatform.auth.service.AuthorizationService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** DB 대신 사용하는 샘플 계정·권한 저장소입니다. 운영 빈으로 자동 등록하지 않습니다. */
public class SampleAccounts {
    private final Map<String, Boolean> enabled = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> permissions = new ConcurrentHashMap<>();

    public void put(String username, Boolean active, Set<String> grantedPermissions) {
        enabled.put(username, active);
        permissions.put(username, Set.copyOf(grantedPermissions));
    }

    public AuthorizationService authorizationService() {
        return new AuthorizationService(
                username -> enabled.containsKey(username) ? new AuthUser(username, null, enabled.get(username)) : null,
                username -> permissions.getOrDefault(username, Set.of()));
    }
}
