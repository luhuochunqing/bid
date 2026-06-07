// Input: 蓝图 4.1.1.2.1 AI 案例沉淀 — 把后端 missingItems 翻译为面向用户的中文场景化提示
// Output: 纯函数 readinessToTooltip(missingItems) -> string（HTML 多行，<br/> 换行）
// Pos: 视图层 utils，与 ClosureStage.vue 共享；不依赖 Vue / Element Plus / API。
// 维护声明: 一旦 CasePrecipitationAppService#getReadiness 的 missingItems 文案调整，
//          必须同步更新本文件关键词匹配；修改后跑 src/views/Project/stages/ClosureStage.spec.js 验证。
//
// 场景化映射（异常处理兜底）：
// - 标书文件缺失 → "上传标书后即可触发"
// - 评分项为空   → "先在标书编制阶段完成招标文件解析"
// - 项目未结项   → "项目结项后才能触发"
// 其他缺失项：原样回显，保持信息不丢失。

/**
 * @param {string[]|null|undefined} missingItems 后端 missingItems 数组
 * @returns {string} 多行 HTML 字符串（用 <br/> 换行）；空数组返回空串
 */
export function readinessToTooltip(missingItems) {
  if (!Array.isArray(missingItems) || missingItems.length === 0) return ''
  const lines = ['请补齐以下前置条件：']
  for (const m of missingItems) {
    if (/缺少标书文件|上传标书文件|BID.*文件|文件.*BID/.test(m)) {
      lines.push('• 上传标书后即可触发')
    } else if (/缺少评分项|缺少打分点|招标文件解析/.test(m)) {
      lines.push('• 先在标书编制阶段完成招标文件解析')
    } else if (/项目阶段未进入|结项|CLOSED/.test(m)) {
      lines.push('• 项目结项后才能触发')
    } else {
      lines.push('• ' + m)
    }
  }
  return lines.join('<br/>')
}
