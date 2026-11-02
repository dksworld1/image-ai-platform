package com.imageaiplatform.config;

import com.imageaiplatform.dao.FoundationProbeDao;
import com.imageaiplatform.dao.impl.MyBatisFoundationProbeDao;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
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
