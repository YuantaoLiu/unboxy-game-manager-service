package com.unboxy.gamemanagerservice.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribe.TranscribeAsyncClient;

import java.time.Duration;

@Configuration
public class TranscribeClient {

    @Value("${aws.region.static}")
    private String awsRegion;

    @Value("${aws.maxConcurrency}")
    private Integer maxConcurrency;

    @Autowired
    private AwsCredentialsProvider awsCredentialsProvider;

    @Bean
    public TranscribeAsyncClient transcribeAsyncClient() {
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .writeTimeout(Duration.ZERO)
                .maxConcurrency(maxConcurrency)
                .build();

        return TranscribeAsyncClient.builder().httpClient(httpClient)
                .region(Region.of(awsRegion))
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }
}
