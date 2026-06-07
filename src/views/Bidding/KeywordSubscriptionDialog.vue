<template>
  <el-dialog
    v-model="visible"
    :title="isEditing ? '编辑订阅' : '新建订阅'"
    width="500px"
    :close-on-click-modal="false"
    @closed="resetForm"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="100px"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="订阅名称" prop="name">
        <el-input v-model="form.name" placeholder="例如：智慧园区项目" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="逻辑关系" prop="logicOperator">
        <el-radio-group v-model="form.logicOperator">
          <el-radio value="OR">OR（任一关键词匹配）</el-radio>
          <el-radio value="AND">AND（全部关键词匹配）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="关键词" prop="keywords">
        <div class="keywords-input-area">
          <div v-for="(kw, idx) in form.keywords" :key="idx" class="keyword-row">
            <el-input
              v-model="form.keywords[idx]"
              placeholder="输入关键词"
              size="small"
              maxlength="200"
              @keyup.enter="addKeyword"
            />
            <el-button type="danger" :icon="Remove" size="small" circle
              @click="removeKeyword(idx)" :disabled="form.keywords.length <= 1" />
          </div>
          <el-button type="primary" link :icon="Plus" @click="addKeyword" class="add-keyword-btn">
            添加关键词
          </el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEditing ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Remove } from '@element-plus/icons-vue'
import httpClient from '@/api/client.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editData: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = ref(props.modelValue)
const isEditing = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({
  name: '',
  logicOperator: 'OR',
  keywords: ['']
})
const formRules = {
  name: [{ required: true, message: '请输入订阅名称', trigger: 'blur' }],
  logicOperator: [{ required: true, message: '请选择逻辑关系', trigger: 'change' }],
  keywords: [{
    validator: (rule, value, callback) => {
      if (!value || value.length === 0 || value.every(k => !k.trim())) {
        callback(new Error('至少输入一个关键词'))
      } else { callback() }
    }, trigger: 'change'
  }]
}

watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })
watch(() => props.editData, (data) => {
  if (data) {
    isEditing.value = true
    form.name = data.name
    form.logicOperator = data.logicOperator
    form.keywords = data.keywords?.length > 0 ? [...data.keywords] : ['']
  } else {
    isEditing.value = false
    resetForm()
  }
}, { immediate: true })

function resetForm() {
  form.name = ''
  form.logicOperator = 'OR'
  form.keywords = ['']
}

function addKeyword() {
  if (form.keywords.length < 20) { form.keywords.push('') }
  else { ElMessage.warning('最多20个关键词') }
}

function removeKeyword(idx) { form.keywords.splice(idx, 1) }

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = { name: form.name.trim(), logicOperator: form.logicOperator, keywords: form.keywords.filter(k => k.trim()) }
    const url = isEditing.value
      ? `/api/tender-keyword-subscriptions/${props.editData.id}`
      : '/api/tender-keyword-subscriptions'
    const resp = isEditing.value
      ? await httpClient.put(url, payload)
      : await httpClient.post(url, payload)
    if (resp.success) {
      ElMessage.success(isEditing.value ? '订阅更新成功' : '订阅创建成功')
      visible.value = false
      emit('saved')
    }
  } catch (e) {
    console.error('Failed to save subscription:', e)
  } finally { submitting.value = false }
}
</script>

<style scoped>
.keywords-input-area { display: flex; flex-direction: column; gap: 8px; }
.keyword-row { display: flex; gap: 8px; align-items: center; }
.keyword-row .el-input { flex: 1; }
.add-keyword-btn { margin-top: 4px; }
</style>
