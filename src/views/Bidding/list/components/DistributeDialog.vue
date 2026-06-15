<template>
  <el-dialog
    v-model="modelValue"
    title="标讯分发"
    width="860px"
    :close-on-click-modal="false"
    @close="$emit('reset')"
  >
    <div class="dialog-grid">
      <section>
        <h4>待分发标讯</h4>
        <el-tag>{{ selectedTenders.length }} 条</el-tag>
        <div class="compact-list">
          <div v-for="tender in selectedTenders.slice(0, 5)" :key="tender.id">{{ tender.title }}</div>
        </div>
        <el-radio-group v-model="form.type" class="block-group">
          <el-radio-button value="auto">智能分发</el-radio-button>
          <el-radio-button value="manual">手动指定</el-radio-button>
        </el-radio-group>
        <el-select v-if="form.type === 'auto'" v-model="form.rule" placeholder="选择规则" class="full-width">
          <el-option v-for="rule in assignRules" :key="rule.value" :label="rule.label" :value="rule.value">
            <span>{{ rule.label }}</span>
            <small class="option-desc">{{ rule.desc }}</small>
          </el-option>
        </el-select>
        <el-select
          v-else
          v-model="form.assignees"
          multiple
          filterable
          placeholder="选择指派人员"
          class="full-width"
          :loading="loadingCandidates"
        >
          <el-option
            v-for="candidate in candidates"
            :key="candidate.id"
            :label="formatAssignmentCandidateLabel(candidate)"
            :value="candidate.id"
          >
            {{ formatAssignmentCandidateLabel(candidate) }} · {{ candidate.departmentName }}
          </el-option>
        </el-select>
        <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="填写分发说明（选填）" />
      </section>
      <section>
        <h4>分配预览</h4>
        <el-empty v-if="preview.length === 0" description="暂无可用预览" />
        <div v-else class="preview-list">
          <div v-for="group in preview" :key="group.id" class="preview-group">
            <div class="preview-heading">
              <strong>{{ group.name }}</strong>
              <el-tag size="small">{{ group.count }} 条</el-tag>
            </div>
            <p v-for="tender in group.tenders.slice(0, 3)" :key="tender.id">{{ tender.title }}</p>
          </div>
        </div>
      </section>
    </div>
    <template #footer>
      <el-button @click="modelValue = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="$emit('submit')">
        确认分发 {{ selectedTenders.length }} 条标讯
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ASSIGN_RULES } from '../constants.js'
import { formatAssignmentCandidateLabel } from '../helpers.js'

const modelValue = defineModel({ type: Boolean, default: false })
defineModel('form', { type: Object, required: true })
defineProps({
  selectedTenders: { type: Array, default: () => [] },
  candidates: { type: Array, default: () => [] },
  preview: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  loadingCandidates: { type: Boolean, default: false },
  assignRules: { type: Array, default: () => ASSIGN_RULES },
})

defineEmits(['reset', 'submit'])
</script>
