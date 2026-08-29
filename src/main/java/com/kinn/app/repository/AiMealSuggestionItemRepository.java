package com.kinn.app.repository;

import com.kinn.app.entity.AiMealSuggestionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiMealSuggestionItemRepository extends JpaRepository<AiMealSuggestionItem, Long> {

    List<AiMealSuggestionItem> findBySuggestionId(Long suggestionId);

    List<AiMealSuggestionItem> findBySuggestionIdIn(List<Long> suggestionIds);
}
