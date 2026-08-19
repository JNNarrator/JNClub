import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const showPlayerPage = ref(false)
  const showLyricsPanel = ref(false)

  function openPlayerPage() {
    showPlayerPage.value = true
    showLyricsPanel.value = false
  }

  function closePlayerPage() {
    showPlayerPage.value = false
  }

  function openLyricsPanel() {
    showLyricsPanel.value = true
  }

  function closeLyricsPanel() {
    showLyricsPanel.value = false
  }

  return {
    showPlayerPage,
    showLyricsPanel,
    openPlayerPage,
    closePlayerPage,
    openLyricsPanel,
    closeLyricsPanel,
  }
})
