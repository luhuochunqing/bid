package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.BidCaseSliceImportCommand;
import com.xiyu.bid.casework.domain.model.BidCaseSliceAdminStat;
import com.xiyu.bid.casework.domain.model.BidCaseSliceAdminView;
import com.xiyu.bid.casework.infrastructure.BidCaseSlice;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 案例切片管理端应用服务（命令编排层 / 副作用层）。
 *
 * <p>负责管理端切片的单条导入、统计、删除等操作，将原本散落在 controller 中的
 * 基础设施访问下沉到 application 层，使 controller 保持单薄。</p>
 */
@Service
@RequiredArgsConstructor
public class BidCaseSliceAdminAppService {

    private final BidCaseSliceRepository sliceRepository;

    /**
     * 单条切片导入。
     *
     * @param command 强类型导入命令
     * @return 持久化后的切片视图
     */
    @Transactional
    public BidCaseSliceAdminView importSingleSlice(BidCaseSliceImportCommand command) {
        if (command.project() == null || command.project().isBlank()) {
            throw new IllegalArgumentException("project 不能为空");
        }
        if (command.title() == null || command.title().isBlank()) {
            throw new IllegalArgumentException("title 不能为空");
        }

        BidCaseSlice slice = new BidCaseSlice();
        slice.setProjectDir(command.project());
        slice.setProjectIdx(command.projectIdx() != null ? command.projectIdx() : 0);
        slice.setDocxFile(command.docxFile() != null ? command.docxFile() : "");
        slice.setDocxLabel(command.docxLabel() != null ? command.docxLabel() : "其他");
        slice.setSectionIdx(command.sectionIdx() != null ? command.sectionIdx() : 0);
        slice.setLevel(command.level() != null ? command.level() : 1);
        slice.setTitle(command.title());
        slice.setTextPreview(command.textPreview() != null ? command.textPreview() : "");
        slice.setTextLength(command.textLength() != null ? command.textLength() : 0);
        slice.setParaCount(command.paraCount() != null ? command.paraCount() : 0);

        BidCaseSlice saved = sliceRepository.save(slice);
        return toView(saved);
    }

    /**
     * 管理端切片统计。
     *
     * @return 切片统计快照
     */
    @Transactional(readOnly = true)
    public BidCaseSliceAdminStat getStats() {
        long total = sliceRepository.count();
        long withEmbedding = sliceRepository.countByEmbeddingIsNotNull();
        long withoutEmbedding = total - withEmbedding;
        return new BidCaseSliceAdminStat(total, withEmbedding, withoutEmbedding);
    }

    /**
     * 删除指定切片。
     *
     * @param id 切片 ID
     */
    @Transactional
    public void deleteSlice(Long id) {
        if (!sliceRepository.existsById(id)) {
            throw new IllegalArgumentException("切片不存在: " + id);
        }
        sliceRepository.deleteById(id);
    }

    private static BidCaseSliceAdminView toView(BidCaseSlice slice) {
        return new BidCaseSliceAdminView(
                slice.getId(),
                slice.getProjectDir(),
                slice.getDocxFile(),
                slice.getDocxLabel(),
                slice.getSectionIdx(),
                slice.getLevel(),
                slice.getTitle(),
                slice.getTextPreview(),
                slice.getTextLength(),
                slice.getParaCount(),
                slice.getCreatedAt()
        );
    }
}
