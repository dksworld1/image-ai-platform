package com.imageaiplatform.auth.service;

import com.imageaiplatform.auth.dao.AuthUserDao;
import com.imageaiplatform.auth.domain.AuthUser;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthUserDao authUserDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(AuthUserDao authUserDao, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.authUserDao = authUserDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResult login(String userId, String password) {
        AuthUser user = authUserDao.findByUserId(userId);
        if (user == null || !Boolean.TRUE.equals(user.enabled()) || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new BadCredentialsException("Invalid user ID or password.");
        }
        return new LoginResult(jwtTokenService.issue(user.userKey()), user.userKey(), user.userId(), user.username());
    }

    public record IssuedToken(String value, Long expiresIn) {}

    public record LoginResult(IssuedToken token, String userKey, String userId, String username) {}
}
