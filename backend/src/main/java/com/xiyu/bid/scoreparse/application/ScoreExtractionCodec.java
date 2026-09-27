package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component @RequiredArgsConstructor
public class ScoreExtractionCodec {
    private final ObjectMapper mapper;
    public String encode(Object value) {
        try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("EXTRACTION_SERIALIZATION_FAILED",e);}
    }
    public String canonicalStoredJson(String value) {
        if(value==null) return null;
        try{return mapper.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(mapper.readValue(value,Object.class));}
        catch(JsonProcessingException e){throw new IllegalStateException("EXTRACTION_JSON_INVALID",e);}
    }
    public ScoreExtractionSnapshot decode(String value) {
        try{return mapper.readerFor(ScoreExtractionSnapshot.class).without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(value);}catch(JsonProcessingException e){throw new IllegalStateException("EXTRACTION_PAYLOAD_INVALID",e);}
    }
}
