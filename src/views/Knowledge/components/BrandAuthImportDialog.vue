<template>
  <el-dialog v-model="visible" title="批量导入品牌授权" width="600px" @close="handleClose" destroy-on-close>
    <el-steps :active="step" finish-status="success" simple style="margin-bottom: 20px">
      <el-step title="上传文件" />
      <el-step title="导入结果" />
    </el-steps>

    <!-- Step 1: Upload -->
    <template v-if="step === 0">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
        <template #title>
          <span>请先下载模板填写数据，再上传 Excel 文件批量导入。</span>
        </template>
      </el-alert>

      <div style="text-align: center; margin-bottom: 16px">
        <el-button type="primary" :loading="downloading" @click="handleDownloadTemplate">
          <el-icon><Download /></el-icon> 下载导入模板
        </el-button>
      </div>

      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :file-list="fileList"
        :limit="1"
        :on-change="handleFileChange"
        :on-remove="() => { fileList = []; uploadFile = null }"
        accept=".xlsx,.xls"
        drag
      >
        <el-icon class="el-upload-icon" size="48"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽 Excel 文件到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx / .xls 格式，请使用下载的模板填写</div>
        </template>
      </el-upload>

      <div style="display: flex; justify-content: flex-end; margin-top: 16px">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="!uploadFile" :loading="importing" @click="handleImport">
          开始导入
        </el-button>
      </div>
    </template>

    <!-- Step 2: Result -->
    <template v-if="step === 1">
      <el-result v-if="resultSuccess" icon="success" title="导入完成">
        <template #sub-title>
          <div style="text-align: center">
            <p>共处理 <strong>{{ result?.totalRows }}</strong> 行数据</p>
            <p>成功导入 <strong style="color: var(--el-color-success)">{{ result?.totalSuccess }}</strong> 条</p>
            <p v-if="result?.totalFailed > 0">
              失败 <strong style="color: var(--el-color-danger)">{{ result?.totalFailed }}</strong> 条
            </p>
          </div>
        </template>
      </el-result>

      <el-result v-else icon="error" title="导入失败">
        <template #sub-title>
          <p>文件格式有误或服务器异常，请检查后重试</p>
        </template>
      </el-result>

      <!-- Error details -->
      <template v-if="result && result.totalFailed > 0 && hasErrors">
        <el-divider />
        <h4 style="margin: 0 0 8px">失败详情</h4>
        <el-table :data="allErrors" size="small" max-height="240" stripe>
          <el-table-column prop="sheet" label="工作表" width="100" />
          <el-table-column prop="message" label="错误信息" />
        </el-table>
      </template>

      <div style="display: flex; justify-content: flex-end; margin-top: 16px">
        <el-button @click="handleClose">关闭</el-button>
        <el-button v-if="result?.totalFailed > 0" type="primary" @click="resetStep">
          重新导入
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { brandAuthApi } from '@/api/modules/brandAuth.js'
import http from '@/api/client.js'

const emit = defineEmits(['close', 'success'])

const visible = ref(false)
const step = ref(0)
const fileList = ref([])
const uploadFile = ref(null)
const downloading = ref(false)
const importing = ref(false)
const result = ref(null)
const resultSuccess = ref(false)

const allErrors = computed(() => {
  if (!result.value?.sheets) return []
  const errors = []
  for (const sheet of result.value.sheets) {
    for (const err of sheet.errors || []) {
      errors.push({ sheet: sheet.sheetName, message: err })
    }
  }
  return errors
})

const hasErrors = computed(() => allErrors.value.length > 0)

function open() {
  visible.value = true
  step.value = 0
  fileList.value = []
  uploadFile.value = null
  result.value = null
  resultSuccess.value = false
}

function resetStep() {
  step.value = 0
  fileList.value = []
  uploadFile.value = null
  result.value = null
  resultSuccess.value = false
}

function handleClose() {
  visible.value = false
  emit('close')
  if (resultSuccess.value) {
    emit('success')
  }
}

function handleFileChange(file) {
  uploadFile.value = file.raw
  fileList.value = [file]
}

async function handleDownloadTemplate() {
  downloading.value = true
  try {
    const resp = await brandAuthApi.downloadTemplate()
    const blob = resp.data
    const url = window.URL.createObjectURL(new Blob([blob]))
    const a = document.createElement('a')
    a.href = url
    a.download = '品牌授权导入模板.xlsx'
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
    ElMessage.success('模板下载成功')
  } catch {
    ElMessage.error('模板下载失败')
  } finally {
    downloading.value = false
  }
}

async function handleImport() {
  if (!uploadFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importing.value = true
  try {
    const res = await brandAuthApi.importExcel(uploadFile.value)
    result.value = res.data || res
    resultSuccess.value = true
    step.value = 1
    if (result.value?.totalSuccess > 0) {
      ElMessage.success(`成功导入 ${result.value.totalSuccess} 条记录`)
    }
    if (result.value?.totalFailed > 0) {
      ElMessage.warning(`${result.value.totalFailed} 条导入失败，请查看详情`)
    }
  } catch (e) {
    resultSuccess.value = false
    step.value = 1
    ElMessage.error(e.response?.data?.message || e.message || '导入失败')
  } finally {
    importing.value = false
  }
}

defineExpose({ open })
</script>

<style scoped lang="scss">
.el-upload-icon {
  color: var(--el-color-primary);
  margin-bottom: 8px;
}
</style>
