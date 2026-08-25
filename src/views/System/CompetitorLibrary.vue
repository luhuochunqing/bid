<template>
  <div class="competitor-library-container">
    <div class="page-header">
      <h2>竞争对手库</h2>
      <span class="page-tip">治理竞品字典：改名、合并相似项、停用错误词条，保持录入与报表规范</span>
    </div>

    <el-table :data="competitors" v-loading="loading" stripe>
      <el-table-column prop="name" label="竞品名称" min-width="240" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
            {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.status === 'DISABLED'" @click="showRename(row)">改名</el-button>
          <el-button link type="warning" :disabled="row.status === 'DISABLED'" @click="showMerge(row)">合并</el-button>
          <el-button link type="danger" :disabled="row.status === 'DISABLED'" @click="disable(row)">停用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="renameVisible" title="修改竞品名称" width="420px">
      <el-input v-model="renameName" placeholder="输入新名称" maxlength="200" />
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" @click="doRename">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mergeVisible" title="合并竞品" width="460px">
      <p class="merge-tip">将「{{ mergeSource?.name }}」合并到目标竞品；合并后源竞品停用，其竞争分析关联迁移到目标竞品，历史项目结果快照不变。</p>
      <el-select v-model="mergeTargetId" placeholder="选择目标竞品" filterable style="width: 100%">
        <el-option v-for="c in mergeCandidates" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <template #footer>
        <el-button @click="mergeVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!mergeTargetId" @click="doMerge">合并</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { competitionIntelApi } from '@/api/modules/competitionIntel.js'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'

const loading = ref(false)
const competitors = ref([])

const renameVisible = ref(false)
const renameTarget = ref(null)
const renameName = ref('')

const mergeVisible = ref(false)
const mergeSource = ref(null)
const mergeTargetId = ref(null)

const mergeCandidates = computed(() =>
  competitors.value.filter(c => c.id !== mergeSource.value?.id && c.status === 'ACTIVE'))

onMounted(loadCompetitors)

async function loadCompetitors() {
  loading.value = true
  try {
    const res = await competitionIntelApi.getCompetitors({ includeDisabled: true })
    competitors.value = res?.data || []
  } catch (e) {
    notifyErrorUnlessRateLimit(e, '加载竞品库失败')
  } finally {
    loading.value = false
  }
}

function showRename(row) {
  renameTarget.value = row
  renameName.value = row.name
  renameVisible.value = true
}

async function doRename() {
  const name = renameName.value?.trim()
  if (!name) return ElMessage.warning('请输入新名称')
  try {
    await competitionIntelApi.renameCompetitor(renameTarget.value.id, name)
    ElMessage.success('改名成功')
    renameVisible.value = false
    loadCompetitors()
  } catch (e) {
    notifyErrorUnlessRateLimit(e, '改名失败')
  }
}

function showMerge(row) {
  mergeSource.value = row
  mergeTargetId.value = null
  mergeVisible.value = true
}

async function doMerge() {
  if (!mergeTargetId.value) return ElMessage.warning('请选择目标竞品')
  try {
    await competitionIntelApi.mergeCompetitor(mergeSource.value.id, mergeTargetId.value)
    ElMessage.success('合并成功')
    mergeVisible.value = false
    loadCompetitors()
  } catch (e) {
    notifyErrorUnlessRateLimit(e, '合并失败')
  }
}

async function disable(row) {
  try {
    await ElMessageBox.confirm(`确认停用「${row.name}」？停用后录入下拉不再出现该竞品。`, '停用竞品', { type: 'warning' })
    await competitionIntelApi.disableCompetitor(row.id)
    ElMessage.success('已停用')
    loadCompetitors()
  } catch (e) {
    if (e !== 'cancel') notifyErrorUnlessRateLimit(e, '停用失败')
  }
}
</script>

<style scoped>
.competitor-library-container { padding: 20px; }
.page-header { display: flex; align-items: baseline; gap: 16px; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
.page-tip { color: var(--el-text-color-secondary); font-size: 13px; }
.merge-tip { color: var(--el-text-color-regular); font-size: 13px; line-height: 1.6; margin-bottom: 12px; }
</style>
