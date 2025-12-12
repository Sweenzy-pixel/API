package com.loginpage.API.model;

import java.util.HashMap;
import java.util.Map;

public class CsvRecord {

    private Map<String, String> fields = new HashMap<>();

    public void setField(String key, String value) {
        fields.put(key.trim(), value);
    }

    public String getField(String key) {
        return fields.getOrDefault(key.trim(), "");
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
