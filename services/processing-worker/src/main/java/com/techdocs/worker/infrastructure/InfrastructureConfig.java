package com.techdocs.worker.infrastructure;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdocs.worker.infrastructure.config.DatabaseUrlParser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.minio.MinioClient;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class InfrastructureConfig {

    @Bean
    DataSource dataSource(@Value("${DATABASE_URL}") String databaseUrl) {
        DatabaseUrlParser.Parsed parsed = DatabaseUrlParser.parse(databaseUrl);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(parsed.jdbcUrl());
        config.setUsername(parsed.username());
        config.setPassword(parsed.password());
        config.setMaximumPoolSize(8);
        config.setPoolName("processing-worker");
        return new HikariDataSource(config);
    }

    @Bean
    ConnectionFactory connectionFactory(@Value("${RABBITMQ_URL}") String rabbitUrl) {
        URI uri = URI.create(rabbitUrl);
        int port = uri.getPort() == -1 ? 5672 : uri.getPort();
        CachingConnectionFactory factory = new CachingConnectionFactory(uri.getHost(), port);
        String[] userInfo = uri.getUserInfo().split(":", 2);
        factory.setUsername(URLDecoder.decode(userInfo[0], StandardCharsets.UTF_8));
        factory.setPassword(URLDecoder.decode(userInfo[1], StandardCharsets.UTF_8));
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    @Bean
    MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setTypePrecedence(Jackson2JsonMessageConverter.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    MinioClient minioClient(
            @Value("${S3_ENDPOINT}") String endpoint,
            @Value("${S3_ACCESS_KEY}") String accessKey,
            @Value("${S3_SECRET_KEY}") String secretKey
    ) {
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

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
                .setRequestConfigCallback(request -> request.setConnectTimeout(2_000).setSocketTimeout(20_000))
                .build();
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    ElasticsearchClient elasticsearchClient(RestClientTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
