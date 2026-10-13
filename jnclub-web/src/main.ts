import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { registerSW } from 'virtual:pwa-register'
import App from './App.vue'
import router from './shared/router'
// 设计 token（构建期由 src/themes/tokens.ts 生成，见 scripts/gen-theme-css.mjs）：
// 以静态 CSS 声明取代原先 App.vue onMounted 里的 117 次 setProperty，
// 使 --xxx 变量随阻塞式样式表在首帧即生效，消除 FOUC。
import './themes/tokens.generated.css'
import './assets/main.css'
import './shared/components/animation/utility.css'

// PWA Service Worker：预缓存应用壳（离线可打开），autoUpdate 静默更新
registerSW({ immediate: true })

const app = createApp(App)

app.use(createPinia())
app.use(router)
// tsParticles 不再全局注册；ParticlesBackground 在用户真正启用粒子时才动态加载并安装，
// 避免首屏加载 tsparticles 引擎相关 chunk。

app.mount('#app')
