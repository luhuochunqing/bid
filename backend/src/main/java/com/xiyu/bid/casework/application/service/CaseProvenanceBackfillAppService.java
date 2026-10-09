// Input: ACTIVE 状态且来源章节不是已验证章节的存量知识案例（空标题 / 句子型伪标题 / 无编号小标题）
// Output: BackfillResult 统计（扫描项目/案例数、写回数、清空数、未变数、失败项目数；标书不可用按失败计、不写库）
// Pos: Backend/casework 应用编排——通过不可变端口回填来源，按项目分组装载标书
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.BidDocumentLoader;
import com.xiyu.bid.casework.application.MarkdownSourceSectionIndex;
import com.xiyu.bid.casework.application.MarkdownTitleRules;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Candidate;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Update;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存量案例来源章节重算服务。
 *
 * <p>切片入库规则收紧（拒绝句子型伪标题、且只承认"章节号 + 标题文字"的已验证章节）之前沉淀的案例，
 * source_section_title 可能是正文句子或无编号的小标题，前端会把它当成「原文所在标题」展示。
 * 本服务对这些案例重放 {@link MarkdownSourceSectionIndex#locate}：定位成功则写回真实章节（序号 + 标题文字），
 * 定位失败则清空五个来源字段——前端随即展示「暂无来源标题」（评分项标题另有独立字段展示），
 * 而不是展示一句正文或一个无法核对的标题。</p>
 *
 * <p><b>标书不可用 ≠ 定位失败</b>：项目无归档、无 BID 文件、读取/解析失败或未解析出任何章节时，
 * 来源无法查证，该项目整批跳过并计入 {@code projectsFailed}，绝不写库（否则会把"查不到"
 * 误判成"没有来源"，清掉存量字段）。只有拿到了可用标书并成功建立章节索引后，
 * "零命中/多命中"才按定位失败处理。</p>
 *
 * <p>幂等：只处理 source_section_title 不是已验证章节标题的 ACTIVE 案例（含空标题、句子型伪标题与
 * 无编号小标题，判据同 {@link MarkdownTitleRules#isChapterTitle(String)}），重复执行不再产生写入。</p>
 *
 * <p>快照一致性：写回附带扫描时刻的关键业务数据（正文/评分项标题/来源项目 ID），
 * 持久化原子校验无变化才写入；扫描后业务数据被改则整批拒绝（抛异常回滚），绝不写入过期来源。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaseProvenanceBackfillAppService {

    private final CaseProvenanceBackfillPort caseRepository;
    private final ProjectRepository projectRepository;
    private final BidDocumentLoader bidDocumentLoader;

    public record BackfillResult(
            int projectsScanned,
            int casesScanned,
            int casesUpdated,
            int casesCleared,
            int casesUnchanged,
            int projectsFailed) {
    }

    public BackfillResult recomputeAll() {
        // 需要重算的判据 = "现有标题不是已验证章节"（isChapterTitle 对 null/空白/句子型/无编号一律 false）
        List<Candidate> candidates = caseRepository.findActiveCandidates().stream()
                .filter(kase -> !MarkdownTitleRules.isChapterTitle(kase.sourceSectionTitle()))
                .toList();
        if (candidates.isEmpty()) {
            log.info("案例来源章节回填：无可重算案例（ACTIVE 案例来源标题均为已验证章节）");
            return new BackfillResult(0, 0, 0, 0, 0, 0);
        }

        Map<Long, List<Candidate>> grouped = groupByProject(candidates);
        int orphanCount = candidates.size() - grouped.values().stream().mapToInt(List::size).sum();
        if (orphanCount > 0) {
            log.warn("案例来源章节回填：{} 条案例缺少 sourceProjectId，无法定位标书，保持原值", orphanCount);
        }

        int updated = 0;
        int cleared = 0;
        int unchanged = orphanCount;
        int failed = 0;

        for (Map.Entry<Long, List<Candidate>> entry : grouped.entrySet()) {
            Long projectId = entry.getKey();
            LoadedBid loadedBid = loadUsableBid(projectId, entry.getValue().size());
            if (loadedBid == null) {
                failed++;
                continue;
            }

            List<Update> dirty = new ArrayList<>();
            for (Candidate kase : entry.getValue()) {
                Outcome outcome = recomputeOne(kase, loadedBid, dirty);
                if (outcome == Outcome.UPDATED) {
                    updated++;
                } else if (outcome == Outcome.CLEARED) {
                    cleared++;
                } else {
                    unchanged++;
                }
            }
            if (!dirty.isEmpty()) {
                caseRepository.saveAll(dirty);
            }
            log.info("案例来源章节回填：项目 {} 处理完成，写回 {} 条", projectId, dirty.size());
        }

        BackfillResult result = new BackfillResult(
                grouped.size(), candidates.size(), updated, cleared, unchanged, failed);
        log.info("案例来源章节回填完成：{}", result);
        return result;
    }

    private enum Outcome { UPDATED, CLEARED, UNCHANGED }

    /** 可用标书 + 其章节索引；取不到可用标书时为 null。 */
    private record LoadedBid(MarkdownSourceSectionIndex sectionIndex, BidDocumentLoader.BidDocument document) {
    }

    /**
     * 装载项目标书并建立章节索引；标书不可用（无归档、无 BID 文件、读取/解析失败、未解析出章节）
     * 时返回 null 并计数告警——调用方此时不得改写任何来源字段。
     */
    private LoadedBid loadUsableBid(Long projectId, int caseCount) {
        BidDocumentLoader.BidDocument bidDocument;
        try {
            bidDocument = bidDocumentLoader.loadBidDocument(projectNameOf(projectId), projectId);
        } catch (RuntimeException e) {
            log.warn("案例来源章节回填：项目 {} 标书装载失败，跳过该项目 {} 条案例：{}",
                    projectId, caseCount, e.getMessage());
            return null;
        }
        MarkdownSourceSectionIndex sectionIndex = parseSectionIndex(bidDocument);
        if (sectionIndex == null || sectionIndex.getSections().isEmpty()) {
            log.warn("案例来源章节回填：项目 {} 标书不可用或未解析出章节，来源无法查证，"
                    + "跳过该项目 {} 条案例（不改写来源字段）", projectId, caseCount);
            return null;
        }
        return new LoadedBid(sectionIndex, bidDocument);
    }

    private Outcome recomputeOne(
            Candidate kase,
            LoadedBid loadedBid,
            List<Update> dirty) {
        MarkdownSourceSectionIndex.Provenance provenance = locate(kase, loadedBid.sectionIndex());
        if (provenance != null) {
            // loadedBid 只在装载成功时构造，document() 非空
            BidDocumentLoader.BidDocument bidDocument = loadedBid.document();
            dirty.add(new Update(kase.id(), provenance.title(), provenance.path(), provenance.index(),
                    bidDocument.documentName(), provenance.pageNumber() != null
                            ? provenance.pageNumber() : bidDocument.pageNumber(),
                    kase.responseText(), kase.scoringPointTitle(), kase.sourceProjectId()));
            return Outcome.UPDATED;
        }

        if (hasNoSourceProvenance(kase)) {
            return Outcome.UNCHANGED;
        }
        dirty.add(new Update(kase.id(), null, null, null, null, null,
                kase.responseText(), kase.scoringPointTitle(), kase.sourceProjectId()));
        return Outcome.CLEARED;
    }

    /**
     * 五个来源字段是否全部为空。<b>清理判据必须覆盖全部五个字段</b>：存量数据可能出现"标题已空、
     * 路径/页码仍残留"的部分写入状态（早期只写标题、或定位失败只清了标题的旧版本所为），
     * 只看标题会把这些字段留在库里，与其他消费者（导出、检索、后台排查）对同一案例的认知不一致。
     */
    private static boolean hasNoSourceProvenance(Candidate kase) {
        return isBlank(kase.sourceSectionTitle())
                && isBlank(kase.sourceSectionPath())
                && isBlank(kase.sourceDocumentName())
                && kase.sourceSectionIndex() == null
                && kase.sourcePageNumber() == null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private MarkdownSourceSectionIndex.Provenance locate(Candidate kase, MarkdownSourceSectionIndex sectionIndex) {
        String snippet = kase.responseText();
        if (sectionIndex == null || snippet == null || snippet.isBlank()) {
            return null;
        }
        try {
            return sectionIndex.locate(snippet, kase.scoringPointTitle());
        } catch (RuntimeException e) {
            log.warn("案例 {} 来源章节定位异常，降级为清空：{}", kase.id(), e.getMessage());
            return null;
        }
    }

    private MarkdownSourceSectionIndex parseSectionIndex(BidDocumentLoader.BidDocument bidDocument) {
        // available() == false 表示标书正文是占位文本或空串，不具证据价值（见 BidDocumentLoader）
        if (bidDocument == null || !bidDocument.available()) {
            return null;
        }
        try {
            return MarkdownSourceSectionIndex.parse(bidDocument.markdown());
        } catch (RuntimeException e) {
            log.warn("Markdown 章节索引解析失败，该项目来源无法查证: {}", e.getMessage());
            return null;
        }
    }

    private Map<Long, List<Candidate>> groupByProject(List<Candidate> candidates) {
        Map<Long, List<Candidate>> grouped = new LinkedHashMap<>();
        for (Candidate kase : candidates) {
            if (kase.sourceProjectId() == null) {
                continue;
            }
            grouped.computeIfAbsent(kase.sourceProjectId(), key -> new ArrayList<>()).add(kase);
        }
        return grouped;
    }

    private String projectNameOf(Long projectId) {
        return projectRepository.findById(projectId)
                .map(Project::getName)
                .orElse("项目-" + projectId);
    }
}
