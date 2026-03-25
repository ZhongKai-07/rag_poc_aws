package com.huatai.rag.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class ApplicationYamlAwsBindingsTest {

    @Test
    void applicationYamlExposesDedicatedBdaRegionBinding() throws Exception {
        ConfigurableEnvironment environment = new StandardEnvironment();
        List<org.springframework.core.env.PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        sources.forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("huatai.aws.bda-region"))
                .isEqualTo("us-east-1");
    }
}
