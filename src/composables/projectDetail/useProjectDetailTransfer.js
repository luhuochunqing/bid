// Input: userStore, projectStore, projectsApi, project, message
// Output: 转移 dialog 状态 + openTransfer/handleTransferConfirm 方法 + canTransfer 权限
// Pos: src/composables/projectDetail/ - 项目详情专用 composable
// 维护声明: 仅维护项目转移 dialog 状态与提交；权限对齐后端 @PreAuthorize
// （hasAnyAuthority bidding.manage / ROLE_ADMIN / ROLE_BIDADMIN；前端 isBidAdmin 含 bid-SystemAdmin）。
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * 项目转移 composable。
 * <p>对齐 useBiddingDetailPage.js 的 transfer 模式（dialog + transferring flag）。
 * 权限对齐后端 transfer 接口与 userStore.isBidAdmin：
 * admin（本地）、/bidAdmin、bid-SystemAdmin（OSS 独立角色，不再映射为 admin）可操作；
 * 投标组长（bid-TeamLeader）不可操作。
 * </p>
 *
 * @param {{ userStore: object, projectStore: object, projectsApi: object, project: import('vue').Ref<object>, message: object }} context
 */
export function useProjectDetailTransfer(context) {
  const { userStore, projectStore, projectsApi, project, message } = context

  const transferDialogVisible = ref(false)
  const transferring = ref(false)
  const transferForm = reactive({
    newOwnerUserId: null,
    reason: '',
  })

  /**
   * 是否可执行项目转移：投标管理级角色可见按钮。
   * 对齐 userStore.isBidAdmin（admin / /bidAdmin / bid-SystemAdmin，不含 bid-TeamLeader）。
   */
  const canTransfer = computed(() => Boolean(userStore?.isBidAdmin && project.value?.id))

  /**
   * 排除当前负责人，避免"转给自己"。
   * 同时排除 project.manager_id 与 project.projectLeaderId（对应 initiationDetails.owner_user_id），
   * 因为两者可能不一致；只排除 managerId 会导致仍能选到实际负责人，产生无效转移。
   */
  const excludeOwnerIds = computed(() => {
    const ids = []
    if (project.value?.managerId) ids.push(project.value.managerId)
    if (project.value?.projectLeaderId) ids.push(project.value.projectLeaderId)
    return [...new Set(ids)]
  })

  const resetForm = () => {
    transferForm.newOwnerUserId = null
    transferForm.reason = ''
  }

  const openTransfer = () => {
    resetForm()
    transferDialogVisible.value = true
  }

  const closeTransfer = () => {
    transferDialogVisible.value = false
    resetForm()
  }

  /**
   * 确认转移。手动校验 + 调用 API + 成功后刷新项目详情。
   */
  const handleTransferConfirm = async () => {
    const projectId = project.value?.id
    if (!projectId) {
      ElMessage.warning('项目信息缺失，无法转移')
      return
    }
    if (!transferForm.newOwnerUserId) {
      ElMessage.warning('请选择新负责人')
      return
    }

    transferring.value = true
    try {
      const res = await projectsApi.transferProject(projectId, {
        newOwnerUserId: transferForm.newOwnerUserId,
        reason: transferForm.reason?.trim() || undefined,
      })
      if (res?.success) {
        ElMessage.success('项目转移成功')
        closeTransfer()
        // 刷新项目详情，让 ProjectBasicInfoCard 的"项目负责人"字段实时更新
        await projectStore.getProjectById(projectId)
      } else {
        throw new Error(res?.message || '项目转移失败')
      }
    } catch (error) {
      console.error('项目转移失败:', error)
      message?.error?.(error?.message || '项目转移失败')
    } finally {
      transferring.value = false
    }
  }

  return {
    transferDialogVisible,
    transferring,
    transferForm,
    canTransfer,
    excludeOwnerIds,
    openTransfer,
    closeTransfer,
    handleTransferConfirm,
  }
}
