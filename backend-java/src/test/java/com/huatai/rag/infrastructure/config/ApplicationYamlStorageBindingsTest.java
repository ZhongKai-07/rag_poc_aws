package com.huatai.rag.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class ApplicationYamlStorageBindingsTest {

    @Test
    void applicationYamlExposesS3StorageEnvironmentBindings() throws Exception {
        ConfigurableEnvironment environment = new StandardEnvironment();
        List<org.springframework.core.env.PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        sources.forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("huatai.storage.document-bucket"))
                .isEqualTo("");
        assertThat(environment.getProperty("huatai.storage.document-prefix"))
                .isEqualTo("");
        assertThat(environment.getProperty("huatai.storage.bda-output-prefix"))
                .isEqualTo("_bda_output");
    }
}
