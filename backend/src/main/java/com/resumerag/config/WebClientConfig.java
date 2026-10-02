package com.resumerag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Points at Ollama's OpenAI-compatible API by default. A local daemon needs
     * no credentials, so the Authorization header is only attached when a key is
     * configured (e.g. to target a hosted OpenAI-compatible gateway instead).
     */
    @Bean
    public WebClient ollamaWebClient(AppProperties appProperties) {
        String baseUrl = appProperties.getLlm().getBaseUrl();
        String apiKey = appProperties.getLlm().getApiKey();

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(StringUtils.hasText(baseUrl) ? baseUrl : "http://localhost:11434/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json");

        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return builder.build();
    }
}
