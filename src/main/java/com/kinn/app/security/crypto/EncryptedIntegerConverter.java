package com.kinn.app.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Integer型の健康管理データ列(血圧・疲労度・運動時間等)を、文字列化した上で
 * 透過的に暗号化するConverter。DB上は暗号文(text)として保持し、読み込み時に復号して
 * Integerへ戻す。
 */
@Converter
@Component
public class EncryptedIntegerConverter implements AttributeConverter<Integer, String> {

    private final HealthDataEncryptor encryptor;

    public EncryptedIntegerConverter(HealthDataEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(Integer attribute) {
        return encryptor.encrypt(attribute == null ? null : String.valueOf(attribute));
    }

    @Override
    public Integer convertToEntityAttribute(String dbData) {
        String plain = encryptor.decrypt(dbData);
        return plain == null ? null : Integer.valueOf(plain);
    }
}
