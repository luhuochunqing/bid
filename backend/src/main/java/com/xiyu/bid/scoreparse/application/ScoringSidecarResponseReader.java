package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ScoringDocument;
import com.xiyu.bid.scoreparse.domain.ScoringExtractionReport;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parse only one JSON field at a time; the markdown writer enforces the character boundary. */
final class ScoringSidecarResponseReader {
    private final ObjectMapper mapper = new ObjectMapper();
    ScoringDocument read(InputStream input,long maxChars,long deadline) throws IOException {
        String text=null;ScoringExtractionReport extraction=ScoringExtractionReport.unknown();
        List<Map<String,Object>> refs=new ArrayList<>();
        try(var body=ScoringDeadline.bounded(input,64L*1024*1024,deadline);
            var parser=mapper.getFactory().createParser(body)) {
            if(parser.nextToken()!=JsonToken.START_OBJECT)throw new IOException("INVALID_SCORING_RESPONSE");
            while(parser.nextToken()!=JsonToken.END_OBJECT) {
                String name=parser.currentName();JsonToken token=parser.nextToken();
                if("markdown".equals(name)&&token==JsonToken.VALUE_STRING) {
                    var writer=new StringWriter();
                    parser.getText(new Writer(){
                        long chars;
                        @Override public void write(char[] c,int o,int n){chars+=n;if(chars>maxChars)throw new OversizedBidTextException();writer.write(c,o,n);}
                        @Override public void flush(){} @Override public void close(){}
                    });
                    text=writer.toString();
                } else if("extraction".equals(name)) {
                    JsonNode node=mapper.readTree(parser);
                    try {extraction=mapper.treeToValue(node,ScoringExtractionReport.class);}
                    catch(JsonProcessingException ex){throw new IOException("INVALID_SCORING_COVERAGE",ex);}
                } else if("sourceRefs".equals(name)||"pages".equals(name)) {
                    if(token!=JsonToken.START_ARRAY) {parser.skipChildren();continue;}
                    while(parser.nextToken()!=JsonToken.END_ARRAY) {
                        JsonNode ref=mapper.readTree(parser);
                        var location=new LinkedHashMap<String,Object>();
                        for(String key:List.of("pageNumber","partName","anchor","relationshipId","mediaName","textStart","textEnd","text","kind"))
                            if(ref.hasNonNull(key))location.put(key,mapper.convertValue(ref.path(key),Object.class));
                        if(!location.isEmpty())refs.add(location);
                    }
                } else parser.skipChildren();
            }
        }
        if(text==null || text.isBlank())throw new BidTextExtractionException("EMPTY_TEXT");
        if("UNKNOWN".equals(extraction.coverageStatus()))throw new IllegalStateException("SCORING_COVERAGE_UNKNOWN");
        return new ScoringDocument(text,refs,extraction);
    }
}
