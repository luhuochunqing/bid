<template>
  <el-dialog v-model="visible" title="批量导入平台账号" width="500px" destroy-on-close>
    <div style="margin-bottom: 16px">
      <el-button link type="primary" @click="downloadTemplate">下载导入模板</el-button>
    </div>
    <el-upload
      ref="uploadRef"
      drag
      action=""
      :auto-upload="false"
      :limit="1"
      accept=".xlsx,.xls"
      :on-change="(f) => importFile = f.raw"
    >
      <el-icon style="font-size: 48px; color: #909399"><Upload /></el-icon>
      <div>拖拽文件到此处或 <em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">仅支持 .xlsx 文件</div>
      </template>
    </el-upload>
    <div v-if="importResult" style="margin-top: 12px">
      <el-alert :type="importResult.failed > 0 ? 'warning' : 'success'" :closable="false">
        <template #title>
          导入完成：总计 {{ importResult.total }} 条，成功 {{ importResult.success }} 条，失败 {{ importResult.failed }} 条
        </template>
      </el-alert>
      <div v-if="importResult.failureDetails?.length" style="margin-top: 8px; max-height: 200px; overflow-y: auto; font-size: 12px; color: var(--el-color-danger)">
        <div v-for="(msg, i) in importResult.failureDetails" :key="i">{{ msg }}</div>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="importing" :disabled="!importFile" @click="doImport">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { resourcesApi } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'imported'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const importFile = ref(null)
const importing = ref(false)
const importResult = ref(null)
const uploadRef = ref(null)

async function downloadTemplate() {
  try {
    const blob = await resourcesApi.accounts.downloadTemplate()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '平台账号导入模板.xlsx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载模板失败')
  }
}

async function doImport() {
  if (!importFile.value) return
  importing.value = true
  importResult.value = null
  try {
    const res = await resourcesApi.accounts.importFile(importFile.value)
    importResult.value = res?.data || res
    if (importResult.value.success > 0) {
      emit('imported')
    }
  } catch (e) {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}
</script>
