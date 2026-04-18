package com.riyura.backend.common.converter;

import com.riyura.backend.common.model.MediaType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MediaTypeLowerCaseConverter implements AttributeConverter<MediaType, String> {

    @Override
    public String convertToDatabaseColumn(MediaType attribute) {
        if (attribute == null)
            return null;
        return attribute.name().toLowerCase();
    }

    @Override
    public MediaType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank())
            return null;
        for (MediaType type : MediaType.values()) {
            if (type.name().equalsIgnoreCase(dbData)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown media_type value in DB: '" + dbData + "'");
    }
}
