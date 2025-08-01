package com.unboxy.gamemanagerservice.config;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

import java.time.Duration;

@Profile("prod")
@Configuration
public class SdkAsyncHttpClientConfig {

    @Value("${aws.client.maxConcurrency}")
    private Integer maxConcurrency;

    @Value("${aws.client.maxPendingConnectionAcquires}")
    private Integer maxPendingConnectionAcquires;

    @Value("${aws.client.connectionAcquisitionTimeoutSeconds}")
    private Integer connectionAcquisitionTimeoutSeconds;

    @Value("${aws.client.connectionMaxIdleTimeSeconds}")
    private Integer connectionMaxIdleTimeSeconds;

    @Value("${aws.client.connectionTimeoutSeconds}")
    private Integer connectionTimeoutSeconds;

    private SslProvider defaultSslProvider() {
        return OpenSsl.isAvailable() ? SslProvider.OPENSSL : SslProvider.JDK;
    }

    @Bean
    SdkAsyncHttpClient getClient() {
        return NettyNioAsyncHttpClient.builder()
                .sslProvider(defaultSslProvider())
                .maxPendingConnectionAcquires(maxPendingConnectionAcquires)
                .connectionAcquisitionTimeout(Duration.ofSeconds(connectionAcquisitionTimeoutSeconds))
                .connectionMaxIdleTime(Duration.ofSeconds(connectionMaxIdleTimeSeconds))
                .connectionTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
                .maxConcurrency(maxConcurrency)
                .build();
    }
}
