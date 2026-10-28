package com.techdocs.worker.infrastructure.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class DatabaseUrlParser {

    private DatabaseUrlParser() {
    }

    public record Parsed(String jdbcUrl, String username, String password) {
    }

    public static Parsed parse(String databaseUrl) {
        URI uri = URI.create(databaseUrl);
        String[] userInfo = uri.getUserInfo().split(":", 2);
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        return new Parsed(
                "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath(),
                URLDecoder.decode(userInfo[0], StandardCharsets.UTF_8),
                URLDecoder.decode(userInfo[1], StandardCharsets.UTF_8)
        );
    }
}
