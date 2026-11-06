package com.imageaiplatform.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.imageaiplatform.auth.dao.AuthUserDao;
import com.imageaiplatform.auth.dao.EffectivePermissionDao;
import com.imageaiplatform.auth.domain.AuthUser;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class AuthorizationServiceTest {
    private static final String READ = "dataset:item:read";
    private static final AuthUser ACTIVE = new AuthUser("member", "unused-hash", true);

    @Test
    void allowsOnlyExplicitlyGrantedPermission() {
        var service = new AuthorizationService(username -> ACTIVE, username -> Set.of(READ));

        assertThatCode(() -> service.requirePermission("member", READ)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requirePermission("member", "dataset:item:delete"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesUserWithoutRolesAndDoesNotTreatAdminOrWildcardAsPermission() {
        for (Set<String> permissions : Set.of(Set.<String>of(), Set.of("ADMIN", "dataset:*", "dataset:item:*"))) {
            var service = new AuthorizationService(username -> ACTIVE, username -> permissions);
            assertThatThrownBy(() -> service.requirePermission("member", READ))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void rejectsMissingAndDisabledAccountsBeforeLoadingPermissions() {
        EffectivePermissionDao mustNotLoad = username -> { throw new AssertionError("Must not load permissions"); };
        AuthUserDao missing = username -> null;
        AuthUserDao disabled = username -> new AuthUser(username, "unused-hash", false);
        AuthUserDao unknownStatus = username -> new AuthUser(username, "unused-hash", null);

        for (AuthUserDao users : new AuthUserDao[] {missing, disabled, unknownStatus}) {
            var service = new AuthorizationService(users, mustNotLoad);
            assertThatThrownBy(() -> service.requirePermission("member", READ))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Account is unavailable.");
        }
    }

    @Test
    void validatesInputBeforeQueryingStorage() {
        AuthUserDao mustNotLoad = username -> { throw new AssertionError("Must not query storage"); };
        var service = new AuthorizationService(mustNotLoad, username -> Set.of(READ));

        for (String username : new String[] {null, "", " ", "u".repeat(101)}) {
            assertThatThrownBy(() -> service.requirePermission(username, READ))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String code : new String[] {null, "", "user:manage", "dataset:item:*", "Dataset:item:read",
                "dataset:item:read\n", "a".repeat(147) + ":b:c"}) {
            assertThatThrownBy(() -> service.requirePermission("member", code))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void propagatesAccountAndPermissionLookupFailures() {
        var failure = new IllegalStateException("Storage unavailable");
        var accountFailure = new AuthorizationService(username -> { throw failure; }, username -> Set.of(READ));
        var permissionFailure = new AuthorizationService(username -> ACTIVE, username -> { throw failure; });

        for (AuthorizationService service : new AuthorizationService[] {accountFailure, permissionFailure}) {
            assertThatThrownBy(() -> service.requirePermission("member", READ)).isSameAs(failure);
        }
    }

    @Test
    void failsClosedWhenDaoViolatesNonNullContract() {
        var service = new AuthorizationService(username -> ACTIVE, username -> null);
        assertThatThrownBy(() -> service.requirePermission("member", READ))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void reflectsPermissionRevocationAndAccountDeactivationOnNextCall() {
        var permissions = new HashSet<>(Set.of(READ));
        AuthUser[] account = {ACTIVE};
        var service = new AuthorizationService(username -> account[0], username -> permissions);
        service.requirePermission("member", READ);

        permissions.clear();
        assertThatThrownBy(() -> service.requirePermission("member", READ))
                .isInstanceOf(AccessDeniedException.class);

        permissions.add(READ);
        account[0] = new AuthUser("member", "unused-hash", false);
        assertThatThrownBy(() -> service.requirePermission("member", READ))
                .isInstanceOf(BadCredentialsException.class);
    }
}
