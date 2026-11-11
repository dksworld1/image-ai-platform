package com.imageaiplatform.workspace.dao.impl;

import com.imageaiplatform.workspace.dao.WorkspaceDao;
import com.imageaiplatform.workspace.domain.WorkspaceSummary;
import java.util.List;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisWorkspaceDao implements WorkspaceDao {
    private final SqlSession sqlSession;

    public MyBatisWorkspaceDao(@Qualifier("appSqlSessionTemplate") SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    @Override
    public List<WorkspaceSummary> findActiveByUserKey(String userKey) {
        return sqlSession.selectList("workspace.findActiveByUserKey", userKey);
    }
}
