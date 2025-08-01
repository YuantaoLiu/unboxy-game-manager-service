package com.unboxy.gamemanagerservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "anthropic")
@Data
public class AnthropicConfig {
    
    private String apiKey;
    private String model = "claude-3-5-sonnet-20240620";
    private Integer maxTokens = 4000;
}