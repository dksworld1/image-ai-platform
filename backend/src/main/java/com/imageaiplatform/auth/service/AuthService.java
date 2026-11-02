package com.imageaiplatform.auth.service;

import com.imageaiplatform.auth.dao.AuthUserDao;
import com.imageaiplatform.auth.domain.AuthUser;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@ConditionalOnProperty(name = "app.database-migration-only", havingValue = "false", matchIfMissing = true)
public class AuthService {
    private final AuthUserDao authUserDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(AuthUserDao authUserDao, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.authUserDao = authUserDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public IssuedToken login(String username, String password) {
        AuthUser user = authUserDao.findByUsername(username);
        if (user == null || !user.enabled() || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new BadCredentialsException("Invalid username or password.");
        }
        return jwtTokenService.issue(user.username());
    }

    public record IssuedToken(String value, long expiresIn) {}
}
