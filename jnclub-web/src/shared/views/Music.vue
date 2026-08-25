<script setup lang="ts">
/**
 * Music.vue — 音乐播放器内嵌页
 * 在 JNClub 壳内以 iframe 全屏内嵌 /music/ 播放器（nginx 静态托管，对外路径不变）。
 * 音乐页面自身无登录态要求，保持匿名访问；仅当用户进入 JNClub 后通过侧边栏入口打开。
 */
</script>

<template>
  <div class="music-page">
    <iframe
      class="music-frame"
      src="/music/"
      title="JNMusic 音乐播放器"
      loading="eager"
      allow="autoplay; fullscreen"
    />
  </div>
</template>

<style scoped>
.music-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.music-frame {
  flex: 1;
  width: 100%;
  border: none;
  background: #fff; /* 内嵌音乐播放器为独立页面，固定白底避免加载前透出暗色背景 */
}
</style>
