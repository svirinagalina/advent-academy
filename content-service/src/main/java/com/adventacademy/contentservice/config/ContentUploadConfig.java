package com.adventacademy.contentservice.config;

import com.adventacademy.contentservice.application.validation.MarkdownFileValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-agnostic {@link MarkdownFileValidator} with its size limit,
 * keeping the configuration binding out of the application layer.
 */
@Configuration
public class ContentUploadConfig {

    @Bean
    public MarkdownFileValidator markdownFileValidator(
            @Value("${content.upload.max-file-size-bytes}") long maxFileSizeBytes) {
        return new MarkdownFileValidator(maxFileSizeBytes);
    }
}
