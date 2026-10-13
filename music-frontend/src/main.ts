import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import App from './App.vue'
import './styles.css'

// === 车机模式：URL 参数 ?car=1 强制，或按车机 UA 识别 ===
// 此前正则含 `pad|tablet|android(?!.*mobile)`，导致 iPad 与部分平板被误判成车机；
// 已收紧为「显式 ?car」+「明确的车机 UA」，宽高比猜测也一并去掉（横屏平板会中招）。
const isCar = new URLSearchParams(location.search).has('car')
  || /carplay|android auto|\bcar\b|flyme auto/i.test(navigator.userAgent)

if (isCar) {
  document.documentElement.classList.add('car-mode')
}

// 首帧绘制后再开启主题过渡，避免亮色冷启动时底色从暗到亮地爬 0.3s
// （styles.css 里 `html.theme-ready` 才带 transition）
requestAnimationFrame(() => {
  requestAnimationFrame(() => {
    document.documentElement.classList.add('theme-ready')
  })
})

// === PWA 安装引导（Android Chrome beforeinstallprompt） ===
let installPromptEvent: Event | null = null
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault()
  installPromptEvent = e
  document.documentElement.classList.add('pwa-installable')
})
window.addEventListener('appinstalled', () => {
  installPromptEvent = null
  document.documentElement.classList.remove('pwa-installable')
})
;(window as any).__installPrompt = () => {
  if (installPromptEvent) {
    (installPromptEvent as any).prompt()
    installPromptEvent = null
    document.documentElement.classList.remove('pwa-installable')
  }
}

// iOS PWA 视口高度修正：100dvh 在 PWA standalone 模式下不等于物理屏幕高度
// iOS PWA 底部安全区域 JS 回退方案
const fixBottomBar = () => {
  const diff = window.screen.height - window.innerHeight
  if (diff > 0) {
    document.documentElement.style.setProperty('--safe-bottom', diff + 'px')
  }
}
window.addEventListener('resize', fixBottomBar)
window.addEventListener('load', fixBottomBar)


// Vant 样式
import 'vant/lib/index.css'

const app = createApp(App)
app.use(createPinia())
app.mount('#app')
