package com.hypeflow.service;

import com.hypeflow.api.PopularWordDto;
import com.hypeflow.api.SearchHistoryDto;
import com.hypeflow.model.SearchHistory;
import com.hypeflow.repo.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchHistoryServiceImpl implements SearchHistoryService {

    private final SearchHistoryRepository historyRepository;

    @Override
    public List<SearchHistoryDto> getLastSearches(int limit) {
        return historyRepository
                .findAllByOrderBySearchedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<PopularWordDto> getMostSearchedWords(int limit) {
        return historyRepository
                .findMostSearchedWords(PageRequest.of(0, limit))
                .stream()
                .map(row -> new PopularWordDto((String) row[0], (Long) row[1]))
                .toList();
    }

    private SearchHistoryDto toDto(SearchHistory entity) {
        List<String> sourcesList = entity.getSources() != null && !entity.getSources().isBlank()
                ? Arrays.stream(entity.getSources().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList()
                : Collections.emptyList();

        return new SearchHistoryDto(
                entity.getWord(),
                entity.getStartDate(),
                entity.getEndDate(),
                sourcesList,
                entity.getTotalMentions(),
                entity.getSearchedAt()
        );
    }

}
