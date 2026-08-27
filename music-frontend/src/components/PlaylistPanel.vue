<script setup lang="ts">
/**
 * PlaylistPanel.vue — 歌单面板（管理或「加入歌单」选择）
 * - manage：列表 + 详情 + 新建 / 重命名 / 删除 / 播放 / 移除
 * - pick  ：选择目标歌单，把 targetTrack 加入（供曲目行「＋」使用）
 */
import { ref, computed, onMounted } from 'vue'
import { ElIcon } from 'element-plus'
import {
  Close, Plus, ArrowLeft, VideoPlay, EditPen, Delete, FolderOpened,
} from '@element-plus/icons-vue'
import { showToast, showConfirmDialog } from 'vant'
import { usePlaylistsStore, type PlaylistItem } from '../stores/playlists'
import { usePlayerStore, type Track } from '../stores/player'

const props = defineProps<{
  /** manage：面板由 store.panelOpen 控制；pick：由父级 v-model:show 控制 */
  mode?: 'manage' | 'pick'
  targetTrack?: Track | null
  show?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void
  (e: 'close'): void
}>()

const playlists = usePlaylistsStore()
const player = usePlayerStore()

const shown = computed({
  get: () => (props.mode === 'manage' ? playlists.panelOpen : props.show ?? false),
  set: (v: boolean) => {
    if (props.mode === 'manage') {
      if (!v) playlists.closePanel()
    } else {
      emit('update:show', v)
      if (!v) emit('close')
    }
  },
})

const newName = ref('')
const creating = ref(false)
const editingId = ref<number | null>(null)
const editingName = ref('')
const addingTrackId = ref<string | null>(null)

function backToList() {
  playlists.activeId = null
  playlists.detail = null
  editingId.value = null
}

async function handleCreate() {
  const name = newName.value.trim()
  if (!name) {
    showToast('请输入歌单名称')
    return
  }
  creating.value = true
  try {
    const created = await playlists.createPlaylist(name)
    newName.value = ''
    // pick 模式：创建后直接把目标曲目加入并关闭
    if (props.mode === 'pick' && props.targetTrack && created) {
      await playlists.addTrack(created.id, props.targetTrack.trackId)
      showToast('已创建并加入歌单')
      shown.value = false
    } else {
      showToast('歌单已创建')
    }
  } catch (e) {
    showToast({ message: (e as Error).message, type: 'error' })
  } finally {
    creating.value = false
  }
}

function startRename(item: PlaylistItem) {
  editingId.value = item.id
  editingName.value = item.name
}

async function confirmRename() {
  const id = editingId.value
  const name = editingName.value.trim()
  editingId.value = null
  if (id == null || !name) return
  try {
    await playlists.renamePlaylist(id, name)
    showToast('已重命名')
  } catch (e) {
    showToast({ message: (e as Error).message, type: 'error' })
  }
}

