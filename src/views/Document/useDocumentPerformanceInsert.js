import { ElMessage } from 'element-plus'

export function useDocumentPerformanceInsert({ sectionData, currentSection }) {
  function checkPendingInsert() {
    try {
      const raw = sessionStorage.getItem('pendingPerformanceInsert')
      if (!raw) return
      const data = JSON.parse(raw)
      if (!data.timestamp || Date.now() - data.timestamp > 5 * 60 * 1000) {
        sessionStorage.removeItem('pendingPerformanceInsert')
        return
      }
      const sections = sectionData.value?.sections || []
      let target = null
      function find(list) {
        for (const s of list || []) {
          if (s.type === 'section' && (/案例|业绩|experience|performance/i.test(s.name || ''))) {
            return s
          }
          if (s.children) {
            const found = find(s.children)
            if (found) return found
          }
        }
        return null
      }
      target = find(sections)
      if (target) {
        currentSection.value = target
        const content = `\n\n## 业绩信息\n\n- 合同名称：${data.contractName || ''}\n- 签约单位：${data.signingEntity || ''}\n- 剩余有效期：${data.remainingDays != null ? data.remainingDays + ' 天' : ''}\n${data.reason ? '- 备注：' + data.reason : ''}\n\n${data.fullText || ''}\n`
        target.content = (target.content || '') + content
        ElMessage.success('业绩信息已自动插入当前章节')
      } else {
        ElMessage.info('未找到案例/业绩章节，请手动选择章节后粘贴')
      }
      sessionStorage.removeItem('pendingPerformanceInsert')
    } catch {
      sessionStorage.removeItem('pendingPerformanceInsert')
    }
  }

  return { checkPendingInsert }
}
