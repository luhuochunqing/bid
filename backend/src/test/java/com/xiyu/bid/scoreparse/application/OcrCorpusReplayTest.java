package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.infrastructure.baidu.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/** Original PDFs, recorded real OCR, independently reviewed original-source expectations. */
@EnabledIfEnvironmentVariable(named="XIYU_OCR_CORPUS",matches=".+")
class OcrCorpusReplayTest {
 @Test void replayCompleteOriginalDocumentsAndReportEveryCase()throws Exception {
  var json=new ObjectMapper();var manifest=Path.of(System.getenv("XIYU_OCR_CORPUS"));
  var cases=json.readTree(manifest.toFile()).path("cases");assertThat(cases.isArray() && !cases.isEmpty()).isTrue();
  String split=System.getenv().getOrDefault("XIYU_OCR_CORPUS_SPLIT","all");var rows=new ArrayList<Map<String,Object>>();
  for(var item:cases){
   if(!split.equals("all") && !split.equals(item.path("split").asText()))continue;
   var row=new LinkedHashMap<String,Object>();row.put("case",item.path("id").asText());row.put("split",item.path("split").asText());
   row.put("taintedByDiagnosis",item.path("tainted_by_diagnosis").asBoolean());
   if("holdout".equals(item.path("split").asText()))assertThat(item.path("tainted_by_diagnosis").asBoolean()).as("diagnosed documents cannot be unseen acceptance").isFalse();
   var failures=new ArrayList<String>();row.put("failures",failures);
   try {
    assertThat(item.path("expected_file").asText()).as("independent original annotation missing").isNotBlank();
    var expected=json.readTree(Path.of(item.path("expected_file").asText()).toFile());
    assertThat(expected.path("annotation_verified").asBoolean()).as("independent source review required").isTrue();
    var pdf=Files.readAllBytes(Path.of(item.path("file").asText()));var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
    assertThat(hash).isEqualTo(item.path("sha256").asText());
    var root=Path.of(item.path("ocr_dir").asText());assertThat(Files.readString(root.resolve("source-sha256.txt"))).isEqualTo(hash);
    row.put("recordedInputProfile",Files.readString(root.resolve("input-profile.txt")));
    var props=new BaiduOcrProperties();props.setEnabled(true);var requested=new ArrayList<Integer>();
    var rawReads=new StringBuilder();
    var client=new BaiduTableOcrClient(props,json){
     @Override public BaiduTableModels.BaiduTableResponse extractTablesFromPdf(byte[] bytes,Integer page)throws java.io.IOException {
      requested.add(page);var payload=json.readTree(root.resolve("page-"+page+".json").toFile());
      var response=json.treeToValue(payload.has("tables")?payload.path("tables"):payload,BaiduTableModels.BaiduTableResponse.class);
      assertThat(response.isSuccess()).as("recorded OCR page "+page).isTrue();
      for(var table:response.tablesResult())for(var cell:table.body())rawReads.append(norm(cell.words()));
      for(var line:payload.path("text").path("words_result"))rawReads.append(norm(line.path("words").asText()));
      return response;
     }
    };
    var output=Path.of(item.path("output_dir").asText());Files.createDirectories(output);
    var baseline=json.readValue(Path.of(item.path("baseline_artifact").asText()).toFile(),com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.class);
    var artifact=new BaiduTableArtifactEnricher(client).enrichIfEnabled(baseline,pdf,"source.pdf");
    json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("actual-artifact.private.json").toFile(),artifact);
    var result=StructuredArtifactScoreExtraction.extract(artifact,"source.pdf","");
    var raw=rawReads.toString();
    var details=new ArrayList<Map<String,Object>>();row.put("ruleDifferences",details);
    boolean negative="missing_detail_reference".equals(expected.path("expectation").asText());
    row.put("caseType",negative?"missing_detail_reference":"positive_extraction");
    for(var truth:expected.path("items")) {
     String code=truth.path("code").asText();var matched=result.facts().stream().filter(f->code.isBlank()
       ? norm(f.dimension()).equals(norm(truth.path("canonical_display_dimension").asText(truth.path("dimension").asText())))
       : code.equals(f.code())).toList();
     if(matched.size()!=1){failures.add("ITEM_SET:"+code+":matches="+matched.size());continue;}
     var fact=matched.getFirst();
     if(!norm(fact.dimension()).equals(norm(truth.path("canonical_display_dimension").asText(truth.path("dimension").asText()))))failures.add("NAME:"+code);
     if(fact.weight()==null || fact.weight().compareTo(new java.math.BigDecimal(truth.path("weight").asText()))!=0)failures.add("WEIGHT:"+code);
     for(var rule:truth.path("rule_fragments"))if(!norm(fact.sourceText()).contains(norm(rule.asText()))) {
      boolean available=raw.contains(norm(rule.asText()));
      failures.add((available?"RULE_CONVERSION_LOSS:":"OCR_ORIGINAL_TEXT_DIFFERENCE:")+code);
      details.add(Map.of("code",code,"name",truth.path("dimension").asText(),"expected",rule.asText(),
        "presentInRawOcr",available,"actualRule",fact.sourceText()));
     }
     var pages=artifact.tables().stream().flatMap(t->t.cells().stream()).filter(c->fact.sourceCellIds().contains(c.cellId()))
       .map(c->c.sourcePage()).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
     for(var page:truth.path("pages"))if(!pages.contains(page.asInt()))failures.add("SOURCE_PAGE:"+code+":"+page.asInt());
    }
    if(!negative && result.facts().size()!=expected.path("items").size())failures.add("FACT_COUNT:"+result.facts().size()+"/"+expected.path("items").size());
    row.put("ocrRawExactForCheckedFragments",failures.stream().noneMatch(f->f.startsWith("OCR_ORIGINAL_TEXT_DIFFERENCE:")));
    row.put("expectedFactCount",expected.path("items").size());
    row.put("knownFactWeightSum",result.facts().stream().map(ScoreFact::weight).filter(java.util.Objects::nonNull).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add));
    row.put("factCount",result.facts().size());row.put("candidateCount",result.candidates().size());
    row.put("originalVerified",result.originalVerified());row.put("decision",result.completeness()==null?"NONE":result.completeness().decision().name());
    row.put("sourceDiagnostics",result.sourceDiagnostics());row.put("coveragePages",requested.size());row.put("requestedPages",requested);
    if(negative) {
     if(result.originalVerified() || !result.candidates().isEmpty())failures.add("MISSING_DETAIL_WRONGLY_PUBLISHED");
     if(result.sourceDiagnostics().stream().noneMatch(d->d.startsWith(expected.path("required_diagnostic_prefix").asText())))failures.add("MISSING_DETAIL_NOT_EXPLAINED");
    } else if(!result.originalVerified() || result.candidates().size()!=expected.path("expected_candidate_count").asInt())failures.add("AUTOMATIC_COMPLETION");
    json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("actual-production.private.json").toFile(),
      Map.of("facts",result.facts(),"qualifications",result.qualifications(),"sourceDiagnostics",result.sourceDiagnostics(),
        "defects",result.completeness()==null?List.of():result.completeness().defects()));
   }catch(Exception | AssertionError error){failures.add("CASE_FAILED:"+error.getClass().getSimpleName()+":"+String.valueOf(error.getMessage()).lines().findFirst().orElse(""));}
   row.put("systemConversionFailures",failures.stream().filter(f->!f.startsWith("OCR_ORIGINAL_TEXT_DIFFERENCE:")).toList());
   row.put("conversionExact",failures.stream().allMatch(f->f.startsWith("OCR_ORIGINAL_TEXT_DIFFERENCE:")));
   row.put("passed",failures.isEmpty());rows.add(row);
  }
  assertThat(rows).isNotEmpty();
  var report=new LinkedHashMap<String,Object>();report.put("cases",rows);report.put("denominator",rows.size());
  report.put("exactWholeDocumentPassed",rows.stream().filter(r->"positive_extraction".equals(r.get("caseType")) && Boolean.TRUE.equals(r.get("passed"))).count());
  report.put("positiveDenominator",rows.stream().filter(r->"positive_extraction".equals(r.get("caseType"))).count());
  report.put("negativeBlockingPassed",rows.stream().filter(r->"missing_detail_reference".equals(r.get("caseType")) && Boolean.TRUE.equals(r.get("passed"))).count());
  report.put("scope","MAINLINE_SIDECAR_RECORDED_OCR_TO_FACTS_NOT_LIVE_OR_PERSISTENCE");report.put("automaticCompletionMeasuredSeparately",true);report.put("split",split);
  if(split.equals("all"))for(String group:List.of("development","holdout")) {
   long families=java.util.stream.StreamSupport.stream(cases.spliterator(),false).filter(c->group.equals(c.path("split").asText()) && !c.path("family").asText().equals("missing-referenced-detail-attachment"))
     .map(c->c.path("family").asText()).filter(f->!f.isBlank() && !f.startsWith("pending")).distinct().count();
   if(families<2)report.put("generalizationStatus","NOT_PROVEN");
  }
  json.writerWithDefaultPrettyPrinter().writeValue(manifest.getParent().resolve("replay-report-"+split+".json").toFile(),report);
  assertThat(rows.stream().filter(r->!Boolean.TRUE.equals(r.get("passed"))).toList()).as("all source documents, no selective denominator").isEmpty();
  assertThat(report.get("generalizationStatus")).as("at least two template families per split required").isNull();
 }
 private static String norm(String text){return text==null?"":Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("\\s+","");}
}
