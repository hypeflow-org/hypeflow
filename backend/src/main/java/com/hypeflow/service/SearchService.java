package com.hypeflow.service;

import com.hypeflow.api.dto.SearchRequest;
import com.hypeflow.api.dto.SearchResponse;
import com.hypeflow.model.SearchHistory;

import java.util.List;

public interface SearchService {

    SearchResponse search(SearchRequest request);

    List<SearchHistory> getLastSearches(int limit);

    List<String> getMostSearchedWords(int limit);
}
