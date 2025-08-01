package com.unboxy.gamemanagerservice.config;

import lombok.RequiredArgsConstructor;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;

@Profile("prod")
@Configuration
@RequiredArgsConstructor
public class OpenSearchClientConfigCloud {

    @Value("${aws.opensearch.host}")
    private String host;

    @Value("${aws.opensearch.port}")
    private String port;

    @Value("${aws.region.static}")
    private String region;

    private final JacksonJsonpMapper jacksonJsonpMapper;

    private final SdkAsyncHttpClient sdkAsyncHttpClient;

    private final AwsCredentialsProvider awsCredentialsProvider;

//    @Bean
//    OpenSearchAsyncClient getAsyncClient() {
//        var credentialsProvider = InstanceProfileCredentialsProvider.create();
//
//        var transportOptions = AwsSdk2TransportOptions.builder().setCredentials(credentialsProvider)
//                .setMapper(jacksonJsonpMapper)
//                .build();
//
//        var transport = new AwsSdk2Transport(sdkAsyncHttpClient, host + ":" + port, "es", Region.of(region), transportOptions);
//
//
//        return new OpenSearchAsyncClient(transport, transportOptions);
//    }
    @Bean
    OpenSearchAsyncClient getAsyncClient() {
        var transportOptions = AwsSdk2TransportOptions.builder()
                .setCredentials(awsCredentialsProvider)
                .setMapper(jacksonJsonpMapper)
                .build();

        var transport = new AwsSdk2Transport(
                sdkAsyncHttpClient,
                host + ":" + port,
                "es",
                Region.of(region),
                transportOptions
        );

        return new OpenSearchAsyncClient(transport, transportOptions);
    }
}
