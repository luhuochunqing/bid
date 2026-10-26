// Input: 物理分页文档及页面图像
// Output: 相关扫描页的文字转写，失败为空，不做业务裁决
// Pos: compliance/domain/policy/ - 扫描页文字视觉端口
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import java.util.List;

@FunctionalInterface
public interface DocumentPageVisionDetector {
    List<ScanPageTextPolicy.Transcription> transcribe(DocumentContent document);
}
