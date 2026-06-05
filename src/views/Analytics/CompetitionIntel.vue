<template>
  <div class="competition-intel-container">
    <div class="page-header">
      <h2>竞争情报</h2>
      <el-button type="primary" @click="showDialog('create')">添加竞争对手</el-button>
    </div>

    <el-table :data="competitors" v-loading="loading" stripe>
      <el-table-column prop="name" label="竞争对手" />
      <el-table-column prop="industry" label="行业" width="120" />
      <el-table-column prop="region" label="区域" width="100" />
      <el-table-column prop="bidCount" label="投标次数" width="100" align="center" />
      <el-table-column prop="winCount" label="中标次数" width="100" align="center" />
      <el-table-column prop="winRate" label="中标率" width="100" align="center">
        <template #default="{ row }">{{ row.winRate }}%</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDialog('view', row)">详情</el-button>
          <el-button link type="danger" @click="deleteCompetitor(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="form.name" :disabled="dialogMode === 'view'" />
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="form.industry" :disabled="dialogMode === 'view'" />
        </el-form-item>
        <el-form-item label="区域">
          <el-input v-model="form.region" :disabled="dialogMode === 'view'" />
        </el-form-item>
        <el-form-item label="网址">
          <el-input v-model="form.website" :disabled="dialogMode === 'view'" />
        </el-form-item>
        <el-form-item label="年营收">
          <el-input-number v-model="form.annualRevenue" :disabled="dialogMode === 'view'" />
        </el-form-item>
        <el-form-item label="员工规模">
          <el-input-number v-model="form.employeeCount" :disabled="dialogMode === 'view'" />
        </el-form-item>
        <el-form-item label="优势">
          <el-input v-model="form.strengths" type="textarea" :disabled="dialogMode === 'view'" />
        </el-form-item>
        <el-form-item label="劣势">
          <el-input v-model="form.weaknesses" type="textarea" :disabled="dialogMode === 'view'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="dialogMode !== 'view'" type="primary" @click="saveCompetitor">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { competitionIntelApi } from '@/api/modules/competitionIntel.js'

const loading = ref(false)
const competitors = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('竞争对手详情')
const dialogMode = ref('view')
const form = reactive({ id: null, name: '', industry: '', region: '', website: '', annualRevenue: 0, employeeCount: 0, strengths: '', weaknesses: '' })

onMounted(() => { loadCompetitors() })

async function loadCompetitors() {
  loading.value = true
  try {
    const res = await competitionIntelApi.getCompetitors()
    competitors.value = res.data || []
  } catch (e) {
    ElMessage.error('加载竞争情报失败')
  } finally {
    loading.value = false
  }
}

function showDialog(mode, row = null) {
  dialogMode.value = mode
  dialogTitle.value = mode === 'create' ? '添加竞争对手' : '竞争对手详情'
  if (mode === 'create') {
    Object.assign(form, { id: null, name: '', industry: '', region: '', website: '', annualRevenue: 0, employeeCount: 0, strengths: '', weaknesses: '' })
  } else {
    Object.assign(form, { ...row })
  }
  dialogVisible.value = true
}

async function saveCompetitor() {
  try {
    if (form.id) {
      await competitionIntelApi.updateCompetitor(form.id, form)
    } else {
      await competitionIntelApi.createCompetitor(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadCompetitors()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function deleteCompetitor(row) {
  try {
    await ElMessageBox.confirm('确认删除?', '警告', { type: 'warning' })
    await competitionIntelApi.deleteCompetitor(row.id)
    ElMessage.success('删除成功')
    loadCompetitors()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.competition-intel-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
</style>
