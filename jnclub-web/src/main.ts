import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { registerSW } from 'virtual:pwa-register'
import App from './App.vue'
import router from './shared/router'
import './assets/main.css'

// PWA Service Worker：预缓存应用壳（离线可打开），autoUpdate 静默更新
registerSW({ immediate: true })

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