async function handleDelete(item: PlaylistItem) {
  try {
    await showConfirmDialog({
      title: '删除歌单',
      message: `确定删除「${item.name}」吗？歌单内曲目不会从曲库删除。`,
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return // 用户取消
  }
  try {
    await playlists.removePlaylist(item.id)
    showToast('歌单已删除')
  } catch (e) {
    showToast({ message: (e as Error).message, type: 'error' })
  }
}

async function openDetail(id: number) {
  await playlists.openDetail(id)
}

function playDetail(idx: number) {
  const tracks = playlists.detail?.tracks
  if (!tracks || !tracks.length) return
  player.setQueue(tracks, idx)
  showToast(`正在播放：${tracks[idx]?.name || ''}`)
}

async function handleRemoveTrack(trackId: string) {
  const id = playlists.activeId.value
  if (id == null) return
  try {
    await playlists.removeTrack(id, trackId)
    showToast('已从歌单移除')
  } catch (e) {
    showToast({ message: (e as Error).message, type: 'error' })
  }
}

/** pick 模式：把目标曲目加入歌单 */
async function handlePickTarget(playlistId: number) {
  const track = props.targetTrack
  if (!track) return
  addingTrackId.value = track.trackId
  try {
    await playlists.addTrack(playlistId, track.trackId)
    showToast(`已加入「${playlists.playlists.find((p) => p.id === playlistId)?.name || '歌单'}」`)
    shown.value = false
  } catch (e) {
    showToast({ message: (e as Error).message || '添加失败，可能已在歌单中', type: 'error' })
  } finally {
    addingTrackId.value = null
  }
}

onMounted(() => {
  if (!playlists.playlists.length) {
    playlists.fetchPlaylists()
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="pl-overlay">
      <div v-if="shown" class="pl-overlay" @click.self="shown = false">
        <div class="pl-panel">
          <header class="pl-head">
            <h3 class="pl-title">
              <el-icon :size="16"><FolderOpened /></el-icon>
              {{ mode === 'pick' ? '加入歌单' : '我的歌单' }}
            </h3>
            <button class="pl-close" aria-label="关闭" @click="shown = false">
              <el-icon :size="16"><Close /></el-icon>
            </button>
          </header>

          <!-- 详情视图 -->
          <template v-if="mode === 'manage' && playlists.activeId != null">
            <div class="pl-subhead">
              <button class="pl-back" @click="backToList">
                <el-icon :size="14"><ArrowLeft /></el-icon>
                <span>返回</span>
              </button>
              <span class="pl-subtitle ellipsis">{{ playlists.detail?.name || '歌单' }}</span>
            </div>
            <div class="pl-body">
              <div v-if="playlists.detailLoading" class="pl-loading">加载中…</div>
              <div v-else-if="!playlists.detail?.tracks?.length" class="pl-empty">歌单还是空的</div>
              <div v-else class="pl-tracks">
                <div
                  v-for="(track, idx) in playlists.detail.tracks"
                  :key="track.trackId"
                  class="pl-track"
                  :class="{ active: player.currentTrack?.trackId === track.trackId }"
                  @click="playDetail(idx)"
                >
                  <span class="pl-track-play">
                    <el-icon :size="14"><VideoPlay /></el-icon>
                  </span>
                  <span class="pl-track-main ellipsis">
                    <span class="pl-track-name ellipsis">{{ track.name }}</span>
                    <span class="pl-track-artist ellipsis">{{ track.artist || '未知艺人' }}</span>
                  </span>
                  <button
                    class="pl-track-remove"
                    aria-label="从歌单移除"
                    @click.stop="handleRemoveTrack(track.trackId)"
                  >
                    <el-icon :size="14"><Delete /></el-icon>
                  </button>
                </div>
              </div>
            </div>
          </template>

          <!-- 列表视图 -->
          <template v-else>
            <div class="pl-create">
              <input
                v-model="newName"
                class="pl-input"
                type="text"
                placeholder="新建歌单名称…"
                maxlength="40"
                @keyup.enter="handleCreate"
              />
              <button class="pl-create-btn" :disabled="creating" @click="handleCreate">
                <el-icon :size="14"><Plus /></el-icon>
                <span>新建</span>
              </button>
            </div>

            <div class="pl-body">
              <div v-if="playlists.loading" class="pl-loading">加载中…</div>
              <div v-else-if="!playlists.playlists.length" class="pl-empty">
                {{ mode === 'pick' ? '还没有歌单，点击上方新建一个' : '还没有歌单，先创建一个吧' }}
              </div>
              <div v-else class="pl-list">
                <template v-for="item in playlists.playlists" :key="item.id">
                  <!-- pick 模式：点击即加入 -->
                  <div
                    v-if="mode === 'pick'"
                    class="pl-item"
                    @click="handlePickTarget(item.id)"
                  >
                    <span class="pl-item-icon"><el-icon :size="15"><FolderOpened /></el-icon></span>
                    <span class="pl-item-name ellipsis">{{ item.name }}</span>
                    <span class="pl-item-count">{{ item.trackCount }} 首</span>
                    <span v-if="addingTrackId && addingTrackId === targetTrack?.trackId" class="pl-item-adding">加入中…</span>
                  </div>

                  <!-- manage 模式：行 + 操作 -->
                  <div v-else class="pl-item manage">
                    <span class="pl-item-icon" @click="openDetail(item.id)">
                      <el-icon :size="15"><FolderOpened /></el-icon>
                    </span>
                    <template v-if="editingId === item.id">
                      <input
                        v-model="editingName"
                        class="pl-input pl-rename-input"
                        type="text"
                        maxlength="40"
                        @keyup.enter="confirmRename"
                        @keyup.esc="editingId = null"
                      />
                      <button class="pl-item-op primary" @click="confirmRename">保存</button>
                    </template>
                    <template v-else>
                      <span class="pl-item-name ellipsis" @click="openDetail(item.id)">{{ item.name }}</span>
                      <span class="pl-item-count">{{ item.trackCount }} 首</span>
                      <button class="pl-item-op" title="播放歌单" @click="openDetail(item.id); playDetail(0)">
                        <el-icon :size="14"><VideoPlay /></el-icon>
                      </button>
                      <button class="pl-item-op" title="重命名" @click="startRename(item)">
                        <el-icon :size="14"><EditPen /></el-icon>
                      </button>
                      <button class="pl-item-op danger" title="删除歌单" @click="handleDelete(item)">
                        <el-icon :size="14"><Delete /></el-icon>
                      </button>
                    </template>
                  </div>
                </template>
              </div>
            </div>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.pl-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.pl-panel {
  width: 100%;
  max-width: 460px;
  max-height: min(76vh, 640px);
  display: flex;
  flex-direction: column;
  background: var(--jn-bg-elev);
  border: 1px solid var(--jn-hair);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.35);
}

.pl-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 16px 18px 12px;
  flex-shrink: 0;
}
.pl-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-family: 'Fraunces', serif;
  font-size: 17px;
  font-weight: 500;
  color: var(--jn-ink-strong);
}
.pl-close {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--jn-ink-dim);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.pl-close:hover { background: var(--jn-row-hover); color: var(--jn-ink-strong); }

.pl-subhead {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px 10px;
  flex-shrink: 0;
}
.pl-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--jn-hair);
  background: transparent;
  color: var(--jn-ink-dim);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  flex-shrink: 0;
}
.pl-back:hover { color: var(--jn-accent); border-color: var(--jn-accent); }
.pl-subtitle {
  font-size: 13px;
  color: var(--jn-ink);
}

