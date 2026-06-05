// Input: domain-specific resource API submodules
// Output: resourcesApi compatibility facade
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 资源管理模块 API
 * 真实 API 资源管理访问层兼容入口
 */
import { accountsApi } from '@/api/modules/resources/accounts'
import {
  barSiteAccountsApi,
  barSiteAttachmentsApi,
  barSiteSopApi,
  barSitesApi
} from '@/api/modules/resources/barSites'
import { barCertificatesApi } from '@/api/modules/resources/certificates'
import { expensesApi } from '@/api/modules/resources/expenses'

export {
  accountsApi,
  barSiteAccountsApi,
  barSiteAttachmentsApi,
  barSiteSopApi,
  barSitesApi,
  barCertificatesApi,
  expensesApi
}

export default {
  accounts: accountsApi,
  barSites: barSitesApi,
  barSiteAccounts: barSiteAccountsApi,
  barSiteSop: barSiteSopApi,
  barSiteAttachments: barSiteAttachmentsApi,
  certificates: barCertificatesApi,
  expenses: expensesApi
}
