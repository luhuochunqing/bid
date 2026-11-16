package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.BidChapterDirtySet;
import com.xiyu.bid.scoreparse.domain.ImageBudget;
import com.xiyu.bid.scoreparse.domain.ScoreResultReusePolicy;
import com.xiyu.bid.scoreparse.domain.ScoringExtractionReport;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/** Slow work owns its file scope; the version service owns only short database transactions. */
final class ScoreScoringExecution {
    private final BidFileLoadingCoordinator loader;
    private final OpenAiScoreAnalyzer analyzer;
    private final ImageBudget imageBudget;
    private final ScoreScoringVersionService versions;
    private final BiConsumer<Integer,String> progress;
    private final ScoringRunReportCodec reports=new ScoringRunReportCodec();
    private final ScoreResultReusePolicy reuse=new ScoreResultReusePolicy();

    ScoreScoringExecution(BidFileLoadingCoordinator loader,OpenAiScoreAnalyzer analyzer,ImageBudget budget,
            ScoreScoringVersionService versions,BiConsumer<Integer,String> progress) {
        this.loader=loader;this.analyzer=analyzer;this.imageBudget=budget;this.versions=versions;this.progress=progress;
    }
    void execute(ScoreParseTask task,ScoreScoringVersionService.Snapshot snapshot) {
        long deadline=ScoringDeadline.from(task.getCreatedAt());
        progress.accept(5,"读取投标原件");
        Long documentId=null;
        try(var file=loader.openScoring(task.getProjectId(),task.getTaskId(),deadline)) {
            documentId=file.documentId();
            task.setBidContentHash(file.contentHash());
            if(!versions.recordInput(task,documentId))return;
            snapshot=versions.capture(task.getProjectId());
            var configuration=analyzer.scoringConfiguration();
            String modelPolicy=com.xiyu.bid.scoreparse.infrastructure.openai.ScoringModelPolicy.fingerprint(configuration);
            task.setRunSummary(reports.bindPolicy(task.getRunSummary(),modelPolicy));
            var identity=new ScoreResultReusePolicy.Identity(file.contentHash(),task.getItemSetHash(),ScoringRunReportCodec.ENGINE,modelPolicy);
            Map<Long,ScoreResult> preserved=new HashMap<>();
            var sources=snapshot.sources();
            snapshot.results().forEach((id,row)->{if(reuse.preserveEligible(identity,sources.get(id)))preserved.put(id,row);});
            String scope=task.getStage();
            var request=reports.scoring(task.getRunSummary()).path("request");
            List<Long> ids=new ArrayList<>();request.path("itemIds").forEach(id->ids.add(id.asLong()));
            if(!"ALL".equals(scope) && snapshot.visibleResults().values().stream()
                    .filter(row->row.getScoringTaskId()!=null).anyMatch(row->{
                        var source=sources.get(row.getScoreItemId());
                        return source==null || !identity.equals(source.identity());
                    })) {
                reject(task,file,"SCORING_REQUIRES_ALL");return;
            }
            var plan=new ScoreScoringItemPicker().plan(snapshot.items(),preserved,"",null,scope,ids,snapshot.previousStatuses());
            var selected=plan.toAssess().stream().map(ScoreItem::getId).collect(Collectors.toSet());
            if("AUTO".equals(task.getTriggerSource()) && reuse.autoSkipEligible(identity,
                    selected.stream().map(snapshot.sources()::get).filter(Objects::nonNull).toList(),selected.size())
                    && preserved.keySet().containsAll(selected)) {
                task.setStage("SKIPPED");
                task.setRunSummary(reports.complete(task,file.documentId(),selected.stream().map(snapshot.coverages()::get).filter(Objects::nonNull)
                                .findFirst().orElse(ScoringExtractionReport.unknown()),
                        snapshot.items().stream().filter(i->preserved.containsKey(i.getId())).map(i->preserved.get(i.getId())).toList(),
                        snapshot.sources(),null,0,0).json());
                versions.finish(task,null,null);return;
            }
            long extractionStart=System.nanoTime();
            var document=file.extract();
            long extractionMillis=(System.nanoTime()-extractionStart)/1_000_000;
            long assessmentStart=System.nanoTime();
            if("UNKNOWN".equals(document.extraction().coverageStatus())){reject(task,file,"SCORING_COVERAGE_UNKNOWN");return;}
            var assessor=new ScoreItemAssessor(analyzer,imageBudget);
            boolean extractionFailure=document.extraction().failedUnitCount()>0 || document.extraction().unreadUnitCount()>0;
            List<ScoreResult> rows=new ArrayList<>();boolean technicalFailure=extractionFailure;
            boolean trustedNewResult=false;
            for(int i=0;i<snapshot.items().size();i++) {
                var item=snapshot.items().get(i);
                if(!selected.contains(item.getId())) {
                    rows.add(preserved.containsKey(item.getId())?assessor.reuse(task,preserved.get(item.getId()))
                            :assessor.fallbackPending(task,List.of(item),"本次未选择此项，暂无当前原件评分").getFirst());
                    continue;
                }
                try {
                    ScoringDeadline.check(deadline);
                    progress.accept(10+80*(i+1)/snapshot.items().size(),"对标打分 "+(i+1)+"/"+snapshot.items().size());
                    var assessed=assessor.assessScoring(task,item,document,file,deadline,configuration);
                    boolean trusted=assessed.getActualScore()!=null || "SUBJECTIVE".equals(assessed.getReuseKind())
                            && assessed.getSuggestion()!=null && !assessed.getSuggestion().isBlank();
                    trustedNewResult|=trusted;
                    if(extractionFailure && !trusted) {
                        assessed.setReuseKind("EXTRACT_FAILED");
                        assessed.setMissedReason("原件提取技术故障，证据未完整读取，请重试本项；"
                                +Objects.toString(assessed.getMissedReason(),"可核验证据不足"));
                    }
                    rows.add(assessed);
                } catch(RuntimeException ex) {
                    technicalFailure=true;
                    var failed=assessor.fallbackPending(task,List.of(item),"模型调用或评分预算失败，请重试本项").getFirst();
                    failed.setReuseKind("FAILED");rows.add(failed);
                }
            }
            task.setStage("FULL");
            task.setChapterHashes(BidChapterHashCodec.encode(BidChapterDirtySet.toHashMap(BidChapterDirtySet.split(document.text()))));
            String failure=extractionFailure?"SCORING_EXTRACTION_FAILED":technicalFailure?"SCORING_ITEM_FAILED":null;
            var encoded=reports.complete(task,file.documentId(),document.extraction(),rows,snapshot.sources(),
                    failure,extractionMillis,(System.nanoTime()-assessmentStart)/1_000_000);
            task.setRunSummary(encoded.json());
            if(System.currentTimeMillis()<deadline)progress.accept(95,"打分结果落库");
            versions.finish(task,encoded.tooLarge() || extractionFailure&&!trustedNewResult?null:rows,
                    encoded.tooLarge()?"REPORT_TOO_LARGE":failure);
        } catch(RuntimeException ex) {
            var encoded=reports.rejection(task,documentId,ScoringExtractionReport.unknown(),
                    ex.getMessage()==null?"SCORING_EXTRACTION_FAILED":ex.getMessage());
            task.setRunSummary(encoded.json());versions.finish(task,null,ex.getMessage()==null?"SCORING_FAILED":ex.getMessage());
        }
    }
    private void reject(ScoreParseTask task,ScoringFileSession file,String reason) {
        task.setRunSummary(reports.rejection(task,file.documentId(),ScoringExtractionReport.unknown(),reason).json());
        versions.finish(task,null,reason);
    }
}
