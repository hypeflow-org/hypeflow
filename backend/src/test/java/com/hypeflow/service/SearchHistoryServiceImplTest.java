package com.hypeflow.service;

import com.hypeflow.api.PopularWordDto;
import com.hypeflow.api.SearchHistoryDto;
import com.hypeflow.model.SearchHistory;
import com.hypeflow.repo.SearchHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchHistoryServiceImplTest {

    @Mock
    private SearchHistoryRepository historyRepository;

    @InjectMocks
    private SearchHistoryServiceImpl service;

    @Test
    void getLastSearchesGeneralTest() {
        SearchHistory entity = new SearchHistory();
        entity.setWord("bitcoin");
        entity.setStartDate(LocalDate.of(2026, 1, 10));
        entity.setEndDate(LocalDate.of(2026, 1, 12));
        entity.setSources("twitter,reddit");
        entity.setTotalMentions(123);
        entity.setSearchedAt(LocalDateTime.now());

        when(historyRepository.findAllByOrderBySearchedAtDesc(any(PageRequest.class)))
                .thenReturn(List.of(entity));

        List<SearchHistoryDto> result = service.getLastSearches(5);

        assertEquals(1, result.size());

        SearchHistoryDto dto = result.get(0);
        assertEquals("bitcoin", dto.word());
        assertEquals(123, dto.totalMentions());
        assertEquals(List.of("twitter", "reddit"), dto.sources());
    }

    @Test
    void getLastSearchesWithNullSources() {
        SearchHistory entity = new SearchHistory();
        entity.setWord("bitcoin");
        entity.setSources(null);

        when(historyRepository.findAllByOrderBySearchedAtDesc(any(PageRequest.class)))
                .thenReturn(List.of(entity));

        List<SearchHistoryDto> result = service.getLastSearches(3);

        assertEquals(1, result.size());
        assertTrue(result.get(0).sources().isEmpty());
    }

    @Test
    void getLastSearchesWithBlankSources() {
        SearchHistory entity = new SearchHistory();
        entity.setWord("bitcoin");
        entity.setSources("   ");

        when(historyRepository.findAllByOrderBySearchedAtDesc(any(PageRequest.class)))
                .thenReturn(List.of(entity));

        List<SearchHistoryDto> result = service.getLastSearches(3);

        assertEquals(1, result.size());
        assertTrue(result.get(0).sources().isEmpty());
    }

    @Test
    void getLastSearchesShouldTrimAndFilterSources() {
        SearchHistory entity = new SearchHistory();
        entity.setWord("dogecoin");
        entity.setSources(" twitter , reddit ,  , news  ");

        when(historyRepository.findAllByOrderBySearchedAtDesc(any(PageRequest.class)))
                .thenReturn(List.of(entity));

        List<SearchHistoryDto> result = service.getLastSearches(5);

        assertEquals(List.of("twitter", "reddit", "news"), result.get(0).sources());
    }

    @Test
    void getLastSearchesEmptyRepository() {
        when(historyRepository.findAllByOrderBySearchedAtDesc(any(PageRequest.class)))
                .thenReturn(List.of());

        List<SearchHistoryDto> result = service.getLastSearches(10);

        assertTrue(result.isEmpty());
    }



    @Test
    void getMostSearchedWordsGeneralTest() {
        Object[] row1 = new Object[] { "bitcoin", 100L };
        Object[] row2 = new Object[] { "ethereum", 50L };

        when(historyRepository.findMostSearchedWords(any(PageRequest.class)))
                .thenReturn(List.of(row1, row2));

        List<PopularWordDto> result = service.getMostSearchedWords(2);

        assertEquals(2, result.size());

        assertEquals("bitcoin", result.get(0).word());
        assertEquals(100L, result.get(0).count());

        assertEquals("ethereum", result.get(1).word());
        assertEquals(50L, result.get(1).count());
    }

    @Test
    void getMostSearchedWordsEmptyRepository() {
        when(historyRepository.findMostSearchedWords(any(PageRequest.class)))
                .thenReturn(List.of());

        List<PopularWordDto> result = service.getMostSearchedWords(5);

        assertTrue(result.isEmpty());
    }
}
