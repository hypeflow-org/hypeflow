package com.hypeflow.service;


import com.hypeflow.model.SearchHistory;

import java.util.List;

public interface SearchHistoryService {

    List<SearchHistory> getLastSearches(int limit);

    List<String> getMostSearchedWords(int limit);

}