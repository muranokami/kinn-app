package com.kinn.app.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Double型の健康管理データ列(体重・身長・血圧・睡眠時間等)を、文字列化した上で
 * 透過的に暗号化するConverter。DB上は暗号文(text)として保持し、読み込み時に復号して
 * Doubleへ戻す。
 */
@Converter
@Component
public class EncryptedDoubleConverter implements AttributeConverter<Double, String> {

    private final HealthDataEncryptor encryptor;

    public EncryptedDoubleConverter(HealthDataEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(Double attribute) {
        return encryptor.encrypt(attribute == null ? null : String.valueOf(attribute));
    }

    @Override
    public Double convertToEntityAttribute(String dbData) {
        String plain = encryptor.decrypt(dbData);
        return plain == null ? null : Double.valueOf(plain);
    }
}
