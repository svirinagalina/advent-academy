package com.adventacademy.contentservice.config;

import com.adventacademy.contentservice.application.validation.AssetFileValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AssetUploadProperties.class)
public class AssetUploadConfig {

    @Bean
    public AssetFileValidator assetFileValidator(AssetUploadProperties properties) {
        return new AssetFileValidator(
                properties.getMaxFileSizeBytes(),
                properties.getAllowedMimeTypes()
        );
    }
}
