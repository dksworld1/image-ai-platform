package com.imageaiplatform.foundation.config;

import com.imageaiplatform.foundation.dao.FoundationProbeDao;
import com.imageaiplatform.foundation.dao.impl.MyBatisFoundationProbeDao;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.database-migration-only", havingValue = "false", matchIfMissing = true)
public class FoundationProbeDaoConfiguration {
    @Bean("catalogFoundationProbeDao")
    FoundationProbeDao catalogFoundationProbeDao(
            @Qualifier("catalogSqlSessionTemplate") SqlSessionTemplate sqlSessionTemplate) {
        return new MyBatisFoundationProbeDao(sqlSessionTemplate);
    }

    @Bean("archiveFoundationProbeDao")
    FoundationProbeDao archiveFoundationProbeDao(
            @Qualifier("archiveSqlSessionTemplate") SqlSessionTemplate sqlSessionTemplate) {
        return new MyBatisFoundationProbeDao(sqlSessionTemplate);
    }
}

