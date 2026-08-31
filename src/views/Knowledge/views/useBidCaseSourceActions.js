/**
 * 案例详情"查看源项目 / 标书原文"操作（CaseGrid 与 AiRecommendDrawer 共用）
 *
 * CO 修复（2026-08-31）：
 * 1. 源项目跳转路由 /bidding/detail/{id} 不存在 → 改为投标项目详情 /project/{id}
 * 2. 标书原文下载前预检档案附件：历史项目档案无附件（原文未归档）时明确提示，不再下载空包
 * 3. blob 下载用 res.data 构造文件，并识别错误体（application/json）——修复假 ZIP 落盘
 */
import { ElMessage } from 'element-plus'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { isJsonErrorBlob, readJsonErrorMessage, triggerBlobDownload } from '@/utils/download'

export function useBidCaseSourceActions() {
  const goToSourceProject = (d) => {
    if (d?.sourceProjectId) {
      window.open(`/project/${d.sourceProjectId}`, '_blank')
    } else {
      ElMessage.warning('来源项目不可用')
    }
  }

  const openBidDocument = async (d) => {
    if (!d?.sourceProjectId) { ElMessage.warning('标书原文暂不可用'); return }
    try {
      // 预检：源项目档案无附件文件时，标书原文未归档，提示并中止（避免下到只含台账的空包）
      const files = await projectLifecycleApi.getArchiveFiles(d.sourceProjectId, 1)
      const fileTotal = files?.data?.totalElements ?? 0
      if (!fileTotal) {
        ElMessage.warning('标书原文未归档：该项目暂无档案附件')
        return
      }
      const res = await projectLifecycleApi.exportProjectArchive(d.sourceProjectId)
      const blob = res?.data
      if (isJsonErrorBlob(blob)) {
        ElMessage.error('标书原文下载失败：' + await readJsonErrorMessage(blob))
        return
      }
      triggerBlobDownload(new Blob([blob], { type: 'application/zip' }), `标书原文-${d.sourceProjectId}.zip`)
      ElMessage.success('标书原文下载成功')
    } catch (e) {
      // 429 由全局拦截器统一提示，业务 catch 不重复弹窗（门禁 429 覆盖规则）
      notifyErrorUnlessRateLimit(e, '标书原文下载失败')
    }
  }

  return { goToSourceProject, openBidDocument }
}
