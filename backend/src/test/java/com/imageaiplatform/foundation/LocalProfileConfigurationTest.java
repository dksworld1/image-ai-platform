package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.imageaiplatform.config.MyBatisConfigurationProperties;
import com.imageaiplatform.dao.FoundationProbeDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.apache.ibatis.session.SqlSessionFactory;

@SpringBootTest(properties = {
        "app.datasources.catalog.url=jdbc:h2:mem:local-catalog;DB_CLOSE_DELAY=-1",
        "app.datasources.archive.url=jdbc:h2:mem:local-archive;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("local")
class LocalProfileConfigurationTest {
    @Autowired
    private MyBatisConfigurationProperties myBatisProperties;

    @Autowired
    @Qualifier("catalogSqlSessionFactory")
    private SqlSessionFactory catalogSqlSessionFactory;

    @Autowired
    @Qualifier("catalogFoundationProbeDao")
    private FoundationProbeDao catalogDao;

    @Test
    void importsLocalDatasourceAndLogConfiguration() {
        assertThat(myBatisProperties.getConfigLocation()).isEqualTo("classpath:config/mybatis-config.xml");
        assertThat(myBatisProperties.getMapperLocations()).isEqualTo("classpath*:mapper/**/*.xml");
        assertThat(catalogSqlSessionFactory.getConfiguration().isMapUnderscoreToCamelCase()).isTrue();

        String value = "local-profile-smoke";
        assertThat(catalogDao.countByValue(value)).isZero();
        assertThat(catalogDao.insert(value)).isEqualTo(1);
    }
}
