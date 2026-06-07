<template>
  <div class="alert-rules-container">
    <div class="page-header">
      <h2>告警规则</h2>
      <el-button type="primary" @click="showDialog('create')">新建规则</el-button>
    </div>

    <el-table :data="rules" v-loading="loading" stripe>
      <el-table-column prop="name" label="规则名称" />
      <el-table-column prop="type" label="类型" width="120">
        <template #default="{ row }">
          <el-tag>{{ getTypeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="condition" label="条件" width="120">
        <template #default="{ row }">
          <span>{{ getConditionLabel(row.condition) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="threshold" label="阈值" width="120" />
      <el-table-column prop="enabled" label="状态" width="80">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" @change="toggleRule(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDialog('edit', row)">编辑</el-button>
          <el-button link type="danger" @click="deleteRule(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="规则名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option label="截止日期" value="DEADLINE" />
            <el-option label="预算" value="BUDGET" />
            <el-option label="风险" value="RISK" />
            <el-option label="文档" value="DOCUMENT" />
            <el-option label="资质到期" value="QUALIFICATION_EXPIRY" />
            <el-option label="保证金退还" value="DEPOSIT_RETURN" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件">
          <el-select v-model="form.condition" :disabled="form.type === 'DEPOSIT_RETURN'">
            <el-option label="大于" value="GREATER_THAN" />
            <el-option label="小于等于/提前提醒" value="LESS_THAN" />
            <el-option label="等于" value="EQUALS" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值">
          <el-input-number v-model="form.threshold" :min="1" :max="365" :disabled="form.type === 'DEPOSIT_RETURN'" />
        </el-form-item>
        <el-alert
          v-if="form.type === 'DEPOSIT_RETURN'"
          type="info"
          :closable="false"
          title="保证金退还提醒阈值由系统设置中的“保证金提醒天数”驱动，此处会自动同步。"
        />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { alertRulesApi } from '@/api/modules/alerts.js'
import { useUserStore } from '@/stores/user'

const loading = ref(false)
const rules = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新建规则')
const form = reactive({ id: null, name: '', type: 'DEADLINE', condition: 'LESS_THAN', threshold: 1 })
const isEdit = ref(false)
const userStore = useUserStore()

onMounted(() => { loadRules() })

async function loadRules() {
  loading.value = true
  try {
    const res = await alertRulesApi.getList()
    rules.value = res.data || []
  } catch (e) {
    ElMessage.error('加载告警规则失败')
  } finally {
    loading.value = false
  }
}

function showDialog(type, row = null) {
  if (type === 'create') {
    dialogTitle.value = '新建规则'
    isEdit.value = false
    Object.assign(form, { id: null, name: '', type: 'DEADLINE', condition: 'LESS_THAN', threshold: 1, enabled: true })
  } else {
    dialogTitle.value = '编辑规则'
    isEdit.value = true
    Object.assign(form, { ...row, threshold: Number(row?.threshold || 1) })
  }
  dialogVisible.value = true
}

async function saveRule() {
  try {
    if (isEdit.value) {
      await alertRulesApi.update(form.id, {
        name: form.name,
        type: form.type,
        condition: form.type === 'DEPOSIT_RETURN' ? 'LESS_THAN' : form.condition,
        threshold: Number(form.threshold || 1),
        enabled: form.enabled ?? true
      })
      ElMessage.success('更新成功')
    } else {
      await alertRulesApi.create({
        name: form.name,
        type: form.type,
        condition: form.type === 'DEPOSIT_RETURN' ? 'LESS_THAN' : form.condition,
        threshold: Number(form.threshold || 1),
        enabled: true,
        createdBy: userStore.userName || 'system'
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadRules()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function toggleRule(row) {
  try {
    await alertRulesApi.toggle(row.id)
    ElMessage.success('状态已更新')
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error('更新失败')
  }
}

async function deleteRule(row) {
  try {
    await ElMessageBox.confirm('确认删除该规则?', '警告', { type: 'warning' })
    await alertRulesApi.delete(row.id)
    ElMessage.success('删除成功')
    loadRules()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function getTypeLabel(type) {
  const map = {
    DEADLINE: '截止日期',
    BUDGET: '预算',
    RISK: '风险',
    DOCUMENT: '文档',
    QUALIFICATION_EXPIRY: '资质到期',
    DEPOSIT_RETURN: '保证金退还'
  }
  return map[type] || type
}

function getConditionLabel(condition) {
  const map = {
    GREATER_THAN: '大于',
    LESS_THAN: '小于等于',
    EQUALS: '等于'
  }
  return map[condition] || condition
}
</script>

<style scoped>
.alert-rules-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
</style>
