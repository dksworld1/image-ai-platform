package com.imageaiplatform.dao.impl;

import com.imageaiplatform.dao.FoundationProbeDao;
import java.util.Map;
import org.mybatis.spring.SqlSessionTemplate;

public class MyBatisFoundationProbeDao implements FoundationProbeDao {
    private static final String STATEMENT_PREFIX = "foundationProbe.";
    private final SqlSessionTemplate sqlSessionTemplate;

    public MyBatisFoundationProbeDao(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    @Override
    public int insert(String value) {
        return sqlSessionTemplate.insert(STATEMENT_PREFIX + "insert", Map.of("value", value));
    }

    @Override
    public long countByValue(String value) {
        Long count = sqlSessionTemplate.selectOne(STATEMENT_PREFIX + "countByValue", Map.of("value", value));
        return count == null ? 0 : count;
    }
}
