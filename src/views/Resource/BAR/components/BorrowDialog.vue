<template>
  <el-dialog
    v-model="dialogVisible"
    title="UK借用申请"
    width="500px"
    @close="handleClose"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <!-- 站点信息 -->
      <el-form-item label="站点">
        <el-input :value="site?.name" disabled />
      </el-form-item>

      <!-- UK列表选择 -->
      <el-form-item label="选择UK">
        <el-select
          v-model="form.ukId"
          placeholder="请选择要借用的UK"
          style="width: 100%"
        >
          <el-option
            v-for="uk in availableUKs"
            :key="uk.id"
            :label="`${uk.type} (${uk.serialNo})`"
            :value="uk.id"
            :disabled="uk.status !== 'available'"
          >
            <span>{{ uk.type }}</span>
            <span style="float: right; color: #8492a6; font-size: 13px">
              {{ uk.serialNo }}
              <el-tag v-if="uk.status === 'borrowed'" type="warning" size="small">已借出</el-tag>
              <el-tag v-else-if="uk.status === 'expired'" type="danger" size="small">已过期</el-tag>
              <el-tag v-else type="success" size="small">在库</el-tag>
            </span>
          </el-option>
        </el-select>
      </el-form-item>

      <!-- UK详情展示 -->
      <el-form-item v-if="selectedUK" label="UK信息">
        <div class="uk-info">
          <p><span class="label">类型：</span>{{ selectedUK.type }}</p>
          <p><span class="label">序列号：</span>{{ selectedUK.serialNo }}</p>
          <p><span class="label">当前持有人：</span>{{ selectedUK.holder }}</p>
          <p><span class="label">有效期：</span>{{ selectedUK.expiryDate }}</p>
          <p v-if="selectedUK.location"><span class="label">存放位置：</span>{{ selectedUK.location }}</p>
        </div>
      </el-form-item>

      <!-- 借用人 -->
      <el-form-item label="借用人" prop="borrower">
        <el-input v-model="form.borrower" placeholder="请输入借用人姓名" />
      </el-form-item>

      <!-- 关联项目 -->
      <el-form-item label="关联项目" prop="projectId">
        <el-select v-model="form.projectId" placeholder="请选择关联项目" style="width: 100%">
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="project.name"
            :value="project.id"
          />
        </el-select>
      </el-form-item>

      <!-- 借用用途 -->
      <el-form-item label="借用用途" prop="purpose">
        <el-radio-group v-model="form.purpose">
          <el-radio value="purchase">购买标书</el-radio>
          <el-radio value="upload">投标上传</el-radio>
          <el-radio value="other">其他</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 其他用途输入 -->
      <el-form-item v-if="form.purpose === 'other'" label="用途说明">
        <el-input v-model="form.purposeOther" placeholder="请说明具体用途" />
      </el-form-item>

      <!-- 预计归还时间 -->
      <el-form-item label="预计归还" prop="returnDate">
        <el-date-picker
          v-model="form.returnDate"
          type="date"
          placeholder="选择日期"
          style="width: 100%"
          :disabled-date="disabledDate"
        />
      </el-form-item>

      <!-- 备注 -->
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注信息" />
      </el-form-item>

      <!-- 审批流程（展示用） -->
      <el-form-item label="审批流程">
        <div class="approval-flow">
          <div class="flow-step">
            <div class="step-icon active">
              <el-icon><User /></el-icon>
            </div>
            <div class="step-text">
              <div class="step-title">申请人</div>
              <div class="step-desc">{{ form.borrower || '待填写' }}</div>
            </div>
          </div>
          <div class="flow-arrow">→</div>
          <div class="flow-step">
            <div class="step-icon">
              <el-icon><Document /></el-icon>
            </div>
            <div class="step-text">
              <div class="step-title">部门审批</div>
              <div class="step-desc">待审批</div>
            </div>
          </div>
          <div class="flow-arrow">→</div>
          <div class="flow-step">
            <div class="step-icon">
              <el-icon><Lock /></el-icon>
            </div>
            <div class="step-text">
              <div class="step-title">资产管理员</div>
              <div class="step-desc">待确认</div>
            </div>
          </div>
          <div class="flow-arrow">→</div>
          <div class="flow-step">
            <div class="step-icon">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <div class="step-text">
              <div class="step-title">出库</div>
              <div class="step-desc">待出库</div>
            </div>
          </div>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">
        提交申请
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { User, Document, Lock, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {  projectsApi } from '@/api'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  site: {
    type: Object,
    default: null
  },
  uk: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const projects = ref([])

const form = ref({
  ukId: '',
  borrower: '',
  projectId: '',
  purpose: 'upload',
  purposeOther: '',
  returnDate: '',
  remark: ''
})

const rules = {
  ukId: [{ required: true, message: '请选择要借用的UK', trigger: 'change' }],
  borrower: [{ required: true, message: '请输入借用人', trigger: 'blur' }],
  projectId: [{ required: true, message: '请选择关联项目', trigger: 'change' }],
  purpose: [{ required: true, message: '请选择借用用途', trigger: 'change' }],
  returnDate: [{ required: true, message: '请选择预计归还日期', trigger: 'change' }]
}

// 可用的UK列表
const availableUKs = computed(() => {
  return props.site?.uks || []
})

// 选中的UK
const selectedUK = computed(() => {
  return availableUKs.value.find(uk => uk.id === form.value.ukId)
})

const loadProjects = async () => {
  const response = await projectsApi.getList()
  if (!response?.success) {
    projects.value = []
    ElMessage.warning(response?.msg || '项目列表加载失败')
    return
  }

  projects.value = Array.isArray(response.data) ? response.data : []
}

// 禁用过去的日期
const disabledDate = (time) => {
  return time.getTime() < Date.now() - 24 * 60 * 60 * 1000
}

watch(() => props.modelValue, (val) => {
  dialogVisible.value = val
  if (val) {
    loadProjects()
    if (props.uk?.id) {
      form.value.ukId = props.uk.id
    }
  }
})

watch(dialogVisible, (val) => {
  emit('update:modelValue', val)
})

const resetForm = () => {
  form.value = {
    ukId: '',
    borrower: '',
    projectId: '',
    purpose: 'upload',
    purposeOther: '',
    returnDate: '',
    remark: ''
  }
  formRef.value?.clearValidate()
}

const handleClose = () => {
  dialogVisible.value = false
  resetForm()
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()

    submitting.value = true

    const data = {
      ukId: form.value.ukId,
      borrower: form.value.borrower,
      project: projects.value.find(p => String(p.id) === String(form.value.projectId))?.name,
      projectId: form.value.projectId,
      purpose: form.value.purpose === 'other' ? form.value.purposeOther : form.value.purpose,
      returnDate: form.value.returnDate,
      remark: form.value.remark
    }

    emit('confirm', data)

    // 模拟提交延迟
    setTimeout(() => {
      submitting.value = false
      handleClose()
    }, 500)
  } catch (error) {
    submitting.value = false
  }
}
</script>

<style scoped>
.uk-info {
  padding: 12px;
  background: var(--bg-subtle);
  border-radius: 6px;
}

.uk-info p {
  margin: 4px 0;
  font-size: 14px;
}

.uk-info .label {
  color: var(--text-muted);
  margin-right: 8px;
}

.approval-flow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}

.flow-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  flex: 1;
}

.flow-step .step-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
  color: var(--text-muted);
}

.flow-step .step-icon.active {
  background: #409eff;
  color: var(--bg-card);
}

.flow-step .step-text {
  font-size: 12px;
}

.flow-step .step-title {
  font-weight: 500;
  color: var(--gray-750);
}

.flow-step .step-desc {
  color: var(--text-muted);
}

.flow-arrow {
  color: #dcdfe6;
  font-size: 18px;
  margin: 0 4px;
}
</style>
