package com.imageaiplatform.auth.dao;

public interface UserRoleDao {
    int grant(String username, String roleCode);
    int revoke(String username, String roleCode);
    void lockAdminRole();
    int countActiveAdmins();
    int countAdminAssignments();
    boolean isActiveAdmin(String username);
}
