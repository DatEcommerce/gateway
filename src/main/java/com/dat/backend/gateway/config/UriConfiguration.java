package com.dat.backend.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "service")
public class UriConfiguration {
    private String product_uri;
}
