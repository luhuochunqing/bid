<template>
  <div class="bar-certificates-container">
    <div class="page-header">
      <h2>证书管理</h2>
      <el-button type="primary" @click="showDialog('create')">添加证书</el-button>
    </div>

    <el-table :data="certificates" v-loading="loading" stripe>
      <el-table-column prop="certificateType" label="证书类型" width="120" />
      <el-table-column prop="certificateNumber" label="证书编号" width="180" />
      <el-table-column prop="issuingAuthority" label="发证机构" />
      <el-table-column prop="issueDate" label="发证日期" width="120" />
      <el-table-column prop="expiryDate" label="到期日期" width="120">
        <template #default="{ row }">
          <span :class="isExpiring(row.expiryDate) ? 'expiring' : ''">{{ row.expiryDate }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="certStatusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDialog('edit', row)">编辑</el-button>
          <el-button link type="danger" @click="deleteCert(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="关联资产">
          <el-select v-model="form.assetId" placeholder="选择资产">
            <el-option v-for="a in assets" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="证书类型">
          <el-select v-model="form.certificateType">
            <el-option label="营业执照" value="LICENSE" />
            <el-option label="资质证书" value="QUALIFICATION" />
            <el-option label="许可证" value="PERMIT" />
            <el-option label="认证" value="CERTIFICATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="证书编号"><el-input v-model="form.certificateNumber" /></el-form-item>
        <el-form-item label="发证机构"><el-input v-model="form.issuingAuthority" /></el-form-item>
        <el-form-item label="发证日期">
          <el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="到期日期">
          <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="有效" value="VALID" />
            <el-option label="过期" value="EXPIRED" />
            <el-option label="撤销" value="REVOKED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCert">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { barAssetsApi } from '@/api/modules/bar.js'

const loading = ref(false)
const certificates = ref([])
const assets = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加证书')
const form = reactive({ id: null, assetId: null, certificateType: 'LICENSE', certificateNumber: '', issuingAuthority: '', issueDate: '', expiryDate: '', status: 'VALID' })
const isEdit = ref(false)

onMounted(() => { loadData() })

async function loadData() {
  loading.value = true
  try {
    const [certRes, assetRes] = await Promise.all([
      barAssetsApi.getCertificates(),
      barAssetsApi.getAssets(),
    ])
    certificates.value = certRes.data || []
    assets.value = assetRes.data || []
  } catch (e) {
    ElMessage.error('加载证书失败')
  } finally {
    loading.value = false
  }
}

function showDialog(type, row = null) {
  if (type === 'create') {
    dialogTitle.value = '添加证书'
    isEdit.value = false
    Object.assign(form, { id: null, assetId: null, certificateType: 'LICENSE', certificateNumber: '', issuingAuthority: '', issueDate: '', expiryDate: '', status: 'VALID' })
  } else {
    dialogTitle.value = '编辑证书'
    isEdit.value = true
    Object.assign(form, { ...row })
  }
  dialogVisible.value = true
}

async function saveCert() {
  try {
    if (isEdit.value) {
      await barAssetsApi.updateCertificate(form.id, form)
    } else {
      await barAssetsApi.createCertificate(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function deleteCert(row) {
  try {
    await ElMessageBox.confirm('确认删除?', '警告', { type: 'warning' })
    await barAssetsApi.deleteCertificate(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function certStatusType(s) {
  const map = { VALID: 'success', EXPIRED: 'danger', REVOKED: 'info' }
  return map[s] || 'info'
}

function isExpiring(date) {
  if (!date) return false
  const days = (new Date(date) - new Date()) / (1000 * 60 * 60 * 24)
  return days > 0 && days <= 30
}
</script>

<style scoped>
.bar-certificates-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
.expiring { color: #e6a23c; font-weight: bold; }
</style>
