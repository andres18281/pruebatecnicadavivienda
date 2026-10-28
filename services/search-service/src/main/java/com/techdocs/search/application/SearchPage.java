package com.techdocs.search.application;

import java.util.List;

public record SearchPage(
        String query,
        int page,
        int size,
        long total,
        boolean timedOut,
        long tookMs,
        List<SearchHit> results
) {
}
