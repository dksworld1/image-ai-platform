package com.imageaiplatform.auth.dao;

import com.imageaiplatform.auth.domain.AuthUser;

public interface AuthUserDao {
    AuthUser findByUserId(String userId);

    AuthUser findByUserKey(String userKey);
}
