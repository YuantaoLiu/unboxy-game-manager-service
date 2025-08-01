package com.unboxy.gamemanagerservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.time.Duration;

@Configuration
public class S3ClientConfigLocal {

    @Value("${aws.region.static}")
    private String awsRegion;

    @Value("${aws.maxConcurrency}")
    private Integer maxConcurrency;

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public S3AsyncClient s3client(AwsCredentialsProvider awsCredentialsProvider) {
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .writeTimeout(Duration.ZERO)
                .maxConcurrency(maxConcurrency)
                .build();

        S3Configuration serviceConfiguration = S3Configuration.builder()
                .checksumValidationEnabled(false)
                .chunkedEncodingEnabled(false)
                .build();

        S3AsyncClientBuilder b = S3AsyncClient.builder().httpClient(httpClient)
                .region(Region.of(awsRegion))
                .credentialsProvider(awsCredentialsProvider)
                .serviceConfiguration(serviceConfiguration);

        return b.build();
    }
}
