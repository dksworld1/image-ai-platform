package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.imageaiplatform.config.FoundationSchemaInitializationConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class FoundationSchemaInitializationProfileTest {
    @Test
    void doesNotRegisterSchemaInitializersWhenProdIsCombinedWithLocal() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod", "local");
            context.register(FoundationSchemaInitializationConfiguration.class);
            context.refresh();

            assertThat(context.containsBean("catalogSchemaInitializer")).isFalse();
            assertThat(context.containsBean("archiveSchemaInitializer")).isFalse();
        }
    }
}
