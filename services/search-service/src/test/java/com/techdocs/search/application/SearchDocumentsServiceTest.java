package com.techdocs.search.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchDocumentsServiceTest {

    @Test
    void rejectsBlankQueryBeforeCallingTheIndex() {
        SearchDocumentsService service = new SearchDocumentsService(criteria -> {
            throw new AssertionError("No debe consultar el indice");
        }, 50, 900);
        assertThrows(SearchDocumentsService.InvalidQueryException.class,
                () -> service.execute("  ", 0, 10, null, null));
    }

    @Test
    void usesDefaultPageSize() {
        SearchDocumentsService service = new SearchDocumentsService(criteria -> {
            assertEquals(10, criteria.size());
            assertEquals("arquitectura", criteria.query());
            return new SearchPage(criteria.query(), 0, 10, 0, false, 1, java.util.List.of());
        }, 50, 900);
        SearchPage page = service.execute("arquitectura", 0, 0, null, null);
        assertEquals(0, page.total());
    }
}
