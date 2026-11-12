package com.imageaiplatform.dataset.dao.impl;

import com.imageaiplatform.dataset.dao.DatasetDao;
import com.imageaiplatform.dataset.domain.Dataset;
import com.imageaiplatform.dataset.domain.DatasetListItem;
import java.util.List;
import java.util.Map;
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

    @Override
    public List<DatasetListItem> findVisibleToUser(String userKey) {
        return sqlSession.selectList("dataset.findVisibleToUser", userKey);
    }

    @Override
    public DatasetListItem findVisibleById(String datasetId, String userKey) {
        return sqlSession.selectOne("dataset.findVisibleById", Map.of("datasetId", datasetId, "userKey", userKey));
    }

    @Override
    public Boolean canDelete(String datasetId, String userKey) {
        return sqlSession.selectOne("dataset.canDelete", Map.of("datasetId", datasetId, "userKey", userKey));
    }

    @Override
    public Integer deleteAllowed(String datasetId, String userKey) {
        return sqlSession.delete("dataset.deleteAllowed", Map.of("datasetId", datasetId, "userKey", userKey));
    }
}
