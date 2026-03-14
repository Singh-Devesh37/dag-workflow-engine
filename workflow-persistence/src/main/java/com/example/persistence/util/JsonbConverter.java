package com.example.persistence.util;

import com.example.core.exception.PersistenceException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;

import java.util.Map;

public class JsonbConverter implements AttributeConverter<Map<String,Object>,String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        try{
            return attribute == null ? null : mapper.writeValueAsString(attribute);
        } catch (Exception e){
            throw new PersistenceException("Failed to convert map to json", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String s) {
        try{
            return s == null ? Map.of() : mapper.readValue(s, new TypeReference<Map<String, Object>>(){});
        } catch(Exception e){
            throw new PersistenceException("Failed to convert json to map", e);
        }
    }
}
