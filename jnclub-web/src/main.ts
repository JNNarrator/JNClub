import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { registerSW } from 'virtual:pwa-register'
import Particles from '@tsparticles/vue3'
import { loadSlim } from '@tsparticles/slim'
import App from './App.vue'
import router from './shared/router'
import './assets/main.css'
import './shared/components/animation/utility.css'

// PWA Service Worker：预缓存应用壳（离线可打开），autoUpdate 静默更新
registerSW({ immediate: true })

const app = createApp(App)

app.use(createPinia())
app.use(router)
// tsParticles 全局注册（组件 <VueParticles>），slim 体积最小（circle 形状够用）
app.use(Particles, {
  init: async (engine) => {
    await loadSlim(engine)
  },
})

app.mount('#app')
