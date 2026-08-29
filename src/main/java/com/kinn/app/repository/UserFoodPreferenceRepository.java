package com.kinn.app.repository;

import com.kinn.app.entity.UserFoodPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserFoodPreferenceRepository extends JpaRepository<UserFoodPreference, Long> {

    Optional<UserFoodPreference> findByEmployeeId(String employeeId);
}
