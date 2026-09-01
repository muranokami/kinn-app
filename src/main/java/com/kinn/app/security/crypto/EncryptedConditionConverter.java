package com.kinn.app.security.crypto;

import com.kinn.app.entity.Condition;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Converter
@Component
public class EncryptedConditionConverter extends AbstractEncryptedEnumConverter<Condition> {
    public EncryptedConditionConverter(HealthDataEncryptor encryptor) {
        super(encryptor, Condition.class);
    }
}
