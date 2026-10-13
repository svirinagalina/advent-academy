package com.adventacademy.meetingservice.infrastructure.config;

import com.adventacademy.meetingservice.adapter.websocket.MeetingSignalingHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    private final MeetingSignalingHandler signalingHandler;
    private final MeetingProperties meetingProperties;

    public WebSocketConfiguration(
            MeetingSignalingHandler signalingHandler,
            MeetingProperties meetingProperties) {
        this.signalingHandler = signalingHandler;
        this.meetingProperties = meetingProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        final String[] origins = meetingProperties.getAllowedOrigins().toArray(String[]::new);
        registry.addHandler(signalingHandler, "/ws/meetings")
                .setAllowedOrigins(origins);
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        final ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(meetingProperties.getMaxMessageBytes());
        container.setMaxSessionIdleTimeout(120_000L);
        return container;
    }
}
