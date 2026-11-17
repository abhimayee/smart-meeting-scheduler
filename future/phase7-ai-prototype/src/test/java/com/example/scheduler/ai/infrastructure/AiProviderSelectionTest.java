package com.example.scheduler.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.scheduler.ai.application.MeetingAiService;
import com.example.scheduler.ai.application.MockMeetingAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class AiProviderSelectionTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderConfiguration.class);

    @Test
    void openAiProviderIsSelectedWithoutMockFallback() {
        contextRunner.withPropertyValues("ai.provider=openai", "ai.openai.api-key=test-only-key",
                        "ai.openai.model=test-model", "ai.openai.base-url=https://provider.example/v1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OpenAiMeetingAiService.class);
                    assertThat(context).doesNotHaveBean(MockMeetingAiService.class);
                });
    }

    @Test
    void aiProviderEnvironmentVariableSelectsOpenAiParser() {
        contextRunner.withInitializer(context -> {
                    var environment = context.getEnvironment();
                    environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                            "test-environment", Map.of(
                                    "AI_PROVIDER", "openai",
                                    "OPENAI_MODEL", "test-model",
                                    "OPENAI_BASE_URL", "http://localhost:11434/v1")));
                    try {
                        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                                .forEach(environment.getPropertySources()::addLast);
                    } catch (java.io.IOException ex) {
                        throw new IllegalStateException("Could not load application.yml for provider test", ex);
                    }
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MeetingAiService.class);
                    assertThat(context.getBean(MeetingAiService.class))
                            .isInstanceOf(OpenAiMeetingAiService.class);
                    assertThat(context).doesNotHaveBean(MockMeetingAiService.class);
                });
    }

    @Test
    void openAiCompatibleProviderDoesNotRequireApiKey() {
        contextRunner.withPropertyValues("ai.provider=openai", "ai.openai.model=test-model")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OpenAiMeetingAiService.class);
                    assertThat(context).doesNotHaveBean(MockMeetingAiService.class);
                });
    }

    @Test
    void mockProviderRemainsTheDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MockMeetingAiService.class);
            assertThat(context).doesNotHaveBean(OpenAiMeetingAiService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({OpenAiMeetingAiService.class, MockMeetingAiService.class})
    static class ProviderConfiguration {
        @Bean RestClient.Builder restClientBuilder() {
            return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory());
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
