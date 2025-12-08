package com.hypeflow.api;

import com.hypeflow.model.SearchHistory;
import com.hypeflow.service.SearchHistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search/history")
public class SearchHistoryController {

    private final SearchHistoryService historyService;

    public SearchHistoryController(SearchHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/last")
    public List<SearchHistory> getLastSearches(@RequestParam(defaultValue = "10") int limit) {
        return historyService.getLastSearches(limit);
    }

    @GetMapping("/popular")
    public List<String> getMostSearchedWords(@RequestParam(defaultValue = "10") int limit) {
        return historyService.getMostSearchedWords(limit);
    }

}