package com.techdocs.document.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseUrlParserTest {

    @Test
    void convertsPostgresUrlToJdbc() {
        DatabaseUrlParser.Parsed parsed = DatabaseUrlParser.parse(
                "postgresql://techdocs_app:change_me_pg@postgres:5432/techdocs"
        );
        assertEquals("jdbc:postgresql://postgres:5432/techdocs", parsed.jdbcUrl());
        assertEquals("techdocs_app", parsed.username());
        assertEquals("change_me_pg", parsed.password());
    }
}
