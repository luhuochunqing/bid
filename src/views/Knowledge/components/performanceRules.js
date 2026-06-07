export const rules = {
  contractName: [{ required: true, message: '请输入合同名称', trigger: 'blur' }],
  signingEntity: [{ required: true, message: '请填写完整必填项', trigger: 'blur' }],
  groupCompany: [{ required: true, message: '请填写完整必填项', trigger: 'blur' }],
  customerType: [{ required: true, message: '请选择客户类型', trigger: 'change' }],
  projectType: [{ required: true, message: '请选择', trigger: 'change' }],
  dockingMethod: [{ required: true, message: '请选择', trigger: 'change' }],
  signingDate: [{ required: true, message: '请选择关键日期', trigger: 'change' }],
  expiryDate: [{ required: true, message: '请选择关键日期', trigger: 'change' }],
  contactPerson: [{ required: true, message: '请填写完整必填项', trigger: 'blur' }],
  contactInfo: [
    { required: true, message: '请填写完整必填项', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (!value) return callback()
        const phoneRegex = /^1[3-9]\d{9}$/
        const telRegex = /^(0\d{2,3}-?)?\d{7,8}$/
        const emailRegex = /^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$/
        if (!phoneRegex.test(value) && !telRegex.test(value) && !emailRegex.test(value)) {
          callback(new Error('请输入有效的联系方式'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  territory: [{ required: true, message: '请填写完整必填项', trigger: 'blur' }],
  customerAddress: [{ required: true, message: '请填写完整必填项', trigger: 'blur' }],
  xiyuProjectManager: [{ required: true, message: '请填写完整必填项', trigger: 'blur' }]
}

export const createDefaultForm = () => ({
  id: null,
  contractName: '',
  signingEntity: '',
  groupCompany: '',
  customerType: '',
  industry: '',
  projectType: '',
  dockingMethod: '',
  customerLevel: 'GROUP',
  signingDate: '',
  expiryDate: '',
  totalExpiryDate: '',
  contactPerson: '',
  contactInfo: '',
  territory: '',
  customerAddress: '',
  xiyuProjectManager: '',
  mallWebsiteUrl: '',
  hasBidNotice: false,
  remarks: '',
  attachmentMap: {
    CONTRACT_AGREEMENT: { fileName: '', fileUrl: '' },
    MALL_SCREENSHOT: { fileName: '', fileUrl: '' },
    SOE_DIRECTORY: { fileName: '', fileUrl: '' },
    CATEGORY_PAGE: { fileName: '', fileUrl: '' },
    RELATIONSHIP_PROOF: { fileName: '', fileUrl: '' },
    BID_NOTICE: { fileName: '', fileUrl: '' },
    OTHER: { fileName: '', fileUrl: '' }
  }
})
