<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="480"
    @close="handleClose"
    role="dialog"
    aria-label="企业微信绑定"
  >
    <div class="wecom-binding-body">
      <p class="wecom-binding-hint">
        请填入该用户在企业微信中的 userid。管理员可在企业微信后台
        <a href="https://work.weixin.qq.com/wework_admin/frame#contacts" target="_blank" rel="noopener">通讯录页面</a>
        查看。
      </p>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="用户" prop="userLabel">
          <span>{{ userLabel }}</span>
        </el-form-item>
        <el-form-item label="企微 userid" prop="wecomUserId">
          <el-input
            v-model="form.wecomUserId"
            placeholder="例如 zhangsan 或企微分配的用户ID"
            maxlength="64"
            show-word-limit
            clearable
            aria-label="企微 userid"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button
        v-if="currentBinding"
        type="danger"
        plain
        :loading="unbinding"
        @click="handleUnbind"
        aria-label="解除绑定"
      >
        解除绑定
      </el-button>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="!form.wecomUserId"
        @click="handleSave"
        aria-label="保存绑定"
      >
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { wecomBindingApi } from '@/api/modules/wecomBinding'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  userId: { type: [Number, String], default: null },
  userName: { type: String, default: '' },
  currentBinding: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'saved', 'unbound'])

const formRef = ref(null)
const saving = ref(false)
const unbinding = ref(false)
const form = ref({ wecomUserId: props.currentBinding || '' })

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const userLabel = computed(() => props.userName || `用户 #${props.userId}`)
const title = computed(() => (props.currentBinding ? '更新企业微信绑定' : '绑定企业微信 userid'))

const rules = {
  wecomUserId: [
    { required: true, message: '请输入企业微信 userid', trigger: 'blur' },
    { max: 64, message: '最多 64 字符', trigger: 'blur' }
  ]
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      form.value.wecomUserId = props.currentBinding || ''
    }
  },
  { immediate: true }
)

const handleClose = () => {
  visible.value = false
}

const handleSave = async () => {
  if (!props.userId) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const result = await wecomBindingApi.bind(props.userId, form.value.wecomUserId.trim())
    ElMessage.success('企业微信绑定已更新')
    emit('saved', result?.wecomUserId ?? form.value.wecomUserId.trim())
    handleClose()
  } catch (error) {
    ElMessage.error(error?.message || '绑定失败，请稍后再试')
  } finally {
    saving.value = false
  }
}

const handleUnbind = async () => {
  if (!props.userId) return
  unbinding.value = true
  try {
    await wecomBindingApi.unbind(props.userId)
    ElMessage.success('已解除企业微信绑定')
    emit('unbound')
    handleClose()
  } catch (error) {
    ElMessage.error(error?.message || '解除绑定失败，请稍后再试')
  } finally {
    unbinding.value = false
  }
}
</script>

<style scoped>
.wecom-binding-body {
  padding: 4px 4px 12px;
}

.wecom-binding-hint {
  font-size: 13px;
  color: var(--text-secondary, #64748b);
  margin-bottom: 16px;
  line-height: 1.6;
}

.wecom-binding-hint a {
  color: var(--brand-xiyu-logo, #2E7659);
}
</style>
