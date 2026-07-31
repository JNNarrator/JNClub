import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  base: '/jnclub/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:19005',
        changeOrigin: true,
      },
      '/sso': {
        target: 'http://localhost:19005',
        changeOrigin: true,
      },
      // 本地 dev 下 BASE_URL + 'sso/login' 会请求 /jnclub/sso/login
      // 需要代理到后端并去掉 /jnclub 前缀
      '/jnclub/sso': {
        target: 'http://localhost:19005',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/jnclub/, ''),
      },
      '/jnclub/api': {
        target: 'http://localhost:19005',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/jnclub/, ''),
      },
    },
  },
})
