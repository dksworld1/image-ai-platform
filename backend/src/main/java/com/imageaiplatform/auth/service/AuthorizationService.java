package com.imageaiplatform.auth.service;

import com.imageaiplatform.auth.dao.AuthUserDao;
import com.imageaiplatform.auth.dao.EffectivePermissionDao;
import com.imageaiplatform.auth.domain.AuthUser;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/** app 데이터소스의 현재 계정 상태와 유효 권한을 확인하는 서비스입니다. */
@Service
public class AuthorizationService {
    private static final Pattern PERMISSION_CODE =
            Pattern.compile("[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*");

    private final AuthUserDao authUserDao;
    private final EffectivePermissionDao effectivePermissionDao;

    public AuthorizationService(AuthUserDao authUserDao, EffectivePermissionDao effectivePermissionDao) {
        this.authUserDao = Objects.requireNonNull(authUserDao);
        this.effectivePermissionDao = Objects.requireNonNull(effectivePermissionDao);
    }

    /**
     * 활성 사용자가 명시적으로 부여받은 권한을 보유한 경우에만 정상 반환합니다.
     *
     * <p>사용자명은 서버가 검증한 인증 정보에서 얻어야 하며 요청 본문의 사용자명을 신뢰하지 않습니다.
     * 권한 코드는 서버의 부여 가능 권한 카탈로그에서 선택한 도메인:리소스:동작 형식입니다.
     * 공개·인증 전용 API의 추적용 식별자는 이 메서드의 판정 대상이 아닙니다.
     *
     * <p>구현은 계정의 존재·활성 상태를 확인하고 유효 권한을 조회해야 합니다. 역할 이름이나
     * 시스템 역할 여부만으로 허용하지 않습니다. 조회 실패는 전파하며 정상 반환으로 바꾸지 않습니다.
     * 이 판정은 리소스 소유권·공유 범위 검사와 권한 회수에 대한 트랜잭션 통제를 대체하지 않습니다.
     *
     * @param authenticatedUsername 서버가 확인한 사용자명
     * @param permissionCode 필요한 부여 가능 권한 코드
     * @throws IllegalArgumentException 입력이 null·공백이거나 권한 코드 형식이 잘못된 경우
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         계정이 존재하지 않거나 비활성 상태인 경우
     * @throws org.springframework.security.access.AccessDeniedException
     *         활성 사용자에게 필요한 권한이 없는 경우
     */
    public void requirePermission(String authenticatedUsername, String permissionCode) {
        if (permissionCode == null || permissionCode.length() > 150
                || !PERMISSION_CODE.matcher(permissionCode).matches()) {
            throw new IllegalArgumentException("Permission code must use domain:resource:action format.");
        }

        Set<String> permissions = effectivePermissions(authenticatedUsername);
        if (!permissions.contains(permissionCode)) {
            throw new AccessDeniedException("Required permission is not granted.");
        }
    }

    /** 다른 모듈에서 사용할 활성 사용자의 현재 유효 권한입니다. */
    public Set<String> effectivePermissions(String authenticatedUsername) {
        requireActiveAccount(authenticatedUsername);
        return Set.copyOf(Objects.requireNonNull(
                effectivePermissionDao.findEffectivePermissionCodesByUsername(authenticatedUsername),
                "EffectivePermissionDao must return a non-null set."));
    }

    /** 인증만 필요한 API에서도 현재 계정 상태를 확인합니다. */
    public void requireActiveAccount(String authenticatedUsername) {
        if (authenticatedUsername == null || authenticatedUsername.isBlank() || authenticatedUsername.length() > 100) {
            throw new IllegalArgumentException("A valid authenticated username is required.");
        }
        AuthUser user = authUserDao.findByUsername(authenticatedUsername);
        if (user == null || !Boolean.TRUE.equals(user.enabled())) {
            throw new BadCredentialsException("Account is unavailable.");
        }
    }
}
