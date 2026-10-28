package com.techdocs.search.infrastructure;

import com.techdocs.search.application.SearchDocumentsService;
import com.techdocs.search.application.SearchDocumentsUseCase;
import com.techdocs.search.application.SearchPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchDocumentsUseCase search;

    public SearchController(SearchDocumentsUseCase search) {
        this.search = search;
    }

    @GetMapping
    public SearchPage search(
            @RequestParam("q") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "etiqueta", required = false) String etiqueta
    ) {
        return search.execute(query, page, size, categoria, etiqueta);
    }
}
