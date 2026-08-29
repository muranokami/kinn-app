package com.kinn.app.service;

import com.kinn.app.dto.DayMealsDto;
import com.kinn.app.dto.MealNutritionSummaryDto;
import com.kinn.app.dto.MealRecordDto;
import com.kinn.app.entity.MealRecord;
import com.kinn.app.entity.MealType;
import com.kinn.app.repository.MealRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 食事記録(Phase 1: 登録・履歴・当日の可視化・基本栄養情報)を担当するサービス。
 * 週間/月間分析や健康データとの連携(Phase 2)、AI献立提案(Phase 3)は含まない。
 */
@Service
public class MealService {

    private final MealRecordRepository repository;

    public MealService(MealRecordRepository repository) {
        this.repository = repository;
    }

    /** 指定日の食事記録をまとめて取得する(記録がなければ空リスト・栄養は全てnull) */
    @Transactional(readOnly = true)
    public DayMealsDto getDay(String employeeId, LocalDate date) {
        List<MealRecord> records = repository.findByEmployeeIdAndMealDateOrderByMealTypeAscMealTimeAsc(employeeId, date);
        return toDayMealsDto(date, records);
    }

    /** 期間内の食事記録を日付ごとにまとめて取得する(履歴・週間/月間分析の元データ) */
    @Transactional(readOnly = true)
    public List<DayMealsDto> getHistory(String employeeId, LocalDate from, LocalDate to) {
        List<MealRecord> records =
                repository.findByEmployeeIdAndMealDateBetweenOrderByMealDateAscMealTypeAscMealTimeAsc(employeeId, from, to);

        Map<LocalDate, List<MealRecord>> byDate = records.stream()
                .collect(Collectors.groupingBy(MealRecord::getMealDate));

        List<DayMealsDto> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            result.add(toDayMealsDto(d, byDate.getOrDefault(d, List.of())));
        }
        return result;
    }

    /**
     * 食事を登録・更新する。
     * ・dto.id があればその1件を更新
     * ・朝/昼/夕は同じ日・区分の既存レコードがあれば上書き(なければ新規)
     * ・間食は常に新規追加(1日に複数件登録できる)
     */
    @Transactional
    public MealRecordDto save(String employeeId, MealRecordDto dto) {
        MealRecord entity;
        if (dto.getId() != null) {
            entity = repository.findByIdAndEmployeeId(dto.getId(), employeeId)
                    .orElseGet(MealRecord::new);
        } else if (dto.getMealType() != MealType.SNACK) {
            entity = repository
                    .findFirstByEmployeeIdAndMealDateAndMealTypeOrderByIdAsc(employeeId, dto.getMealDate(), dto.getMealType())
                    .orElseGet(MealRecord::new);
        } else {
            entity = new MealRecord();
        }

        entity.setEmployeeId(employeeId);
        entity.setMealDate(dto.getMealDate());
        entity.setMealType(dto.getMealType());
        entity.setMealTime(dto.getMealTime());
        entity.setDishName(dto.getDishName());
        // recipeIdは手動保存フォームからは触らせない(⑫の紐付けはMealService#linkRecipe経由のみ)。
        // 判定は「このentityがDB上まだ存在しないか(id未採番)」で行う(dto.getId()==nullでは
        // 朝/昼/夕の上書き保存[=既存行のentityをdto.id無しで受け取るケース]を区別できないため)。
        // 新規作成時のみdtoのrecipeIdを採用し、既存行の更新では既存のrecipeIdをそのまま保持する
        // ことで、食事内容を編集しただけでレシピのリンクが意図せず外れないようにする。
        if (entity.getId() == null) {
            entity.setRecipeId(dto.getRecipeId());
        }
        entity.setItems(dto.getItems());
        entity.setAmount(dto.getAmount());
        entity.setCalories(dto.getCalories());
        entity.setProteinG(dto.getProteinG());
        entity.setFatG(dto.getFatG());
        entity.setCarbsG(dto.getCarbsG());
        entity.setFiberG(dto.getFiberG());
        entity.setSaltG(dto.getSaltG());
        entity.setPhotoUrl(dto.getPhotoUrl());
        entity.setMemo(dto.getMemo());

        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(String employeeId, Long id) {
        repository.findByIdAndEmployeeId(id, employeeId).ifPresent(repository::delete);
    }

    /**
     * 食事記録にレシピを紐付ける(⑫。Phase 5)。RecipeService#generateForDishから、
     * レシピの解決・生成が成功した後に呼ばれる。employeeIdによる所有者チェックを行うため、
     * 他ユーザーの食事記録IDを指定しても何も起きない(㉔。findByIdAndEmployeeIdのJavadoc参照)。
     */
    @Transactional
    public void linkRecipe(String employeeId, Long mealRecordId, Long recipeId) {
        repository.findByIdAndEmployeeId(mealRecordId, employeeId).ifPresent(record -> {
            record.setRecipeId(recipeId);
            repository.save(record);
        });
    }

    // ------------------------------------------------------------------

    private DayMealsDto toDayMealsDto(LocalDate date, List<MealRecord> records) {
        List<MealRecordDto> breakfast = filterType(records, MealType.BREAKFAST);
        List<MealRecordDto> lunch = filterType(records, MealType.LUNCH);
        List<MealRecordDto> dinner = filterType(records, MealType.DINNER);
        List<MealRecordDto> snacks = filterType(records, MealType.SNACK);

        return DayMealsDto.builder()
                .date(date)
                .breakfast(breakfast)
                .lunch(lunch)
                .dinner(dinner)
                .snacks(snacks)
                .nutrition(summarizeNutrition(records))
                .breakfastRecorded(!breakfast.isEmpty())
                .lunchRecorded(!lunch.isEmpty())
                .dinnerRecorded(!dinner.isEmpty())
                .build();
    }

    private List<MealRecordDto> filterType(List<MealRecord> records, MealType type) {
        return records.stream()
                .filter(r -> r.getMealType() == type)
                .sorted(Comparator.comparing(MealRecord::getId))
                .map(this::toDto)
                .toList();
    }

    /** 入力されている栄養素だけを合計する(誰も入力していない項目はnullのまま=「未入力」を表す) */
    private MealNutritionSummaryDto summarizeNutrition(List<MealRecord> records) {
        Integer totalCalories = null;
        Double totalProtein = null;
        Double totalFat = null;
        Double totalCarbs = null;
        Double totalFiber = null;
        Double totalSalt = null;

        for (MealRecord r : records) {
            if (r.getCalories() != null) totalCalories = (totalCalories == null ? 0 : totalCalories) + r.getCalories();
            if (r.getProteinG() != null) totalProtein = (totalProtein == null ? 0 : totalProtein) + r.getProteinG();
            if (r.getFatG() != null) totalFat = (totalFat == null ? 0 : totalFat) + r.getFatG();
            if (r.getCarbsG() != null) totalCarbs = (totalCarbs == null ? 0 : totalCarbs) + r.getCarbsG();
            if (r.getFiberG() != null) totalFiber = (totalFiber == null ? 0 : totalFiber) + r.getFiberG();
            if (r.getSaltG() != null) totalSalt = (totalSalt == null ? 0 : totalSalt) + r.getSaltG();
        }

        return MealNutritionSummaryDto.builder()
                .totalCalories(totalCalories)
                .totalProteinG(round1(totalProtein))
                .totalFatG(round1(totalFat))
                .totalCarbsG(round1(totalCarbs))
                .totalFiberG(round1(totalFiber))
                .totalSaltG(round1(totalSalt))
                .build();
    }

    private Double round1(Double v) {
        return v == null ? null : Math.round(v * 10) / 10.0;
    }

    private MealRecordDto toDto(MealRecord r) {
        return MealRecordDto.builder()
                .id(r.getId())
                .mealDate(r.getMealDate())
                .mealType(r.getMealType())
                .mealTime(r.getMealTime())
                .dishName(r.getDishName())
                .recipeId(r.getRecipeId())
                .items(r.getItems())
                .amount(r.getAmount())
                .calories(r.getCalories())
                .proteinG(r.getProteinG())
                .fatG(r.getFatG())
                .carbsG(r.getCarbsG())
                .fiberG(r.getFiberG())
                .saltG(r.getSaltG())
                .photoUrl(r.getPhotoUrl())
                .memo(r.getMemo())
                .build();
    }
}
