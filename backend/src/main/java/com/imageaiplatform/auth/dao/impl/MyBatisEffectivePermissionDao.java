package com.imageaiplatform.auth.dao.impl;

import com.imageaiplatform.auth.dao.EffectivePermissionDao;
import java.util.Set;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisEffectivePermissionDao implements EffectivePermissionDao {
    private final SqlSession sqlSession;

    public MyBatisEffectivePermissionDao(@Qualifier("appSqlSessionTemplate") SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    @Override
    public Set<String> findEffectivePermissionCodesByUserKey(String userKey) {
        return Set.copyOf(sqlSession.<String>selectList(
                "effectivePermission.findEffectivePermissionCodesByUserKey", userKey));
    }
}
