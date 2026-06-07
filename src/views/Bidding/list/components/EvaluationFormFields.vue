<template>
  <el-form ref="evalFormRef" :model="form" label-width="120px">
    <el-row :gutter="16">
      <el-col :span="24">
        <el-form-item label="项目背景" prop="projectBackground">
          <el-input v-model="form.projectBackground" type="textarea" :rows="3" placeholder="请填写项目背景信息" />
        </el-form-item>
      </el-col>
      <el-col :span="24">
        <el-form-item label="竞争对手情况" prop="competitorAnalysis">
          <el-input v-model="form.competitorAnalysis" type="textarea" :rows="3" placeholder="请填写竞争对手分析" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="合同周期起" prop="contractPeriodStart">
          <el-date-picker v-model="form.contractPeriodStart" type="date" placeholder="选择合同开始日期" class="full-width" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="合同周期止" prop="contractPeriodEnd">
          <el-date-picker v-model="form.contractPeriodEnd" type="date" placeholder="选择合同结束日期" class="full-width" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="入围家数" prop="shortlistedCount">
          <el-input-number v-model="form.shortlistedCount" :min="1" placeholder="入围家数" class="full-width" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="平台服务费" prop="platformServiceFee">
          <el-input-number v-model="form.platformServiceFee" :min="0" :precision="2" placeholder="平台服务费金额" class="full-width" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="往期报价" prop="previousQuotation">
          <el-input-number v-model="form.previousQuotation" :min="0" :precision="2" placeholder="往期报价金额" class="full-width" />
        </el-form-item>
      </el-col>
      <el-col :span="24">
        <el-form-item label="投标建议" prop="bidRecommendation">
          <el-input v-model="form.bidRecommendation" type="textarea" :rows="2" placeholder="投标建议（可选）" />
        </el-form-item>
      </el-col>
    </el-row>
  </el-form>
</template>

<script setup>
const form = defineModel({ type: Object, required: true })
</script>
