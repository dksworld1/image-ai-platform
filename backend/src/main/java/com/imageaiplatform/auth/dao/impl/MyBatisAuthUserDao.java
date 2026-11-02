package com.imageaiplatform.auth.dao.impl;

import com.imageaiplatform.auth.dao.AuthUserDao;
import com.imageaiplatform.auth.domain.AuthUser;
import org.apache.ibatis.session.SqlSession;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Qualifier;

@Repository
public class MyBatisAuthUserDao implements AuthUserDao {
    private static final String NAMESPACE = "authUser";
    private final SqlSession sqlSession;

    public MyBatisAuthUserDao(@Qualifier("appSqlSessionTemplate") SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    @Override
    public AuthUser findByUsername(String username) {
        return sqlSession.selectOne(NAMESPACE + ".findByUsername", username);
    }
}
