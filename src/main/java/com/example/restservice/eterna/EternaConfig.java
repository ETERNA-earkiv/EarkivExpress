package com.example.restservice.eterna;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "eterna.api")
public class EternaConfig {

    private String baseUrl;
    private String username;
    private String password;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String getUsername) {
        this.username = getUsername;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public ApiClient createApiClient() {
        return ApiClient.builder()
                .baseUrl(baseUrl)
                .setBasicAuth(username, password)
                .build();
    }
}