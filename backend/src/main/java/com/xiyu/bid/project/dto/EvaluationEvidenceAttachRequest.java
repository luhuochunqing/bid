// Input: 附加证据请求体（已上传文档 ID 列表）
// Output: 校验后的 fileIds
// Pos: project/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationEvidenceAttachRequest {

    @NotEmpty(message = "fileIds 不能为空")
    private List<Long> fileIds;
}
