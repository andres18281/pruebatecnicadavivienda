package com.techdocs.document.infrastructure.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class DatabaseUrlParser {

    private DatabaseUrlParser() {
    }

    public record Parsed(String jdbcUrl, String username, String password) {
    }

    public static Parsed parse(String databaseUrl) {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL es obligatorio.");
        }
        URI uri = URI.create(databaseUrl);
        if (uri.getUserInfo() == null || !uri.getUserInfo().contains(":")) {
            throw new IllegalArgumentException("DATABASE_URL debe incluir usuario y contrasena.");
        }
        String[] userInfo = uri.getUserInfo().split(":", 2);
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();
        return new Parsed(
                jdbcUrl,
                decode(userInfo[0]),
                decode(userInfo[1])
        );
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
