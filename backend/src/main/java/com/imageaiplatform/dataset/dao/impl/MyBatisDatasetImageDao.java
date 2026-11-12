package com.imageaiplatform.dataset.dao.impl;

import com.imageaiplatform.dataset.dao.DatasetImageDao;
import com.imageaiplatform.dataset.domain.DatasetImage;
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
    public Boolean existsByOriginalFilename(String datasetId, String originalFilename) {
        return sqlSession.selectOne("datasetImage.existsByOriginalFilename", Map.of("datasetId", datasetId, "originalFilename", originalFilename));
    }

    @Override
    public Integer insert(DatasetImage image) {
        return sqlSession.insert("datasetImage.insert", image);
    }

    @Override
    public List<DatasetImage> findByDatasetId(String datasetId) {
        return sqlSession.selectList("datasetImage.findByDatasetId", datasetId);
    }
}
