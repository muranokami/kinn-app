package com.kinn.app.security.crypto;

import com.kinn.app.entity.HealthConditionLevel;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Converter
@Component
public class EncryptedHealthConditionLevelConverter extends AbstractEncryptedEnumConverter<HealthConditionLevel> {
    public EncryptedHealthConditionLevelConverter(HealthDataEncryptor encryptor) {
        super(encryptor, HealthConditionLevel.class);
    }
}
