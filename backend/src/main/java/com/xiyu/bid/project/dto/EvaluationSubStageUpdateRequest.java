// Input: PATCH 评标子状态请求体
// Output: 校验过的目标 EvaluationSubStage
// Pos: project/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import com.xiyu.bid.project.core.EvaluationSubStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationSubStageUpdateRequest {

    @NotNull(message = "targetSubStage 不能为空")
    private EvaluationSubStage targetSubStage;

    @NotBlank(message = "情况说明不能为空")
    private String notes;
}
