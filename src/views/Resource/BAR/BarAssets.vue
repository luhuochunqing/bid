<template>
  <div class="bar-assets-container">
    <div class="page-header">
      <h2>资产台账</h2>
      <el-button type="primary" @click="showDialog('create')">添加资产</el-button>
    </div>

    <el-table :data="assets" v-loading="loading" stripe>
      <el-table-column prop="name" label="资产名称" />
      <el-table-column prop="category" label="类别" width="120" />
      <el-table-column prop="serialNumber" label="序列号" width="150" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="owner" label="负责人" width="100" />
      <el-table-column prop="location" label="位置" width="120" />
      <el-table-column prop="value" label="价值(万)" width="100" align="right" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDialog('edit', row)">编辑</el-button>
          <el-button link type="danger" @click="deleteAsset(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category">
            <el-option label="设备" value="EQUIPMENT" />
            <el-option label="车辆" value="VEHICLE" />
            <el-option label="办公用品" value="OFFICE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="序列号"><el-input v-model="form.serialNumber" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="在用" value="ACTIVE" />
            <el-option label="闲置" value="IDLE" />
            <el-option label="报废" value="SCRAPPED" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.owner" /></el-form-item>
        <el-form-item label="位置"><el-input v-model="form.location" /></el-form-item>
        <el-form-item label="价值(万)"><el-input-number v-model="form.value" :min="0" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAsset">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { barAssetsApi } from '@/api/modules/bar.js'

const loading = ref(false)
const assets = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加资产')
const form = reactive({ id: null, name: '', category: 'OTHER', serialNumber: '', status: 'ACTIVE', owner: '', location: '', value: 0, description: '' })
const isEdit = ref(false)

onMounted(() => { loadAssets() })

async function loadAssets() {
  loading.value = true
  try {
    const res = await barAssetsApi.getAssets()
    assets.value = res.data || []
  } catch (e) {
    ElMessage.error('加载资产失败')
  } finally {
    loading.value = false
  }
}

function showDialog(type, row = null) {
  if (type === 'create') {
    dialogTitle.value = '添加资产'
    isEdit.value = false
    Object.assign(form, { id: null, name: '', category: 'OTHER', serialNumber: '', status: 'ACTIVE', owner: '', location: '', value: 0, description: '' })
  } else {
    dialogTitle.value = '编辑资产'
    isEdit.value = true
    Object.assign(form, { ...row })
  }
  dialogVisible.value = true
}

async function saveAsset() {
  try {
    if (isEdit.value) {
      await barAssetsApi.updateAsset(form.id, form)
    } else {
      await barAssetsApi.createAsset(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadAssets()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function deleteAsset(row) {
  try {
    await ElMessageBox.confirm('确认删除?', '警告', { type: 'warning' })
    await barAssetsApi.deleteAsset(row.id)
    ElMessage.success('删除成功')
    loadAssets()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function statusType(s) {
  const map = { ACTIVE: 'success', IDLE: 'warning', SCRAPPED: 'info' }
  return map[s] || 'info'
}
</script>

<style scoped>
.bar-assets-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
</style>
