package com.imageaiplatform.auth.dao;

public interface UserAccountDao {
    void create(String username, String passwordHash);
    int setEnabled(String username, Boolean enabled);
}
