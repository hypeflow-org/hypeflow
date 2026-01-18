package com.hypeflow.api;

import com.hypeflow.service.SearchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search/history")
@RequiredArgsConstructor
public class SearchHistoryController {

    private final SearchHistoryService historyService;

    @GetMapping("/last")
    public List<SearchHistoryDto> getLastSearches(@RequestParam(defaultValue = "10") int limit) {
        return historyService.getLastSearches(limit);
    }

    @GetMapping("/popular")
    public List<PopularWordDto> getMostSearchedWords(@RequestParam(defaultValue = "10") int limit) {
        return historyService.getMostSearchedWords(limit);
    }

}
