package com.hypeflow.api.controllers;

import com.hypeflow.api.dto.SearchRequest;
import com.hypeflow.api.dto.SearchResponse;
import com.hypeflow.model.SearchHistory;
import com.hypeflow.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        SearchResponse response = searchService.search(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<SearchHistory>> getHistory() {
        return ResponseEntity.ok(searchService.getLastSearches(10));
    }

}
