package com.imageaiplatform.dataset.dao.impl;

import com.imageaiplatform.dataset.dao.DatasetImageDao;
import com.imageaiplatform.dataset.domain.DatasetImage;
import com.imageaiplatform.dataset.domain.DatasetImageHistory;
import com.imageaiplatform.dataset.domain.DatasetImageTarget;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDatasetImageDao implements DatasetImageDao {
    private final SqlSession sqlSession;

    public MyBatisDatasetImageDao(@Qualifier("appSqlSessionTemplate") SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    @Override
    public Integer insert(DatasetImage image) {
        return sqlSession.insert("datasetImage.insert", image);
    }

    @Override
    public List<DatasetImage> findByDatasetId(String datasetId) {
        return sqlSession.selectList("datasetImage.findByDatasetId", datasetId);
    }

    @Override
    public DatasetImage findById(String datasetId, String imageId) {
        return sqlSession.selectOne("datasetImage.findById", Map.of("datasetId", datasetId, "imageId", imageId));
    }

    @Override
    public String lockDataset(String datasetId, String userKey) {
        return sqlSession.selectOne("datasetImage.lockDataset", Map.of("datasetId", datasetId, "userKey", userKey));
    }

    @Override
    public DatasetImage findActiveByFilename(String datasetId, String originalFilename) {
        return sqlSession.selectOne("datasetImage.findActiveByFilename", Map.of("datasetId", datasetId, "originalFilename", originalFilename));
    }

    @Override
    public Integer markDeleted(DatasetImage image) {
        return sqlSession.update("datasetImage.markDeleted", image);
    }

    @Override
    public Integer updateDescription(DatasetImage image) {
        return sqlSession.update("datasetImage.updateDescription", image);
    }

    @Override
    public Integer insertHistory(DatasetImageHistory history) {
        return sqlSession.insert("datasetImage.insertHistory", history);
    }

    @Override
    public Boolean canRead(String datasetId, String userKey) {
        return sqlSession.selectOne("datasetImage.canRead", Map.of("datasetId", datasetId, "userKey", userKey));
    }

    @Override
    public Boolean canManage(String datasetId, String userKey) {
        return sqlSession.selectOne("datasetImage.canManage", Map.of("datasetId", datasetId, "userKey", userKey));
    }

    @Override
    public List<DatasetImageTarget> findTargets(String userKey) {
        return sqlSession.selectList("datasetImage.findTargets", userKey);
    }
}
