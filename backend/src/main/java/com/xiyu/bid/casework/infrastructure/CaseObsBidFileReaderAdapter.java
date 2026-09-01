// Input: KnowledgeCaseRepository（JPA 仓储）
// Output: 案例 OBS 原版标书对象键引用
// Pos: casework 模块基础设施适配层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.casework.domain.model.CaseObsBidFile;
import com.xiyu.bid.casework.domain.gateway.CaseObsBidFileReader;
import org.springframework.stereotype.Component;

/**
 * {@link CaseObsBidFileReader} 的基础设施适配器：桥接 {@link KnowledgeCaseRepository}。
 */
@Component
public class CaseObsBidFileReaderAdapter implements CaseObsBidFileReader {

    private final KnowledgeCaseRepository knowledgeCaseRepository;

    public CaseObsBidFileReaderAdapter(KnowledgeCaseRepository knowledgeCaseRepository) {
        this.knowledgeCaseRepository = knowledgeCaseRepository;
    }

    @Override
    public CaseObsBidFile findByCaseId(Long caseId) {
        return knowledgeCaseRepository.findById(caseId)
                .map(c -> new CaseObsBidFile(true, c.getObsBidFileKey()))
                .orElse(new CaseObsBidFile(false, null));
    }
}
