package com.ihsanharh.hiveutils.forms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class Form {
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    public abstract String getType();

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize form to JSON", e);
            return "{}";
        }
    }
}
