package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiyu.bid.scoreparse.domain.ScoreResultReusePolicy;
import com.xiyu.bid.scoreparse.domain.ScoringExtractionReport;
import com.xiyu.bid.scoreparse.dto.ScoreScoringCommand;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded diagnostics, never a second copy of evidence text or signed URLs. */
public final class ScoringRunReportCodec {
    public static final String ENGINE = "actual-score-v4";
    private static final int MAX_BYTES = 61440;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    public record Encoded(String json, boolean tooLarge) {}

    public JsonNode scoring(String json) {
        try { return json == null ? mapper.createObjectNode() : mapper.readTree(json).path("scoring"); }
        catch (JsonProcessingException ex) { return mapper.createObjectNode(); }
    }
    public String request(ScoreScoringCommand command) {
        var encoded = encode(Map.of("schemaVersion",1,"engineVersion",ENGINE,"request",Map.of(
                "source",command.normalizedSource(),"scope",command.normalizedScope(),
                "itemIds",command.itemIds()==null?List.of():command.itemIds())));
        if(encoded.tooLarge()) throw new IllegalArgumentException("SCORING_REQUEST_TOO_LARGE");
        return encoded.json();
    }
    public String bindPolicy(String json,String fingerprint) {
        Map<String,Object> report=mapper.convertValue(scoring(json),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        report.put("modelPolicyFingerprint",fingerprint);return encode(report).json();
    }
    public Encoded bindInput(ScoreParseTask task,Long documentId) {
        Map<String,Object> report=mapper.convertValue(scoring(task.getRunSummary()),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        report.put("input",input(task,documentId,ScoringExtractionReport.unknown()));
        report.put("taskQuality","UNKNOWN");return encode(report);
    }
    public Encoded encodeReport(String json,String taskId) {
        if(json==null)return new Encoded(null,false);
        var report=scoring(json);
        if(!report.isObject())return encode(Map.of("reportIncomplete",true,"taskQuality","UNKNOWN"));
        Map<String,Object> value=mapper.convertValue(report,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        value.put("taskId",taskId);return encode(value);
    }
    public Encoded encode(Map<String,?> report) {
        ObjectNode scoring = mapper.valueToTree(report);
        scoring.put("schemaVersion",1); scoring.put("engineVersion",ENGINE);
        boolean trimmed = trim(scoring);
        if(trimmed) scoring.put("samplesTruncated",true);
        String json = serialize(Map.of("scoring",scoring));
        if(bytes(json)<=MAX_BYTES) return new Encoded(json,false);
        // Optional prose can be discarded; identity, requests and item outcomes cannot.
        removeOptional(scoring);
        scoring.put("samplesTruncated",true);
        json = serialize(Map.of("scoring",scoring));
        if(bytes(json)<=MAX_BYTES) return new Encoded(json,false);
        var failure=mapper.createObjectNode();failure.put("schemaVersion",1);failure.put("engineVersion",ENGINE);
        failure.put("reasonCode","REPORT_TOO_LARGE");failure.put("reportIncomplete",true);failure.put("taskQuality","UNKNOWN");
        String taskId=scoring.path("taskId").asText("");
        if(!taskId.isBlank()&&taskId.length()<=64)failure.put("taskId",taskId);
        var identity=mapper.createObjectNode();
        for(String field:List.of("fileHash","ruleFingerprint")) {
            String value=scoring.path("input").path(field).asText("");
            if(!value.isBlank()&&value.length()<=128)identity.put(field,value);
        }
        if(scoring.path("input").path("sourceDocumentId").isIntegralNumber())identity.set("sourceDocumentId",scoring.path("input").path("sourceDocumentId"));
        if(!identity.isEmpty())failure.set("input",identity);
        return new Encoded(serialize(Map.of("scoring",failure)),true);
    }
    public Encoded complete(ScoreParseTask task,Long documentId,ScoringExtractionReport coverage,
            List<ScoreResult> rows,Map<Long,ScoreResultReusePolicy.Source> retained,String failure,
            long extractionMillis,long assessmentMillis) {
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("taskId",task.getTaskId());
        var previous = scoring(task.getRunSummary());
        if(previous.has("request")) report.put("request",previous.path("request"));
        if(previous.has("modelPolicyFingerprint"))report.put("modelPolicyFingerprint",previous.path("modelPolicyFingerprint"));
        report.put("input",input(task,documentId,coverage));
        List<Map<String,Object>> items = new ArrayList<>();
        for(var row:rows) {
            var old = ("REUSED".equals(row.getReuseKind()) || !Objects.equals(task.getId(),row.getScoringTaskId())) ? retained.get(row.getScoreItemId()) : null;
            var data = new LinkedHashMap<String,Object>();
            data.put("scoreItemId",row.getScoreItemId());
            String outcome = old!=null?old.outcome():outcome(row);
            data.put("outcome",outcome);
            data.put("reasonCode","EXTRACT_FAILED".equals(row.getReuseKind())?"SCORING_EXTRACTION_FAILED":
                    "FAILED".equals(outcome)?"MODEL_OR_BUDGET_FAILURE":"NEEDS_REVIEW".equals(outcome)?"EVIDENCE_INSUFFICIENT":"");
            data.put("originTaskId",old!=null?old.originTaskId():task.getId());
            data.put("sourceFileHash",old!=null?old.identity().fileHash():task.getBidContentHash());
            data.put("sourceRuleFingerprint",old!=null?old.identity().ruleFingerprint():task.getItemSetHash());
            data.put("sourceEngineVersion",old!=null?old.identity().engineVersion():ENGINE);
            data.put("sourceModelPolicyFingerprint",old!=null?old.identity().modelPolicyFingerprint():previous.path("modelPolicyFingerprint").asText(null));
            data.put("sourceCoverageStatus",old!=null?old.coverageStatus():coverage.coverageStatus());
            items.add(data);
        }
        report.put("items",items);
        boolean failedRow=rows.stream().anyMatch(r->"FAILED".equals(outcome(r)));
        report.put("taskQuality",failure!=null||failedRow?"PARTIAL"
                :rows.stream().anyMatch(r->"NEEDS_REVIEW".equals(outcome(r)))?"NEEDS_REVIEW":"COMPLETE");
        if(failure!=null) report.put("reasonCode",failure);
        var timing=new LinkedHashMap<String,Object>();
        if(task.getCreatedAt()!=null) {
            timing.put("createdAt",task.getCreatedAt().toString());
            timing.put("elapsedMillis",Math.max(0,java.time.Duration.between(task.getCreatedAt(),java.time.LocalDateTime.now()).toMillis()));
        }
        if(task.getStartedAt()!=null)timing.put("startedAt",task.getStartedAt().toString());
        timing.put("extractionMillis",extractionMillis);timing.put("assessmentMillis",assessmentMillis);
        report.put("timing",timing);
        return encode(report);
    }
    public Encoded rejection(ScoreParseTask task,Long documentId,ScoringExtractionReport coverage,String reason) {
        var report = new LinkedHashMap<String,Object>();
        report.put("taskId",task.getTaskId());
        var previous=scoring(task.getRunSummary());
        if(previous.has("request"))report.put("request",previous.path("request"));
        report.put("input",input(task,documentId,coverage)); report.put("reasonCode",reason);report.put("taskQuality","UNKNOWN");
        return encode(report);
    }
    private Map<String,Object> input(ScoreParseTask task,Long id,ScoringExtractionReport coverage) {
        var input = new LinkedHashMap<String,Object>();
        input.put("sourceDocumentId",id);input.put("fileHash",task.getBidContentHash());
        input.put("ruleFingerprint",task.getItemSetHash());input.put("extraction",coverage);return input;
    }
    public ScoreResultReusePolicy.Source source(ScoreParseTask owner,Long itemId) {
        var report=scoring(owner.getRunSummary());
        for(var item:report.path("items")) if(item.path("scoreItemId").asLong()==itemId) {
            if(!item.hasNonNull("originTaskId")) return null;
            return new ScoreResultReusePolicy.Source(new ScoreResultReusePolicy.Identity(
                    item.path("sourceFileHash").asText(null),item.path("sourceRuleFingerprint").asText(null),
                    item.path("sourceEngineVersion").asText(null),item.path("sourceModelPolicyFingerprint").asText(null)),item.path("sourceCoverageStatus").asText("UNKNOWN"),
                    item.path("outcome").asText("UNKNOWN"),item.path("originTaskId").asLong());
        }
        return null;
    }
    public static String outcome(ScoreResult row) {
        if("FAILED".equals(row.getReuseKind()) || "EXTRACT_FAILED".equals(row.getReuseKind()))return "FAILED";
        if("SUBJECTIVE".equals(row.getReuseKind()))return "SUBJECTIVE";
        return row.getActualScore()!=null?"SCORED":"NEEDS_REVIEW";
    }
    private boolean trim(JsonNode node) {
        boolean changed=false;
        if(node.isObject()) {
            var fields=node.fields();
            while(fields.hasNext()) {
                var entry=fields.next();var value=entry.getValue();
                if("description".equals(entry.getKey()) && value.isTextual() && value.asText().length()>256) {
                    ((ObjectNode)node).put(entry.getKey(),value.asText().substring(0,256));changed=true;
                } else if(Set.of("unreadUnits","failedUnits","emptyUnits","sourceRefs").contains(entry.getKey()) && value.isArray()) {
                    var array=mapper.createArrayNode();
                    for(int i=0;i<Math.min(20,value.size());i++)array.add(locationSample(value.get(i)));
                    if(!array.equals(value)){((ObjectNode)node).set(entry.getKey(),array);changed=true;}
                } else changed|=trim(value);
            }
        } else if(node.isArray())for(var value:node)changed|=trim(value);
        return changed;
    }
    private ObjectNode locationSample(JsonNode source) {
        var sample=mapper.createObjectNode();
        if(source.path("pageNumber").isIntegralNumber())sample.set("pageNumber",source.path("pageNumber"));
        for(String field:List.of("partName","anchor","relationshipId","mediaName")) {
            String value=source.path(field).asText("");
            if(!value.isBlank())sample.put(field,value.substring(0,Math.min(256,value.length())));
        }
        String reason=source.path("reason").asText("");
        if(reason.matches("[A-Z][A-Z0-9_]{0,63}"))sample.put("reason",reason);
        return sample;
    }
    private void removeOptional(JsonNode node) {
        if(node.isObject()) {
            ((ObjectNode)node).remove(List.of("description","unreadUnits","failedUnits","emptyUnits","sourceRefs"));
            node.forEach(this::removeOptional);
        } else if(node.isArray())node.forEach(this::removeOptional);
    }
    private String serialize(Object value) {
        try{return mapper.writeValueAsString(value);}catch(JsonProcessingException ex){throw new IllegalArgumentException("SCORING_REPORT_INVALID",ex);}
    }
    private static int bytes(String value){return value.getBytes(StandardCharsets.UTF_8).length;}
}
