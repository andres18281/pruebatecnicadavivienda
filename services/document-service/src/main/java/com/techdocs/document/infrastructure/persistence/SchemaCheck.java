package com.techdocs.document.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class SchemaCheck {

    private static final Logger log = LoggerFactory.getLogger(SchemaCheck.class);

    SchemaCheck(JdbcTemplate jdbc) {
        Integer count = jdbc.queryForObject("select count(*) from documents", Integer.class);
        log.info("document-service listo, documentos={}", count);
    }
}
