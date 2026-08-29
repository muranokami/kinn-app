package com.kinn.app.controller;

import com.kinn.app.dto.UserFoodPreferenceDto;
import com.kinn.app.service.UserFoodPreferenceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 食事の好み・制約(好きな食材/苦手な食材/アレルギー等)API。AI献立提案の入力設定画面から利用する。
 */
@RestController
@RequestMapping("/api/meal/preference")
public class UserFoodPreferenceController {

    private final UserFoodPreferenceService preferenceService;

    public UserFoodPreferenceController(UserFoodPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public UserFoodPreferenceDto getPreference(Authentication authentication) {
        return preferenceService.getPreference(authentication.getName());
    }

    @PutMapping
    public UserFoodPreferenceDto savePreference(
            @RequestBody UserFoodPreferenceDto dto,
            Authentication authentication) {
        return preferenceService.savePreference(authentication.getName(), dto);
    }
}
