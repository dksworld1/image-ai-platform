package com.imageaiplatform.auth.dao.impl;

import com.imageaiplatform.auth.dao.UserAccountDao;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisUserAccountDao implements UserAccountDao {
    private final SqlSession sqlSession;

    public MyBatisUserAccountDao(@Qualifier("appSqlSessionTemplate") SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    @Override
    public void create(String username, String passwordHash) {
        sqlSession.insert("userAccount.create", Map.of("username", username, "passwordHash", passwordHash));
    }

    @Override
    public int setEnabled(String username, Boolean enabled) {
        return sqlSession.update("userAccount.setEnabled", Map.of("username", username, "enabled", enabled));
    }
}
