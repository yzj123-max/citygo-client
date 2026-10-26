package com.demo.logindemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {
    private String apiKey;
    private String apiUrl;
    private String modelName;
}