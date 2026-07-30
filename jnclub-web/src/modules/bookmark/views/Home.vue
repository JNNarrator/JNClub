<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NCard, NGrid, NGi, NButton, NIcon, NEmpty, NSpin } from 'naive-ui'
import { AddOutline } from '@vicons/ionicons5'
import { useDirectoryStore } from '../stores/directory'
import { useBookmarkStore } from '../stores/bookmark'
import { useNoteStore } from '../stores/note'
import DirectoryTree from '../components/DirectoryTree.vue'
import BookmarkGrid from '../components/BookmarkGrid.vue'
import NoteList from '../components/NoteList.vue'

const directoryStore = useDirectoryStore()
const bookmarkStore = useBookmarkStore()
const noteStore = useNoteStore()

const activeTab = ref<'bookmarks' | 'notes'>('bookmarks')
const selectedDirectoryId = ref<number | null>(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    await directoryStore.fetchDirectories()
    if (directoryStore.directories.length > 0) {
      selectedDirectoryId.value = directoryStore.directories[0].id
      await loadData()
    }
  } finally {
    loading.value = false
  }
})

const loadData = async () => {
  if (selectedDirectoryId.value) {
    if (activeTab.value === 'bookmarks') {
      await bookmarkStore.fetchBookmarks(selectedDirectoryId.value)
    } else {
      await noteStore.fetchNotes(selectedDirectoryId.value)
    }
  }
}

const handleDirectorySelect = async (id: number) => {
  selectedDirectoryId.value = id
  await loadData()
}

const handleTabChange = async (tab: 'bookmarks' | 'notes') => {
  activeTab.value = tab
  await loadData()
}
</script>

<template>
  <div class="home">
    <NGrid :x-gap="24" :y-gap="24" :cols="24">
      <!-- 左侧目录树 -->
      <NGi :span="6">
        <NCard title="目录" :bordered="true">
          <DirectoryTree
            :directories="directoryStore.directories"
            :selected-id="selectedDirectoryId"
            @select="handleDirectorySelect"
            @refresh="directoryStore.fetchDirectories"
          />
        </NCard>
      </NGi>
      
      <!-- 右侧内容区 -->
      <NGi :span="18">
        <NCard :bordered="true">
          <template #header>
            <div style="display: flex; align-items: center; justify-content: space-between;">
              <div style="display: flex; gap: 16px;">
                <NButton
                  :type="activeTab === 'bookmarks' ? 'primary' : 'default'"
                  @click="handleTabChange('bookmarks')"
                >
                  收藏夹
                </NButton>
                <NButton
                  :type="activeTab === 'notes' ? 'primary' : 'default'"
                  @click="handleTabChange('notes')"
                >
                  便签
                </NButton>
              </div>
              <NButton type="primary">
                <template #icon>
                  <NIcon :component="AddOutline" />
                </template>
                {{ activeTab === 'bookmarks' ? '添加收藏' : '新建便签' }}
              </NButton>
            </div>
          </template>
          
          <NSpin :show="loading">
            <template v-if="!selectedDirectoryId">
              <NEmpty description="请先选择一个目录" />
            </template>
            <template v-else-if="activeTab === 'bookmarks'">
              <BookmarkGrid
                :bookmarks="bookmarkStore.bookmarks"
                @refresh="loadData"
              />
            </template>
            <template v-else>
              <NoteList
                :notes="noteStore.notes"
                @refresh="loadData"
              />
            </template>
          </NSpin>
        </NCard>
      </NGi>
    </NGrid>
  </div>
</template>

<style scoped>
.home {
  height: 100%;
}
</style>
