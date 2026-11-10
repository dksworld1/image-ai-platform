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
    private static AuthUserDao users(java.util.function.Function<String, AuthUser> lookup) {
        var dao = org.mockito.Mockito.mock(AuthUserDao.class);
        org.mockito.Mockito.when(dao.findByUserKey(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> lookup.apply(call.getArgument(0)));
        return dao;
    }

    private static final String READ = "dataset:item:read";
    private static final AuthUser ACTIVE = new AuthUser("member", "member", null, "unused-hash", true, null, null, null, null);

    @Test
    void allowsOnlyExplicitlyGrantedPermission() {
        var service = new AuthorizationService(users(userKey -> ACTIVE), userKey -> Set.of(READ));

        assertThatCode(() -> service.requirePermission("member", READ)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requirePermission("member", "dataset:item:delete"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesUserWithoutRolesAndDoesNotTreatAdminOrWildcardAsPermission() {
        for (Set<String> permissions : Set.of(Set.<String>of(), Set.of("ADMIN", "dataset:*", "dataset:item:*"))) {
            var service = new AuthorizationService(users(userKey -> ACTIVE), userKey -> permissions);
            assertThatThrownBy(() -> service.requirePermission("member", READ))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void rejectsMissingAndDisabledAccountsBeforeLoadingPermissions() {
        EffectivePermissionDao mustNotLoad = userKey -> { throw new AssertionError("Must not load permissions"); };
        AuthUserDao missing = users(userKey -> null);
        AuthUserDao disabled = users(userKey -> new AuthUser(userKey, userKey, null, "unused-hash", false, null, null, null, null));
        AuthUserDao unknownStatus = users(userKey -> new AuthUser(userKey, userKey, null, "unused-hash", null, null, null, null, null));

        for (AuthUserDao users : new AuthUserDao[] {missing, disabled, unknownStatus}) {
            var service = new AuthorizationService(users, mustNotLoad);
            assertThatThrownBy(() -> service.requirePermission("member", READ))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Account is unavailable.");
        }
    }

    @Test
    void validatesInputBeforeQueryingStorage() {
        AuthUserDao mustNotLoad = users(userKey -> { throw new AssertionError("Must not query storage"); });
        var service = new AuthorizationService(mustNotLoad, userKey -> Set.of(READ));

        for (String userKey : new String[] {null, "", " ", "u".repeat(101)}) {
            assertThatThrownBy(() -> service.requirePermission(userKey, READ))
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
        var accountFailure = new AuthorizationService(users(userKey -> { throw failure; }), userKey -> Set.of(READ));
        var permissionFailure = new AuthorizationService(users(userKey -> ACTIVE), userKey -> { throw failure; });

        for (AuthorizationService service : new AuthorizationService[] {accountFailure, permissionFailure}) {
            assertThatThrownBy(() -> service.requirePermission("member", READ)).isSameAs(failure);
        }
    }

    @Test
    void failsClosedWhenDaoViolatesNonNullContract() {
        var service = new AuthorizationService(users(userKey -> ACTIVE), userKey -> null);
        assertThatThrownBy(() -> service.requirePermission("member", READ))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void reflectsPermissionRevocationAndAccountDeactivationOnNextCall() {
        var permissions = new HashSet<>(Set.of(READ));
        AuthUser[] account = {ACTIVE};
        var service = new AuthorizationService(users(userKey -> account[0]), userKey -> permissions);
        service.requirePermission("member", READ);

        permissions.clear();
        assertThatThrownBy(() -> service.requirePermission("member", READ))
                .isInstanceOf(AccessDeniedException.class);

        permissions.add(READ);
        account[0] = new AuthUser("member", "member", null, "unused-hash", false, null, null, null, null);
        assertThatThrownBy(() -> service.requirePermission("member", READ))
                .isInstanceOf(BadCredentialsException.class);
    }
}
