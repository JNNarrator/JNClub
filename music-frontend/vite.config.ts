import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  // 前后端分离：前端独立部署在 /music/ 路径，资源从 /music/ 加载。
  base: '/music/',
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      // 音乐后端已并入 JNClub 单体（19005），对外路径仍是 /music/api/...
      // 注意：这里只能代理 /music/api，不能写 /music ——
      // 前端自身就部署在 /music/ 下，代理整个 /music 会把页面与静态资源
      // 一并转发给后端（dev 下表现为访问 http://localhost:5174/music/ 返回 500）。
      '/music/api': {
        target: 'http://127.0.0.1:19005',
        changeOrigin: true,
      }
    }
  }
})
