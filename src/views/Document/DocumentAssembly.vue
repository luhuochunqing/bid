<template>
  <div class="document-assembly-container">
    <div class="page-header">
      <h2>文档组装</h2>
      <el-button type="primary" @click="showDialog('create')">创建模板</el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="模板列表" name="templates">
        <el-table :data="templates" v-loading="loading" stripe>
          <el-table-column prop="name" label="模板名称" />
          <el-table-column prop="category" label="分类" width="120" />
          <el-table-column prop="description" label="描述" />
          <el-table-column label="变量数" width="80" align="center">
            <template #default="{ row }">{{ row.variables?.length || 0 }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="selectTemplate(row)">使用</el-button>
              <el-button link type="primary" @click="showDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="deleteTemplate(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="组装文档" name="assembly" v-if="selectedTemplate">
        <el-card>
          <h3>使用模板: {{ selectedTemplate.name }}</h3>
          <el-form :model="variables" label-width="120px" style="max-width: 600px">
            <el-form-item v-for="v in selectedTemplate.variables" :key="v.name" :label="v.label || v.name">
              <el-input v-model="variables[v.name]" :placeholder="v.placeholder || v.name" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="assembleDocument">生成文档</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card v-if="assembledContent" class="result-card">
          <h3>生成结果</h3>
          <pre class="content-preview">{{ assembledContent }}</pre>
          <el-button type="primary" @click="copyContent">复制内容</el-button>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="模板名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category">
            <el-option label="技术方案" value="TECHNICAL" />
            <el-option label="商务文件" value="COMMERCIAL" />
            <el-option label="资质文件" value="QUALIFICATION" />
            <el-option label="合同" value="CONTRACT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="模板内容">
          <el-input v-model="form.content" type="textarea" :rows="8" placeholder="使用 ${变量名} 表示需要替换的位置" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { documentAssemblyApi } from '@/api/modules/collaboration.js'

const loading = ref(false)
const templates = ref([])
const activeTab = ref('templates')
const selectedTemplate = ref(null)
const assembledContent = ref('')
const variables = reactive({})
const dialogVisible = ref(false)
const dialogTitle = ref('创建模板')
const form = reactive({ id: null, name: '', category: 'OTHER', description: '', content: '', variables: [] })
const isEdit = ref(false)

onMounted(() => { loadTemplates() })

async function loadTemplates() {
  loading.value = true
  try {
    const res = await documentAssemblyApi.getTemplates()
    templates.value = res.data || []
  } catch (e) {
    ElMessage.error('加载模板失败')
  } finally {
    loading.value = false
  }
}

function selectTemplate(row) {
  selectedTemplate.value = row
  Object.keys(variables).forEach(k => delete variables[k])
  row.variables?.forEach(v => { variables[v.name] = '' })
  activeTab.value = 'assembly'
}

async function assembleDocument() {
  try {
    const res = await documentAssemblyApi.assembleDocument(selectedTemplate.value.id, variables)
    if (res.success) {
      assembledContent.value = res.data?.assembledContent || ''
      ElMessage.success('文档生成成功')
    } else {
      ElMessage.error(res.message || '生成失败')
    }
  } catch (e) {
    ElMessage.error('生成失败')
  }
}

function copyContent() {
  navigator.clipboard.writeText(assembledContent.value)
  ElMessage.success('已复制到剪贴板')
}

function showDialog(type, row = null) {
  if (type === 'create') {
    dialogTitle.value = '创建模板'
    isEdit.value = false
    Object.assign(form, { id: null, name: '', category: 'OTHER', description: '', content: '' })
  } else {
    dialogTitle.value = '编辑模板'
    isEdit.value = true
    Object.assign(form, { ...row })
  }
  dialogVisible.value = true
}

async function saveTemplate() {
  try {
    const payload = { ...form }
    if (isEdit.value) {
      await documentAssemblyApi.updateTemplate(form.id, payload)
    } else {
      await documentAssemblyApi.createTemplate(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadTemplates()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function deleteTemplate(row) {
  try {
    await ElMessageBox.confirm('确认删除?', '警告', { type: 'warning' })
    await documentAssemblyApi.deleteTemplate(row.id)
    ElMessage.success('删除成功')
    loadTemplates()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.document-assembly-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
.result-card { margin-top: 20px; }
.content-preview { background: #f5f5f5; padding: 16px; border-radius: 4px; white-space: pre-wrap; max-height: 400px; overflow-y: auto; }
</style>
