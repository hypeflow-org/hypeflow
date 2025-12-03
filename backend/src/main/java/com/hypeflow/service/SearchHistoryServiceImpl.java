package com.hypeflow.service;


import com.hypeflow.model.SearchHistory;
import com.hypeflow.repo.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchHistoryServiceImpl implements SearchHistoryService {

    private final SearchHistoryRepository historyRepository;

    @Override
    public List<SearchHistory> getLastSearches(int limit) {
        return historyRepository
                .findAllByOrderBySearchedAtDesc(PageRequest.of(0, limit));
    }

    @Override
    public List<String> getMostSearchedWords(int limit) {
        List<Object[]> results = historyRepository.findMostSearchedWords();
        return results.stream()
                .limit(limit)
                .map(r -> (String) r[0])
                .collect(Collectors.toList());
    }

}