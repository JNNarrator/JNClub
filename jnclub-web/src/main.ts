import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { registerSW } from 'virtual:pwa-register'
import App from './App.vue'
import router from './shared/router'
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
