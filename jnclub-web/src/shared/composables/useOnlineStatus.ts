/**
 * useOnlineStatus — 在线/离线状态监听
 * PWA 应用壳离线可打开，但业务数据依赖网络，离线时提示用户
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'

export function useOnlineStatus() {
  const isOnline = ref(navigator.onLine)

  const handleOnline = () => { isOnline.value = true }
  const handleOffline = () => { isOnline.value = false }

  onMounted(() => {
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  })

  return { isOnline }
}