.pl-create {
  display: flex;
  gap: 8px;
  padding: 0 18px 12px;
  flex-shrink: 0;
}
.pl-input {
  flex: 1;
  min-width: 0;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--jn-hair-strong);
  border-radius: 999px;
  background: var(--jn-input-bg);
  color: var(--jn-ink);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s;
}
.pl-input:focus { border-color: var(--jn-accent); }
.pl-create-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  background: var(--jn-accent);
  color: var(--jn-accent-ink);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.pl-create-btn:disabled { opacity: 0.5; cursor: wait; }

.pl-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 8px 12px;
  scrollbar-width: none;
}
.pl-body::-webkit-scrollbar { display: none; }

.pl-loading,
.pl-empty {
  padding: 40px 0;
  text-align: center;
  color: var(--jn-ink-muted);
  font-size: 13px;
}

.pl-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pl-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}
.pl-item:hover { background: var(--jn-row-hover); }
.pl-item.manage { cursor: default; }
.pl-item-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: var(--jn-accent);
  background: var(--jn-accent-soft);
  flex-shrink: 0;
}
.pl-item-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: var(--jn-ink);
  cursor: pointer;
}
.pl-item-count {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-muted);
  flex-shrink: 0;
}
.pl-item-adding { font-size: 11px; color: var(--jn-accent); flex-shrink: 0; }
.pl-item-op {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--jn-ink-dim);
  cursor: pointer;
  flex-shrink: 0;
}
.pl-item-op:hover { color: var(--jn-accent); background: var(--jn-accent-soft); }
.pl-item-op.danger:hover { color: var(--jn-danger); background: color-mix(in oklab, var(--jn-danger) 12%, transparent); }
.pl-item-op.primary {
  width: auto;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--jn-accent);
  color: var(--jn-accent-ink);
  font-size: 12px;
}
.pl-rename-input { flex: 1; min-width: 0; }

.pl-tracks {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.pl-track {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}
.pl-track:hover { background: var(--jn-row-hover); }
.pl-track.active { background: var(--jn-row-active); }
.pl-track.active .pl-track-name { color: var(--jn-accent); }
.pl-track-play {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: var(--jn-accent);
  background: var(--jn-accent-soft);
  flex-shrink: 0;
}
.pl-track-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.pl-track-name { font-size: 13.5px; color: var(--jn-ink); }
.pl-track-artist { font-size: 11.5px; color: var(--jn-ink-muted); }
.pl-track-remove {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--jn-ink-muted);
  cursor: pointer;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.pl-track-remove:hover { color: var(--jn-danger); background: color-mix(in oklab, var(--jn-danger) 12%, transparent); }

.ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.pl-overlay-enter-active,
.pl-overlay-leave-active { transition: opacity 0.2s ease; }
.pl-overlay-enter-active .pl-panel,
.pl-overlay-leave-active .pl-panel { transition: transform 0.25s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.2s ease; }
.pl-overlay-enter-from,
.pl-overlay-leave-to { opacity: 0; }
.pl-overlay-enter-from .pl-panel,
.pl-overlay-leave-to .pl-panel { transform: translateY(24px) scale(0.98); opacity: 0; }

@media (max-width: 720px) {
  .pl-overlay { align-items: flex-end; padding: 0; }
  .pl-panel {
    max-width: none;
    max-height: 78vh;
    border-radius: 16px 16px 0 0;
  }
}
</style>