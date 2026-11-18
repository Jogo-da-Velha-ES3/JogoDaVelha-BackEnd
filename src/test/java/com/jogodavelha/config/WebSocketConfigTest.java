package com.jogodavelha.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    private final CorsProperties corsProperties = new CorsProperties();
    private final StompJwtAuthenticationInterceptor interceptor =
            mock(StompJwtAuthenticationInterceptor.class);
    private final WebSocketConfig config = new WebSocketConfig(corsProperties, interceptor);

    @Test
    void preservesStompReceiveOrderAndKeepsEndpointConfiguration() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration endpoint = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(endpoint);
        when(endpoint.setAllowedOriginPatterns(any(String[].class))).thenReturn(endpoint);

        config.registerStompEndpoints(registry);

        verify(registry).setPreserveReceiveOrder(true);
        verify(registry).addEndpoint("/ws");
        verify(endpoint).setAllowedOriginPatterns(any(String[].class));
    }
}
