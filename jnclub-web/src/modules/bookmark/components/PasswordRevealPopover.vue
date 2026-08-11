<script setup lang="ts">
import { ref, watch } from 'vue'
import { NPopover, NButton, NIcon, useMessage } from 'naive-ui'
import { Eye, EyeOff, Copy } from 'lucide-vue-next'
import { useVaultStore, type VaultItem } from '../stores/vault'
import { copyText } from '../../../shared/utils/clipboard'

const props = defineProps<{ item: VaultItem }>()

const vaultStore = useVaultStore()
const message = useMessage()

const revealed = ref(false)
const pwdText = ref('')

// 打开时按需拉取明文（缓存已取过则不重复请求）
watch(revealed, async (v) => {
  if (!v || pwdText.value) return
  try {
    const detail = await vaultStore.fetchDetail(props.item.id)
    pwdText.value = detail.password || ''
  } catch (e: any) {
    message.error(e.message || '获取密码失败')
    revealed.value = false
  }
})

const onShowChange = (v: boolean) => {
  revealed.value = v
}

const onCopy = async () => {
  if (await copyText(pwdText.value)) message.success('密码已复制')
  else message.error('复制失败')
}
</script>

<template>
  <NPopover
    :show="revealed"
    trigger="click"
    placement="right"
    :show-arrow="true"
    @update:show="onShowChange"
  >
    <template #trigger>
      <NButton quaternary circle size="small" :title="revealed ? '隐藏密码' : '显示密码'">
        <template #icon><NIcon :component="revealed ? EyeOff : Eye" size="16" /></template>
      </NButton>
    </template>
    <div class="pwd-pop">
      <code class="pwd-text">{{ pwdText || '…' }}</code>
      <NButton size="tiny" type="primary" @click="onCopy">
        <template #icon><NIcon :component="Copy" size="13" /></template>
        复制
      </NButton>
    </div>
  </NPopover>
</template>

<style scoped>
.pwd-pop {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 300px;
}
.pwd-text {
  font-family: var(--font-mono);
  font-size: var(--fs-md);
  color: var(--text-1);
  word-break: break-all;
  user-select: all;
}
</style>
