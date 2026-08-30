package com.kinn.app.security.crypto;

import com.kinn.app.entity.DrinkingStatus;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Converter
@Component
public class EncryptedDrinkingStatusConverter extends AbstractEncryptedEnumConverter<DrinkingStatus> {
    public EncryptedDrinkingStatusConverter(HealthDataEncryptor encryptor) {
        super(encryptor, DrinkingStatus.class);
    }
}
