import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import { resolve } from 'path'

export default defineConfig({
  base: '/jnclub/',
  plugins: [
    vue(),
    // PWA：可安装 + Service Worker 预缓存应用壳（离线可打开，API 不缓存保持实时）
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg'],
      manifest: {
        name: 'JNClub 个人工作台',
        short_name: 'JNClub',
        description: '收藏夹 · 便签 · 云盘 · 密码库 — 个人工作台',
        theme_color: '#EC5B8E',
        background_color: '#F5F5F7',
        display: 'standalone',
        orientation: 'any',
        start_url: '/jnclub/',
        scope: '/jnclub/',
        lang: 'zh-CN',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: 'pwa-maskable-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
          { src: 'pwa-180x180.png', sizes: '180x180', type: 'image/png' },
        ],
      },
      workbox: {
        // 只预缓存构建产物中的应用壳静态资源（不缓存 /api、/sso 等动态请求）
        globPatterns: ['**/*.{js,css,html,svg,png,woff2,ico}'],
        navigateFallback: '/jnclub/index.html',
        runtimeCaching: [
          // Google Fonts 缓存字体（跨域，CacheFirst 命中即用）
          {
            urlPattern: /^https:\/\/fonts\.(googleapis|gstatic)\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'jnclub-fonts',
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 365 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // 大依赖独立 chunk，减少首屏/编辑页单 chunk 体积
          'naive-ui': ['naive-ui'],
          'md-editor': ['md-editor-v3'],
          'lucide': ['lucide-vue-next'],
          'vendor': ['vue', 'vue-router', 'pinia', 'axios'],
        },
      },
    },
    // md-editor-v3 内含 codemirror，为便签编辑页路由懒加载 chunk（gzip 约 300KB），阈值放宽容许
    chunkSizeWarningLimit: 900,
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
