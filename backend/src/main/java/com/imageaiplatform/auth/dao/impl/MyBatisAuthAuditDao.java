package com.imageaiplatform.auth.dao.impl;

import com.imageaiplatform.auth.dao.AuthAuditDao;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAuthAuditDao implements AuthAuditDao {
    private final SqlSession sqlSession;

    public MyBatisAuthAuditDao(@Qualifier("appSqlSessionTemplate") SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    @Override
    public void recordRoleChange(String actorUsername, String targetUsername, String roleCode, String action) {
        sqlSession.insert("authAudit.recordRoleChange", Map.of(
                "actorUsername", actorUsername, "targetUsername", targetUsername,
                "roleCode", roleCode, "action", action));
    }
}
