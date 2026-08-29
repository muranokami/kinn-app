package com.kinn.app.repository;

import com.kinn.app.entity.AiMealSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AiMealSuggestionRepository extends JpaRepository<AiMealSuggestion, Long> {

    /** 指定日の最新(最も attemptNo が大きい)提案を取得する */
    Optional<AiMealSuggestion> findFirstByEmployeeIdAndSuggestionDateOrderByAttemptNoDesc(
            String employeeId, LocalDate suggestionDate);

    /** 「別の献立を提案」用に次の attemptNo を決めるため、これまでの件数を数える */
    long countByEmployeeIdAndSuggestionDate(String employeeId, LocalDate suggestionDate);

    List<AiMealSuggestion> findByEmployeeIdAndSuggestionDateBetweenOrderBySuggestionDateAscAttemptNoAsc(
            String employeeId, LocalDate from, LocalDate to);

    Optional<AiMealSuggestion> findByIdAndEmployeeId(Long id, String employeeId);
}
