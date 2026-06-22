package com.yasirkhan.trip.configurations;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Slf4j
public class FeignConfig {

    @Value("${app.security.internal-secret:my-super-secret-service-key}")
    private String internalSecret;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                try {
                    String serviceName = "TRIP_SERVICE";
                    long currentTimestamp = System.currentTimeMillis();

                    // 1. Combine the data (The Secret is NEVER sent over the network!)
                    String rawData = serviceName + currentTimestamp + internalSecret;

                    // 2. Hash it using SHA-256
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] hash = digest.digest(rawData.getBytes(StandardCharsets.UTF_8));
                    String signature = Base64.getEncoder().encodeToString(hash);

                    // 3. Attach the public data and the secure signature
                    template.header("X-Service-Name", serviceName);
                    template.header("X-Timestamp", String.valueOf(currentTimestamp));
                    template.header("X-Signature", signature);

                } catch (Exception e) {
                    log.error("Failed to sign internal request: {}", e.getMessage());
                }
            }
        };
    }
}