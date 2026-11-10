package com.imageaiplatform.dataset.dao.impl;

import com.imageaiplatform.dataset.dao.DatasetDao;
import com.imageaiplatform.dataset.domain.Dataset;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDatasetDao implements DatasetDao {
    private final SqlSession sqlSession;

    public MyBatisDatasetDao(@Qualifier("appSqlSessionTemplate") SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    @Override
    public void insert(Dataset dataset) {
        sqlSession.insert("dataset.insert", dataset);
    }
}
