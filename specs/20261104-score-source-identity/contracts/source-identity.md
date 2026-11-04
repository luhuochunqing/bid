# Internal contract

`sameUnit(first, candidate)` 只能在同方案、同物理来源、同原文规则范围且不是父子单元时返回true。空引用、不同已定位子规则或不同文档返回false。权重冲突不得被当作重复丢弃。

重试只有选定target且验证通过才能替换；同源别名不会追加重复分数。引用/关联不得因选择更高confidence丢失。

OCR失败保留baseline表、meta、textBlocks、documents和schemaVersion，添加稳定页级诊断；零成功页同样返回诊断artifact。诊断进入现有人工复核策略。禁用OCR返回原baseline不加错误。
