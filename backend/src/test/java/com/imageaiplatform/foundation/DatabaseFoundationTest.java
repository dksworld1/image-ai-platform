package com.imageaiplatform.foundation;

import com.imageaiplatform.dao.FoundationProbeDao;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseFoundationTest {
    @Autowired
    @Qualifier("catalogFoundationProbeDao")
    private FoundationProbeDao catalogDao;

    @Autowired
    @Qualifier("archiveFoundationProbeDao")
    private FoundationProbeDao archiveDao;

    @Autowired
    @Qualifier("catalogTransactionManager")
    private PlatformTransactionManager catalogTransactionManager;

    @Autowired
    @Qualifier("archiveTransactionManager")
    private PlatformTransactionManager archiveTransactionManager;

    @Test
    void sameDaoImplementationUsesIndependentFileDatabases() {
        String value = "saved-" + UUID.randomUUID();
        assertThat(catalogDao).isNotSameAs(archiveDao);
        assertThat(catalogDao.getClass()).isSameAs(archiveDao.getClass());

        assertThat(catalogDao.insert(value)).isEqualTo(1);
        assertThat(catalogDao.countByValue(value)).isEqualTo(1);
        assertThat(archiveDao.countByValue(value)).isZero();

        assertThat(archiveDao.insert(value)).isEqualTo(1);
        assertThat(archiveDao.countByValue(value)).isEqualTo(1);
    }

    @Test
    void catalogTransactionManagerRollsBackDaoChangesAfterFailure() {
        TransactionTemplate transaction = new TransactionTemplate(catalogTransactionManager);

        String value = "catalog-rolled-back-" + UUID.randomUUID();
        assertThatThrownBy(() -> transaction.execute(status -> {
            catalogDao.insert(value);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("force rollback");

        assertThat(catalogDao.countByValue(value)).isZero();
    }

    @Test
    void archiveTransactionManagerRollsBackDaoChangesAfterFailure() {
        TransactionTemplate transaction = new TransactionTemplate(archiveTransactionManager);

        String value = "archive-rolled-back-" + UUID.randomUUID();
        assertThatThrownBy(() -> transaction.execute(status -> {
            archiveDao.insert(value);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("force rollback");

        assertThat(archiveDao.countByValue(value)).isZero();
    }
}
