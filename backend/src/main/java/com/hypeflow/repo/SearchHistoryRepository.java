package com.hypeflow.repo;

import com.hypeflow.model.SearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    List<SearchHistory> findAllByOrderBySearchedAtDesc(Pageable pageable);

    @Query("SELECT s.word, COUNT(s) as cnt FROM SearchHistory s GROUP BY s.word ORDER BY cnt DESC")
    List<Object[]> findMostSearchedWords(Pageable pageable);

}
