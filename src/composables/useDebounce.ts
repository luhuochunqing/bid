// Input: 频繁触发的回调（搜索输入、窗口 resize 等）
// Output: 去抖动后延迟执行，前导/尾随模式可配置
// Pos: composables/ — 前端边缘情况处理 (#23)
import { ref, onUnmounted } from 'vue'

/**
 * 去抖动 composable — 延迟执行回调直到停止触发指定毫秒数。
 * 默认 trailing 模式：最后一次触发后 delayMs 毫秒执行。
 */
export function useDebounce<T extends (...args: any[]) => void>(
  fn: T,
  delayMs: number = 300
): { debouncedFn: (...args: Parameters<T>) => void; cancel: () => void; isPending: ReturnType<typeof ref<boolean>> } {
  const isPending = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null

  const cancel = () => {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
    isPending.value = false
  }

  const debouncedFn = (...args: Parameters<T>) => {
    cancel()
    isPending.value = true
    timer = setTimeout(() => {
      timer = null
      isPending.value = false
      fn(...args)
    }, delayMs)
  }

  onUnmounted(cancel)
  return { debouncedFn, cancel, isPending }
}
