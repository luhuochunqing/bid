/**
 * 并发上传工具（L-07）
 *
 * 替代 for-await 串行上传，使用 Promise.allSettled + 并发上限。
 * - 任意文件失败不阻塞其他文件，失败项汇总返回
 * - 并发上限默认 3，避免一次提交过多 multipart 请求压满后端
 * - 不抛异常：成功/失败都在返回值里，由调用方决定如何提示
 */

/**
 * 以受控并发上传多个文件。
 *
 * @param {Array<File|any>} files - 待上传文件列表
 * @param {(file: File|any, index: number) => Promise<any>} uploader - 单文件上传函数
 * @param {Object} [options]
 * @param {number} [options.concurrency=3] - 并发上限
 * @returns {Promise<{ successes: Array<{ file: any, result: any }>, failures: Array<{ file: any, error: any }> }>}
 */
export async function parallelUpload(files, uploader, options = {}) {
  const concurrency = Math.max(1, options.concurrency ?? 3)
  const list = Array.from(files || [])
  const results = new Array(list.length)

  let cursor = 0
  async function worker() {
    while (cursor < list.length) {
      const index = cursor++
      try {
        results[index] = { ok: true, result: await uploader(list[index], index) }
      } catch (error) {
        results[index] = { ok: false, error }
      }
    }
  }

  const workers = Array.from({ length: Math.min(concurrency, list.length) }, () => worker())
  await Promise.all(workers)

  const successes = []
  const failures = []
  results.forEach((r, i) => {
    if (r.ok) successes.push({ file: list[i], result: r.result })
    else failures.push({ file: list[i], error: r.error })
  })
  return { successes, failures }
}

export default parallelUpload
