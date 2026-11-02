package com.imageaiplatform.auth.dao;

import com.imageaiplatform.auth.domain.AuthUser;

public interface AuthUserDao {
    AuthUser findByUsername(String username);
}
