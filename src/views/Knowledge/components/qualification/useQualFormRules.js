import { reactive } from 'vue'

const CONTACT_REGEX = /^(1[3-9]\d{9}|(0\d{2,3})[-]?\d{7,8}|[^\s@]+@[^\s@]+\.[^\s@]+)$/

export function useQualFormRules(form, certFile, editingId) {
  // §4.2.1.1 必填规则：10 个字段必填（基础5 + 补充4 + 附件1）
  const rules = reactive({
    name: [{ required: true, message: '请输入证书名称', trigger: 'blur' }],
    level: [{ required: true, message: '请输入等级', trigger: 'blur' }],
    issuer: [{ required: true, message: '请输入认证机构', trigger: 'blur' }],
    certificateNo: [{ required: true, message: '请输入证书编号', trigger: 'blur' }],
    issueDate: [{ required: true, message: '请选择发证日期', trigger: 'change' }],
    expiryDate: [
      { required: true, message: '请选择证书有效期', trigger: 'change' },
      {
        validator: (rule, value, callback) => {
          if (value && form.issueDate && value <= form.issueDate) {
            callback(new Error('证书有效期必须晚于发证日期'))
          } else {
            callback()
          }
        },
        trigger: 'change'
      }
    ],
    agency: [{ required: true, message: '请输入代理机构', trigger: 'blur' }],
    agencyContact: [
      { required: true, message: '请输入代理联系方式', trigger: 'blur' },
      {
        validator: (rule, value, callback) => {
          if (value && !CONTACT_REGEX.test(value)) {
            callback(new Error('请输入有效的手机号、固话或邮箱'))
          } else {
            callback()
          }
        },
        trigger: 'blur'
      }
    ],
    certScope: [{ required: true, message: '请输入认证范围', trigger: 'blur' }],
    // CO-155 fix: 这三个字段之前硬编码默认值所以未加必填规则，现在改 el-select 必须校验
    category: [{ required: true, message: '请选择领域', trigger: 'change' }],
    subjectType: [{ required: true, message: '请选择主体类型', trigger: 'change' }],
    subjectName: [{ required: true, message: '请输入主体名称', trigger: 'blur' }],
    attachment: [{
      validator: (rule, value, callback) => {
        if (!certFile.value && !editingId.value) {
          callback(new Error('请上传证书附件'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }]
  })

  return { rules }
}
