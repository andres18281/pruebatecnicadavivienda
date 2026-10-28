package com.techdocs.search.application;

public interface SearchIndexPort {
    SearchPage search(SearchCriteria criteria);
}
