<template>
  <div>
    <el-form-item label="签约日期" prop="signingDate">
      <el-date-picker 
        v-model="form.signingDate" 
        type="date" 
        value-format="YYYY-MM-DD" 
        placeholder="选择签约时间"
        style="width: 100%" 
      />
    </el-form-item>
    <el-form-item label="截止日期" prop="expiryDate">
      <el-date-picker 
        v-model="form.expiryDate" 
        type="date" 
        value-format="YYYY-MM-DD" 
        placeholder="合同期满失效时间"
        style="width: 100%" 
      />
    </el-form-item>
    <el-form-item label="总截止日期" prop="totalExpiryDate">
      <el-date-picker 
        v-model="form.totalExpiryDate" 
        type="date" 
        value-format="YYYY-MM-DD" 
        placeholder="含可续约延期的最终截止时间"
        style="width: 100%" 
      />
    </el-form-item>
  </div>
</template>

<script setup>
defineProps({
  form: {
    type: Object,
    required: true
  }
})
</script>
