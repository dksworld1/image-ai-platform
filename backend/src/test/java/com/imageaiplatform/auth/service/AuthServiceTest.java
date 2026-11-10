package com.imageaiplatform.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.imageaiplatform.auth.domain.AuthUser;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
    @Test
    void rejectsNullAccountStatusWithoutUnboxingOrIssuingToken() {
        var users = org.mockito.Mockito.mock(com.imageaiplatform.auth.dao.AuthUserDao.class);
        org.mockito.Mockito.when(users.findByUserId("member"))
                .thenReturn(new AuthUser("member-key", "member", null, "unused-hash", null, null, null, null, null));
        var service = new AuthService(
                users,
                new BCryptPasswordEncoder(),
                new JwtTokenService("test-only-signing-key-not-for-real-use-32-bytes", Duration.ofMinutes(15)));

        assertThatThrownBy(() -> service.login("member", "password"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid user ID or password.");
    }
}
