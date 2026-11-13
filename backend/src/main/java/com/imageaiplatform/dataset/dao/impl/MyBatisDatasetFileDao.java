package com.imageaiplatform.dataset.dao.impl;

import com.imageaiplatform.dataset.dao.DatasetFileDao;
import com.imageaiplatform.dataset.domain.DatasetFile;
import com.imageaiplatform.dataset.domain.DatasetFileHistory;
import com.imageaiplatform.dataset.domain.DatasetFileTarget;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDatasetFileDao implements DatasetFileDao {
    private final SqlSession sqlSession;

    public MyBatisDatasetFileDao(@Qualifier("appSqlSessionTemplate") SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    @Override
    public Integer insert(DatasetFile entry) {
        return sqlSession.insert("datasetFile.insert", entry);
    }

    @Override
    public List<DatasetFile> findByDatasetId(String datasetId) {
        return sqlSession.selectList("datasetFile.findByDatasetId", datasetId);
    }

    @Override
    public DatasetFile findById(String datasetId, String fileId) {
        return sqlSession.selectOne("datasetFile.findById", Map.of("datasetId", datasetId, "fileId", fileId));
    }

    @Override
    public String lockDataset(String datasetId, String userKey) {
        return sqlSession.selectOne("datasetFile.lockDataset", Map.of("datasetId", datasetId, "userKey", userKey));
    }

    @Override
    public DatasetFile findActiveByFilename(String datasetId, String originalFilename) {
        return sqlSession.selectOne("datasetFile.findActiveByFilename", Map.of("datasetId", datasetId, "originalFilename", originalFilename));
    }

    @Override
    public Integer markDeleted(DatasetFile entry) {
        return sqlSession.update("datasetFile.markDeleted", entry);
    }

    @Override
    public Integer updateDescription(DatasetFile entry) {
        return sqlSession.update("datasetFile.updateDescription", entry);
    }

    @Override
    public Integer insertHistory(DatasetFileHistory history) {
        return sqlSession.insert("datasetFile.insertHistory", history);
    }

    @Override
    public Boolean canRead(String datasetId, String userKey) {
        return sqlSession.selectOne("datasetFile.canRead", Map.of("datasetId", datasetId, "userKey", userKey));
    }

    @Override
    public Boolean canManage(String datasetId, String userKey) {
        return sqlSession.selectOne("datasetFile.canManage", Map.of("datasetId", datasetId, "userKey", userKey));
    }

    @Override
    public List<DatasetFileTarget> findTargets(String userKey) {
        return sqlSession.selectList("datasetFile.findTargets", userKey);
    }
}
