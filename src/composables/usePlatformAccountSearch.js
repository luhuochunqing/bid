// Input: free-text query string from the CA form dialog
// Output: a reactive list of bidding platform account options for el-select
// Pos: src/composables/ - shared composables
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref } from 'vue'
import { resourcesApi } from '@/api'

/**
 * Composable used by the CA form dialog to power a remote-search
 * el-select that picks from existing 投标平台账号 (platform accounts).
 */
export function usePlatformAccountSearch(pageSize = 50) {
  const platformOptions = ref([])
  const platformOptionsLoading = ref(false)

  async function searchPlatforms(query) {
    platformOptionsLoading.value = true
    try {
      const res = await resourcesApi.accounts.getList({
        keyword: query || '',
        pageSize
      })
      const list = Array.isArray(res?.data) ? res.data : []
      platformOptions.value = list.map(p => ({
        id: p.id,
        accountName: p.accountName || p.platform
      }))
    } catch {
      platformOptions.value = []
    } finally {
      platformOptionsLoading.value = false
    }
  }

  return {
    platformOptions,
    platformOptionsLoading,
    searchPlatforms
  }
}
