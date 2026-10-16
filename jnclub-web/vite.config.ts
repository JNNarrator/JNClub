import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import { resolve } from 'path'

/**
 * PWA manifest 单一来源：既交给 VitePWA（构建时产出 manifest.webmanifest），
 * 也交给下面的 dev 中间件（开发时响应同一路径），避免两处内容漂移。
 */
const pwaManifest = {
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
}

/**
 * dev 下响应 manifest.webmanifest。
 *
 * 为什么需要：vite-plugin-pwa 只在构建时产出该文件，dev server 收到请求后
 * 落到 SPA fallback，返回的是 index.html（HTML）——浏览器按 JSON 解析失败，
 * 每次页面加载都会打印两条 `Manifest: Line: 1, column: 1, Syntax error.`。
 * 这是纯 dev 噪音，却极易被误读为真实缺陷（本次评审中就被误列过一次）。
 *
 * 为什么不用 devOptions.enabled：那会连带启用 dev Service Worker，
 * 导致改代码后刷新拿到的仍是旧缓存，开发体验的代价远大于收益。
 * 这里只补一个静态响应，不碰 SW。
 */
function devManifestPlugin(): Plugin {
  return {
    name: 'jnclub:dev-manifest',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url?.split('?')[0] === '/jnclub/manifest.webmanifest') {
          res.setHeader('Content-Type', 'application/manifest+json')
          res.end(JSON.stringify(pwaManifest))
          return
        }
        next()
      })
    },
  }
}

export default defineConfig({
  base: '/jnclub/',
  plugins: [
    vue(),
    devManifestPlugin(),
    // PWA：可安装 + Service Worker 预缓存应用壳（离线可打开，API 不缓存保持实时）
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg'],
      manifest: pwaManifest,
      workbox: {
        // 只预缓存构建产物中的应用壳静态资源（不缓存 /api、/sso 等动态请求）
        globPatterns: ['**/*.{js,css,html,svg,png,woff2,ico}'],
        navigateFallback: '/jnclub/index.html',
        // SSO 回跳(/jnclub/sso/login?ticket=...)与 API 必须走网络，
        // 否则被 SW fallback 拦截为 SPA 页面，ticket 换票请求到不了后端 → 登录提示"缺少登录凭证"
        navigateFallbackDenylist: [/^\/jnclub\/sso\//, /^\/jnclub\/api\//],
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
    modulePreload: {
      polyfill: false,
      // 避免首屏预载 md-editor（便签编辑器）大 chunk；它只在打开 /notes 路由时才真正加载
      resolveDependencies: (_filename, deps) => deps.filter((dep) => !dep.includes('md-editor')),
    },
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
