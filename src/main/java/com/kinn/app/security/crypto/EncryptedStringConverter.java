package com.kinn.app.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * String型の健康管理データ列(メモ等の自由記述)を透過的に暗号化するConverter。
 * Spring BootのHibernate自動設定がConverterをSpringのBeanコンテナ経由で解決するため、
 * {@code @Component}を付けてHealthDataEncryptorをコンストラクタインジェクションできる
 * (他のConverterも同じ方針。AbstractEncryptedEnumConverter参照)。
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final HealthDataEncryptor encryptor;

    public EncryptedStringConverter(HealthDataEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptor.decrypt(dbData);
    }
}
