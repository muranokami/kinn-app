package com.kinn.app.security.crypto;

import com.kinn.app.entity.SmokingStatus;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Converter
@Component
public class EncryptedSmokingStatusConverter extends AbstractEncryptedEnumConverter<SmokingStatus> {
    public EncryptedSmokingStatusConverter(HealthDataEncryptor encryptor) {
        super(encryptor, SmokingStatus.class);
    }
}
