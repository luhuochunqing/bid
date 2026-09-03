/**
 * 案例详情"查看源项目 / 标书原文"操作（CaseGrid 与 AiRecommendDrawer 共用）
 *
 * CO 修复（2026-08-31）：
 * 1. 源项目跳转路由 /bidding/detail/{id} 不存在 → 改为投标项目详情 /project/{id}
 * 2. 标书原文下载前预检档案附件：历史项目档案无附件（原文未归档）时明确提示，不再下载空包
 * 3. blob 下载用 res.data 构造文件，并识别错误体（application/json）——修复假 ZIP 落盘
 *
 * 058 分流（2026-09）：obsBidFileKey 非空 → OBS 预签名直链下载原版 docx；
 * 否则有 sourceProjectId → 档案预检 + ZIP；皆无 → 提示"标书原文暂不可用"
 */
import { ElMessage } from 'element-plus'
import { casesApi } from '@/api/modules/knowledge'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle'
import { getDocuments, getDocumentDownloadUrl } from '@/api/modules/projectDocuments'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { isJsonErrorBlob, readJsonErrorMessage, triggerBlobDownload, downloadWithFilename } from '@/utils/download'

export function useBidCaseSourceActions() {
  const goToSourceProject = (d) => {
    if (d?.sourceProjectId) {
      window.open(`/project/${d.sourceProjectId}`, '_blank')
    } else {
      ElMessage.warning('来源项目不可用')
    }
  }

  const openBidDocument = async (d) => {
    // 分支一（058）：历史切片案例有 OBS 原版对象键 → 预签名直链下载原版 docx
    if (d?.obsBidFileKey) {
      try {
        const { url, filename } = await casesApi.getDownloadUrl(d.caseId || d.id)
        if (!url) { ElMessage.error('标书原文下载失败：链接生成异常'); return }
        window.open(url, '_blank')
        ElMessage.success(`标书原文下载已开始：${filename || ''}`)
      } catch (e) {
        notifyErrorUnlessRateLimit(e, '标书原文下载失败')
      }
      return
    }
    // 分支二：系统生成案例（有源项目档案）→ 优先项目档案 ZIP 导出；档案为空时兜底下载项目投标文件
    if (!d?.sourceProjectId) { ElMessage.warning('标书原文暂不可用'); return }
    try {
      // 预检：源项目档案附件
      const files = await projectLifecycleApi.getArchiveFiles(d.sourceProjectId, 1)
      const fileTotal = files?.data?.totalElements ?? 0
      if (fileTotal > 0) {
        const res = await projectLifecycleApi.exportProjectArchive(d.sourceProjectId)
        const blob = res?.data
        if (isJsonErrorBlob(blob)) {
          ElMessage.error('标书原文下载失败：' + await readJsonErrorMessage(blob))
          return
        }
        triggerBlobDownload(new Blob([blob], { type: 'application/zip' }), `标书原文-${d.sourceProjectId}.zip`)
        ElMessage.success('标书原文下载成功')
        return
      }

      // 兜底：档案无附件时，若项目本身有已上传的投标文件（BID），直接下载单标书文件
      try {
        const docResp = await getDocuments(d.sourceProjectId, { documentCategory: 'BID' })
        const bidDocs = Array.isArray(docResp?.data) ? docResp.data : []
        if (bidDocs.length > 0) {
          const primaryDoc = bidDocs[0]
          const downloadUrl = getDocumentDownloadUrl(d.sourceProjectId, primaryDoc.id)
          if (downloadUrl) {
            await downloadWithFilename(downloadUrl, primaryDoc.name || '投标文件')
            ElMessage.success(`标书原文下载成功：${primaryDoc.name || '投标文件'}`)
            return
          }
        }
      } catch {
        // 兜底失败继续走无附件提示
      }

      ElMessage.warning('标书原文未归档：该项目暂无档案附件')
    } catch (e) {
      // 429 由全局拦截器统一提示，业务 catch 不重复弹窗（门禁 429 覆盖规则）
      notifyErrorUnlessRateLimit(e, '标书原文下载失败')
    }
  }

  return { goToSourceProject, openBidDocument }
}
