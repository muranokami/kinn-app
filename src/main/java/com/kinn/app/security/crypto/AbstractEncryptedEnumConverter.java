package com.kinn.app.security.crypto;

import jakarta.persistence.AttributeConverter;

/**
 * Enum型の健康管理データ列(喫煙/飲酒状況・体調レベル等)を、Enum名(name())を文字列として
 * 暗号化した上でDBへ保存するConverterの共通実装。
 *
 * 喫煙/飲酒状況・体調レベルはそれ単体でも要配慮個人情報(健康状態)にあたるため、数値項目
 * (体重・血圧等)やメモと同じ方針で暗号化する。JPAの{@code @Enumerated}とは併用できない
 * (両方とも列の変換方式を決める仕組みで競合するため)ため、Enumごとにこのクラスを継承した
 * 具象Converterを用意する(EncryptedSmokingStatusConverter等)。
 */
public abstract class AbstractEncryptedEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final HealthDataEncryptor encryptor;
    private final Class<E> enumType;

    protected AbstractEncryptedEnumConverter(HealthDataEncryptor encryptor, Class<E> enumType) {
        this.encryptor = encryptor;
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return encryptor.encrypt(attribute == null ? null : attribute.name());
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        String plain = encryptor.decrypt(dbData);
        return plain == null ? null : Enum.valueOf(enumType, plain);
    }
}
