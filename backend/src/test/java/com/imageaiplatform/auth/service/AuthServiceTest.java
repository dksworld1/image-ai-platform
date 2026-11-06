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
        var service = new AuthService(
                username -> new AuthUser(username, "unused-hash", null),
                new BCryptPasswordEncoder(),
                new JwtTokenService("test-only-signing-key-not-for-real-use-32-bytes", Duration.ofMinutes(15)));

        assertThatThrownBy(() -> service.login("member", "password"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password.");
    }
}
