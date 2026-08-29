package com.kinn.app.service;

import com.kinn.app.dto.UserFoodPreferenceDto;
import com.kinn.app.entity.UserFoodPreference;
import com.kinn.app.repository.UserFoodPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ユーザーの食事の好み・制約(好きな食材/苦手な食材/アレルギー/食事制限/予算/調理時間/自炊-外食/料理レベル)を管理する。
 * 構成はHealthProfileServiceに倣っている。MealRecommendationServiceがAI献立提案の入力として利用する。
 */
@Service
public class UserFoodPreferenceService {

    private final UserFoodPreferenceRepository repository;

    public UserFoodPreferenceService(UserFoodPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UserFoodPreferenceDto getPreference(String employeeId) {
        return repository.findByEmployeeId(employeeId)
                .map(this::toDto)
                .orElseGet(() -> UserFoodPreferenceDto.builder().build());
    }

    @Transactional
    public UserFoodPreferenceDto savePreference(String employeeId, UserFoodPreferenceDto dto) {
        UserFoodPreference entity = repository.findByEmployeeId(employeeId)
                .orElseGet(UserFoodPreference::new);

        entity.setEmployeeId(employeeId);
        entity.setFavoriteFoods(dto.getFavoriteFoods());
        entity.setDislikedFoods(dto.getDislikedFoods());
        entity.setAllergies(dto.getAllergies());
        entity.setDietaryRestrictions(dto.getDietaryRestrictions());
        entity.setBudgetYen(dto.getBudgetYen());
        entity.setCookingMinutes(dto.getCookingMinutes());
        entity.setSelfCooked(dto.getSelfCooked());
        entity.setCookingLevel(dto.getCookingLevel());

        return toDto(repository.save(entity));
    }

    private UserFoodPreferenceDto toDto(UserFoodPreference p) {
        return UserFoodPreferenceDto.builder()
                .favoriteFoods(p.getFavoriteFoods())
                .dislikedFoods(p.getDislikedFoods())
                .allergies(p.getAllergies())
                .dietaryRestrictions(p.getDietaryRestrictions())
                .budgetYen(p.getBudgetYen())
                .cookingMinutes(p.getCookingMinutes())
                .selfCooked(p.getSelfCooked())
                .cookingLevel(p.getCookingLevel())
                .build();
    }
}
