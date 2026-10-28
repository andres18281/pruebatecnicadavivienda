package com.techdocs.search.infrastructure;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    RestClientTransport elasticsearchTransport(
            @Value("${ELASTICSEARCH_URL}") String url,
            @Value("${ELASTICSEARCH_USER}") String username,
            @Value("${ELASTICSEARCH_PASSWORD}") String password,
            ObjectMapper objectMapper
    ) {
        URI uri = URI.create(url);
        HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        RestClient restClient = RestClient.builder(host)
                .setDefaultHeaders(new org.apache.http.Header[]{new BasicHeader("Authorization", "Basic " + token)})
                .setRequestConfigCallback(request -> request.setConnectTimeout(2_000).setSocketTimeout(5_000))
                .build();
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    ElasticsearchClient elasticsearchClient(RestClientTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
