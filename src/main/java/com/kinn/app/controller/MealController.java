package com.kinn.app.controller;

import com.kinn.app.dto.DayMealsDto;
import com.kinn.app.dto.MealRecordDto;
import com.kinn.app.service.MealService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 食事管理API。ログイン中のユーザー(Authentication#getName() = 実効employeeId)の
 * データのみを参照・更新する。クライアントから employeeId を受け取る方式は廃止した。
 */
@RestController
@RequestMapping("/api/meal")
public class MealController {

    private final MealService mealService;

    public MealController(MealService mealService) {
        this.mealService = mealService;
    }

    /** 指定日(省略時は今日)の食事記録+栄養合計を取得 */
    @GetMapping("/day")
    public DayMealsDto getDay(
            @RequestParam(required = false) String date,
            Authentication authentication) {
        LocalDate d = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        return mealService.getDay(authentication.getName(), d);
    }

    /** 期間内の食事記録を日付ごとに取得(履歴画面用) */
    @GetMapping("/history")
    public List<DayMealsDto> getHistory(
            @RequestParam String from,
            @RequestParam String to,
            Authentication authentication) {
        return mealService.getHistory(authentication.getName(), LocalDate.parse(from), LocalDate.parse(to));
    }

    /** 食事を登録・更新(朝/昼/夕は当日分を上書き、間食は新規追加) */
    @PutMapping
    public MealRecordDto save(
            @Valid @RequestBody MealRecordDto dto,
            Authentication authentication) {
        return mealService.save(authentication.getName(), dto);
    }

    /** 食事記録を削除(自分の記録のみ。他ユーザーの記録IDを指定しても何も起きない) */
    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id,
            Authentication authentication) {
        mealService.delete(authentication.getName(), id);
    }
}
