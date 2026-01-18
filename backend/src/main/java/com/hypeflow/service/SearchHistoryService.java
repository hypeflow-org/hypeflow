package com.hypeflow.service;

import com.hypeflow.api.PopularWordDto;
import com.hypeflow.api.SearchHistoryDto;

import java.util.List;

public interface SearchHistoryService {

    List<SearchHistoryDto> getLastSearches(int limit);

    List<PopularWordDto> getMostSearchedWords(int limit);

}
