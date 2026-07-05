// Input: BrandAuth.vue 导出相关逻辑（Excel + ZIP）
// Output: composable 复用导出状态与方法
// Pos: src/composables/ — BrandAuth 导出逻辑抽离
import { ref, computed } from 'vue'
import { ElMessage, ElLoading } from 'element-plus'
import http from '@/api/client'
import brandAuthApi from '@/api/modules/brandAuth.js'

/**
 * 品牌授权导出 composable。
 * 提取自 BrandAuth.vue，负责 Excel 导出和 ZIP 导出的状态与方法。
 *
 * @param {import('vue').Reactive} filters 筛选条件 reactive 对象
 * @param {import('vue').Ref<string>} activeTab 当前 tab（'manufacturer' | 'agent'）
 * @param {import('vue').Ref<number>} total 当前总条数
 */
export function useBrandAuthExport(filters, activeTab, total) {
  const exportVisible = ref(false)
  const exportZipDialogVisible = ref(false)

  const filterSummary = computed(() => {
    const p = []
    if (filters.productLines?.length) p.push('产线:' + filters.productLines.join(','))
    if (filters.brandId) p.push('品牌ID:' + filters.brandId)
    if (filters.brandName) p.push('品牌:' + filters.brandName)
    if (filters.importDomestic) p.push('进口/国产:' + filters.importDomestic)
    if (filters.manufacturerName) p.push('原厂:' + filters.manufacturerName)
    if (filters.agentName) p.push('代理商:' + filters.agentName)
    if (filters.statuses?.length) p.push('状态:' + filters.statuses.join(','))
    if (filters.keyword) p.push('关键词:' + filters.keyword)
    return p.length ? p.join('；') : '全部'
  })

  const exportFilename = computed(() => {
    const d = new Date(); const ts = d.toISOString().slice(0, 16)
      .replace('T', '_').replace(/:/g, '')
    return (activeTab.value === 'agent' ? '代理商授权清单' : '原厂授权清单') + '_' + ts + '.xlsx'
  })

  const handleExport = (command) => {
    if (command === 'zip') {
      exportZipDialogVisible.value = true
      return
    }
    exportVisible.value = true
  }

  const doExport = async () => {
    if (total.value > 500) { ElMessage.warning('单次最多导出500条'); return }
    try {
      const p = new URLSearchParams()
      if (filters.productLines?.length) filters.productLines.forEach(v => p.append('productLines', v))
      if (filters.brandId) p.append('brandId', filters.brandId)
      if (filters.brandName) p.append('brandName', filters.brandName)
      if (filters.importDomestic) p.append('importDomestic', filters.importDomestic)
      if (filters.manufacturerName) p.append('manufacturerName', filters.manufacturerName)
      if (filters.statuses?.length) filters.statuses.forEach(v => p.append('statuses', v))
      if (filters.keyword) p.append('keyword', filters.keyword)
      p.append('authorizationType', activeTab.value === 'agent' ? 'AGENT' : 'MANUFACTURER')
      const resp = await http.get('/api/knowledge/brand-auth/export?' + p.toString(), { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([resp.data]))
      const a = document.createElement('a')
      a.href = url
      a.download = exportFilename.value
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch { ElMessage.error('导出失败') }
    finally { exportVisible.value = false }
  }

  const handleExportZipConfirm = async (checkedTypes) => {
    exportZipDialogVisible.value = false
    const loading = ElLoading.service({ lock: true, text: '正在打包，请稍候...', background: 'rgba(0, 0, 0, 0.7)' })
    try {
      const params = {
        ...filters,
        authorizationType: activeTab.value === 'agent' ? 'AGENT' : 'MANUFACTURER',
        attachmentTypes: checkedTypes
      }
      const resp = await brandAuthApi.exportZip(params)
      const url = window.URL.createObjectURL(new Blob([resp.data]))
      const a = document.createElement('a')
      a.href = url
      const ts = new Date().toISOString().slice(0, 16).replace('T', '_').replace(/:/g, '')
      a.download = (activeTab.value === 'agent' ? '代理商授权导出' : '原厂授权导出') + '_' + ts + '.zip'
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
      ElMessage.success('ZIP 导出成功')
    } catch (err) {
      ElMessage.error('导出失败: ' + (err?.message || '未知错误'))
    } finally {
      loading.close()
    }
  }

  return {
    exportVisible,
    exportZipDialogVisible,
    filterSummary,
    exportFilename,
    handleExport,
    doExport,
    handleExportZipConfirm
  }
}
