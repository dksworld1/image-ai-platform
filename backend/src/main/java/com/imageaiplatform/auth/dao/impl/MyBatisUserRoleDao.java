package com.imageaiplatform.auth.dao.impl;

import com.imageaiplatform.auth.dao.UserRoleDao;
import java.util.Map;
import java.sql.Connection;
import java.sql.SQLException;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisUserRoleDao implements UserRoleDao {
    private final SqlSession sqlSession;

    public MyBatisUserRoleDao(@Qualifier("appSqlSessionTemplate") SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    @Override
    public int grant(String username, String roleCode) {
        return sqlSession.insert("userRole.grant", Map.of("username", username, "roleCode", roleCode));
    }

    @Override
    public int revoke(String username, String roleCode) {
        return sqlSession.delete("userRole.revoke", Map.of("username", username, "roleCode", roleCode));
    }

    @Override
    public void lockAdminRole() {
        try {
            Connection connection = sqlSession.getConnection();
            if (connection.getAutoCommit() || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalStateException("Administrator changes require a READ_COMMITTED transaction.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot determine administrator transaction isolation.", exception);
        }
        if (sqlSession.selectOne("userRole.lockAdminRole") == null) {
            throw new IllegalStateException("ADMIN role is unavailable; apply migrations first.");
        }
    }

    @Override
    public int countActiveAdmins() {
        return sqlSession.selectOne("userRole.countActiveAdmins");
    }

    @Override
    public int countAdminAssignments() {
        return sqlSession.selectOne("userRole.countAdminAssignments");
    }

    @Override
    public boolean isActiveAdmin(String username) {
        return sqlSession.<Integer>selectOne("userRole.countActiveAdminByUsername", username) > 0;
    }
}
