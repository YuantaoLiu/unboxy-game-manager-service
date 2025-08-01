package com.unboxy.gamemanagerservice.config;

import lombok.RequiredArgsConstructor;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.client.RestClient;
import org.apache.http.HttpHost;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("!prod")
@Configuration
@RequiredArgsConstructor
public class OpenSearchClientConfigLocal {

    @Value("${cloud.aws.opensearch.host}")
    private String host;

    @Value("${cloud.aws.opensearch.port}")
    private String port;

    private final JacksonJsonpMapper jacksonJsonpMapper;

    @Bean
    OpenSearchAsyncClient getAsyncClientLocal() {
        // Use REST client transport to avoid HttpClient5 conflicts
        final RestClient restClient = RestClient.builder(
                new HttpHost(host, Integer.parseInt(port), "http")
        ).build();

        final RestClientTransport transport = new RestClientTransport(restClient, jacksonJsonpMapper);

        return new OpenSearchAsyncClient(transport);
    }
}
