/**
 * CO-554 v3: 资质行是否可下载附件的判定函数。
 *
 * 设计原则：只认 attachments（附件表真相），不认主表 fileUrl（不一致缓存值）。
 * 历史背景：主表 fileUrl 是冗余字段，与附件表多次出现不一致（CO-368 守卫、CO-554
 * 多路径写入），导致「无附件但 fileUrl 有脏数据」的记录误显示下载按钮，点下载拿到
 * 服务器错误响应被存成 txt。本函数统一判定入口，确保按钮显示与附件表一致。
 *
 * @param {{ fileUrl?: string, attachments?: Array<{ fileUrl?: string }> }} row
 * @returns {boolean}
 */
export const hasDownloadableAttachment = (row) =>
  Array.isArray(row.attachments) &&
  row.attachments.some((a) => a.fileUrl && String(a.fileUrl).trim())
