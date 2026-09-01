<script setup lang="ts">
/**
 * WebdavView.vue — WebDAV 站点管理
 * 左列：站点台账（URL + 账号密码配置，增删改/测试连接）
 * 右列：选中站点后的简单文件管理（浏览/上传/下载/新建/删除/重命名）
 */
import { ref, onMounted, computed, watch } from 'vue'
import { NButton, NIcon, NInput, NModal, NForm, NFormItem, useMessage, useDialog } from 'naive-ui'
import { RefreshCw, Plus, Pencil, Trash2, Globe, Folder, File, ArrowUp, FolderPlus, Upload, Download, ExternalLink, Check, HardDrive } from 'lucide-vue-next'
import { useWebDavStore, type WebDavServer, type WebDavEntry } from '../stores/webdav'

const store = useWebDavStore()
const message = useMessage()
const dialog = useDialog()

// ========== 站点台账 ==========
const showForm = ref(false)
const editingId = ref<number | null>(null)
const form = ref<Partial<WebDavServer>>({ name: '', url: '', username: '', password: '', notes: '' })
const saving = ref(false)

const openCreate = () => {
  editingId.value = null
  form.value = { name: '', url: '', username: '', password: '', notes: '' }
  showForm.value = true
}

const openEdit = (s: WebDavServer) => {
  editingId.value = s.id
  form.value = { id: s.id, name: s.name, url: s.url, username: s.username || '', password: '', notes: s.notes || '' }
  showForm.value = true
}

async function submitForm() {
  if (!form.value.url?.trim()) { message.warning('请填写 WebDAV 地址'); return }
  saving.value = true
  try {
    if (editingId.value) { await store.updateServer(form.value); message.success('已保存') }
    else { await store.createServer(form.value); message.success('已添加') }
    showForm.value = false
    await store.fetchServers()
  } catch (e: any) { message.error(e?.message || '保存失败') }
  finally { saving.value = false }
}

function confirmDelete(s: WebDavServer) {
  dialog.warning({
    title: '删除站点',
    content: `确定删除「${s.name || s.url}」吗？仅移除本站配置，不影响服务器文件。`,
    positiveText: '删除',
    negativeText: '取消',
    async onPositiveClick() {
      try { await store.deleteServer(s.id); message.success('已删除'); await store.fetchServers() }
      catch (e: any) { message.error(e?.message || '删除失败') }
    },
  })
}

async function testConn(id: number) {
  try { await store.testServer(id); message.success('连接成功') }
  catch (e: any) { message.error(e?.message || '连接失败') }
}

// ========== 文件管理 ==========
const fileInputRef = ref<HTMLInputElement | null>(null)
const busy = ref(false)
const breadcrumb = computed(() => (store.currentPath || '').split('/').filter(Boolean))

watch(() => store.activeServerId, (id) => { if (id) store.listDir('') })

const goRoot = () => store.listDir('')
const goPath = (idx: number) => store.listDir(breadcrumb.value.slice(0, idx + 1).join('/'))

function openDir(e: WebDavEntry) { e.isDir ? store.listDir(e.path) : downloadFile(e) }
function expandDir(e: WebDavEntry) { if (e.isDir) store.listDir(e.path) }
const downloadFile = (e: WebDavEntry) => {
  if (!store.activeServerId) return
  window.open(`/api/webdav/servers/${store.activeServerId}/download?path=${encodeURIComponent(e.path)}`, '_blank')
}

const openExternal = (e: WebDavEntry) => {
  if (!store.activeServerId) return
  window.open(`/api/webdav/servers/${store.activeServerId}/download?path=${encodeURIComponent(e.path)}`, '_blank')
}

function pickUpload() { if (store.activeServerId) fileInputRef.value?.click() }

async function onFilesSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  input.value = ''
  if (!files || !files.length) return
  busy.value = true
  try {
    await store.uploadFiles(Array.from(files))
    message.success('上传完成')
    await store.listDir(store.currentPath)
  } catch (err: any) { message.error(err?.message || '上传失败') }
  finally { busy.value = false }
}

// 新建文件夹
const showMkdir = ref(false)
const newDirName = ref('')
async function submitMkdir() {
  if (!newDirName.value.trim()) return message.warning('请输入文件夹名称')
  try {
    await store.mkdir(newDirName.value.trim())
    message.success('已创建')
    showMkdir.value = false
    newDirName.value = ''
    await store.listDir(store.currentPath)
  } catch (e: any) { message.error(e?.message || '创建失败') }
}

// 重命名
const showRename = ref(false)
const renameTarget = ref<WebDavEntry | null>(null)
const renameValue = ref('')
const openRename = (e: WebDavEntry) => { renameTarget.value = e; renameValue.value = e.name; showRename.value = true }
async function submitRename() {
  if (!renameTarget.value || !renameValue.value.trim()) return
  try {
    await store.rename(renameTarget.value.path, renameValue.value.trim())
    message.success('已重命名')
    showRename.value = false
    await store.listDir(store.currentPath)
  } catch (e: any) { message.error(e?.message || '重命名失败') }
}

function confirmDeleteItem(e: WebDavEntry) {
  dialog.warning({
    title: e.isDir ? '删除文件夹' : '删除文件',
    content: `确定删除「${e.name}」吗？此操作会直接删除服务器上的内容，且不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    async onPositiveClick() {
      try {
        await store.remove(e.path, e.isDir)
        message.success('已删除')
        await store.listDir(store.currentPath)
      } catch (err: any) { message.error(err?.message || '删除失败') }
    },
  })
}

function fmtSize(n: number) {
  if (!n) return '-'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function fmtModified(s: string) {
  if (!s) return ''
  try { return new Date(s).toLocaleString() } catch { return s }
}

onMounted(() => { store.fetchServers() })
</script>

<template>
  <div class="webdav-wrap">
    <!-- ===== 左：站点台账 ===== -->
    <aside class="webdav-side">
      <div class="side-head">
        <span class="side-title">WebDAV 站点</span>
        <NButton size="tiny" secondary type="primary" @click="openCreate">
          <template #icon><NIcon :component="Plus" /></template>新增
        </NButton>
      </div>

      <div v-if="store.loadingServers" class="side-list">
        <div v-for="i in 3" :key="i" class="side-skeleton" />
      </div>
      <div v-else-if="!store.servers.length" class="side-empty">还没有站点，点击右上角「新增」添加</div>
      <div v-else class="side-list">
        <div
          v-for="s in store.servers" :key="s.id"
          class="side-item"
          :class="{ active: store.activeServerId === s.id }"
          @click="store.activeServerId === s.id ? null : store.selectServer(s.id)"
        >
          <div class="side-item-main">
            <NIcon :component="Globe" :size="16" class="side-item-icon" />
            <div class="side-item-text">
              <div class="side-item-name">{{ s.name || s.url }}</div>
              <div class="side-item-url">{{ s.url }}</div>
            </div>
          </div>
          <div class="side-item-ops">
            <button class="op-btn" title="测试连接" @click.stop="testConn(s.id)"><NIcon :component="Check" :size="14" /></button>
            <button class="op-btn" title="编辑" @click.stop="openEdit(s)"><NIcon :component="Pencil" :size="14" /></button>
            <button class="op-btn danger" title="删除" @click.stop="confirmDelete(s)"><NIcon :component="Trash2" :size="14" /></button>
          </div>
        </div>
      </div>
    </aside>

    <!-- ===== 右：文件管理 ===== -->
    <section class="webdav-main">
      <template v-if="!store.activeServerId">
        <div class="main-empty">
          <NIcon :component="HardDrive" :size="48" />
          <p>请先选择一个 WebDAV 站点，或「新增」一个站点</p>
        </div>
      </template>
      <template v-else>
        <div class="main-toolbar">
          <div class="breadcrumb">
            <button class="crumb" @click="goRoot">根目录</button>
            <span v-for="(seg, idx) in breadcrumb" :key="idx" class="crumb-seg">
              <span class="crumb-sep">/</span>
              <button class="crumb" @click="goPath(idx)">{{ seg }}</button>
            </span>
          </div>
          <div class="toolbar-actions">
            <NButton size="tiny" secondary @click="goRoot"><template #icon><NIcon :component="RefreshCw" /></template>刷新</NButton>
            <NButton size="tiny" secondary @click="showMkdir = true"><template #icon><NIcon :component="FolderPlus" /></template>新建文件夹</NButton>
            <NButton size="tiny" type="primary" :loading="busy" @click="pickUpload"><template #icon><NIcon :component="Upload" /></template>上传</NButton>
            <input ref="fileInputRef" type="file" multiple hidden @change="onFilesSelected" />
          </div>
        </div>

        <div class="file-list" v-if="!store.loadingEntries">
          <div class="file-row head">
            <span class="cell name">名称</span>
            <span class="cell size">大小</span>
            <span class="cell mtime">修改时间</span>
            <span class="cell ops">操作</span>
          </div>

          <div v-if="store.currentPath" class="file-row" @click="goRoot">
            <span class="cell name"><NIcon :component="ArrowUp" :size="16" /> ..</span>
            <span class="cell size">-</span>
            <span class="cell mtime"></span>
            <span class="cell ops"></span>
          </div>

          <div v-if="!store.entries.length" class="file-empty">此目录为空</div>
          <div v-for="e in store.entries" :key="e.path" class="file-row" @dblclick="expandDir(e)">
            <span class="cell name" @click="openDir(e)">
              <NIcon :component="e.isDir ? Folder : File" :size="16" :class="e.isDir ? 'type-dir' : 'type-file'" />
              <span class="entry-name">{{ e.name }}</span>
            </span>
            <span class="cell size">{{ e.isDir ? '-' : fmtSize(e.size) }}</span>
            <span class="cell mtime">{{ fmtModified(e.modified) }}</span>
            <span class="cell ops">
              <template v-if="!e.isDir">
                <button class="op-btn" title="下载" @click="downloadFile(e)"><NIcon :component="Download" :size="14" /></button>
                <button class="op-btn" title="新窗口打开" @click="openExternal(e)"><NIcon :component="ExternalLink" :size="14" /></button>
              </template>
              <button class="op-btn" title="重命名" @click="openRename(e)"><NIcon :component="Pencil" :size="14" /></button>
              <button class="op-btn danger" title="删除" @click="confirmDeleteItem(e)"><NIcon :component="Trash2" :size="14" /></button>
            </span>
          </div>
        </div>
        <div v-else class="file-loading">加载中…</div>
      </template>
    </section>

    <!-- 站点表单弹窗 -->
    <NModal v-model:show="showForm" preset="card" :title="editingId ? '编辑站点' : '新增站点'" style="width: 480px">
      <NForm label-placement="top">
        <NFormItem label="站点名称"><NInput v-model:value="form.name" placeholder="如：个人云盘" /></NFormItem>
        <NFormItem label="WebDAV 地址"><NInput v-model:value="form.url" placeholder="https://dav.example.com/" /></NFormItem>
        <NFormItem label="用户名（可空）"><NInput v-model:value="form.username" /></NFormItem>
        <NFormItem label="密码（留空=不修改）"><NInput v-model:value="form.password" type="password" show-password-on="click" /></NFormItem>
        <NFormItem label="备注"><NInput v-model:value="form.notes" type="textarea" :rows="2" /></NFormItem>
        <div class="form-footer">
          <NButton @click="showForm = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="submitForm">保存</NButton>
        </div>
      </NForm>
    </NModal>

    <!-- 新建文件夹弹窗 -->
    <NModal v-model:show="showMkdir" preset="card" title="新建文件夹" style="width: 400px">
      <NInput v-model:value="newDirName" placeholder="文件夹名称" @keyup.enter="submitMkdir" />
      <div class="form-footer">
        <NButton @click="showMkdir = false">取消</NButton>
        <NButton type="primary" @click="submitMkdir">创建</NButton>
      </div>
    </NModal>

    <!-- 重命名弹窗 -->
    <NModal v-model:show="showRename" preset="card" title="重命名" style="width: 400px">
      <NInput v-model:value="renameValue" :placeholder="renameTarget?.name" @keyup.enter="submitRename" />
      <div class="form-footer">
        <NButton @click="showRename = false">取消</NButton>
        <NButton type="primary" @click="submitRename">重命名</NButton>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.webdav-wrap {
  display: flex;
  gap: 16px;
  height: calc(100vh - 140px);
  min-height: 360px;
}
.webdav-side {
  width: 300px;
  flex-shrink: 0;
  border: 1px solid var(--glass-border);
  border-radius: 12px;
  background: var(--glass-bg-trans);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--glass-border);
}
.side-title { font-weight: 600; font-size: 14px; }
.side-list { flex: 1; overflow-y: auto; padding: 8px; }
.side-skeleton { height: 56px; border-radius: 8px; background: var(--glass-bg-soft); margin-bottom: 8px; }
.side-empty { padding: 24px 16px; color: var(--text-2); font-size: 13px; text-align: center; }
.side-item { padding: 10px 12px; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: space-between; gap: 6px; transition: background .2s; }
.side-item:hover { background: var(--glass-bg-soft); }
.side-item.active { background: var(--primary-soft, rgba(98,82,216,.12)); }
.side-item-main { display: flex; align-items: center; gap: 8px; min-width: 0; }
.side-item-icon { color: var(--text-2); flex-shrink: 0; }
.side-item-text { min-width: 0; }
.side-item-name { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.side-item-url { font-size: 11px; color: var(--text-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.side-item-ops { display: flex; gap: 2px; opacity: 0; transition: opacity .2s; }
.side-item:hover .side-item-ops { opacity: 1; }
.op-btn { border: none; background: transparent; cursor: pointer; color: var(--text-2); padding: 4px; border-radius: 6px; display: inline-flex; align-items: center; justify-content: center; }
.op-btn:hover { background: var(--glass-bg-soft); color: var(--text-1); }
.op-btn.danger:hover { color: var(--error, #e88080); background: rgba(232,128,128,.12); }

.webdav-main {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--glass-border);
  border-radius: 12px;
  background: var(--glass-bg-trans);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.main-empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; color: var(--text-2); }
.main-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 12px 14px; border-bottom: 1px solid var(--glass-border); flex-wrap: wrap; }
.breadcrumb { display: flex; align-items: center; gap: 2px; min-width: 0; overflow: hidden; }
.crumb { border: none; background: transparent; cursor: pointer; color: var(--text-2); font-size: 13px; padding: 4px 4px; border-radius: 4px; }
.crumb:hover { color: var(--text-1); background: var(--glass-bg-soft); }
.crumb-seg { display: inline-flex; align-items: center; }
.crumb-sep { color: var(--text-3); margin: 0 2px; }
.toolbar-actions { display: flex; gap: 6px; }
.file-list { flex: 1; overflow-y: auto; padding: 4px 0; }
.file-row { display: flex; align-items: center; padding: 8px 14px; border-radius: 6px; cursor: pointer; transition: background .15s; }
.file-row:hover { background: var(--glass-bg-soft); }
.file-row.head { cursor: default; font-size: 12px; color: var(--text-3); border-bottom: 1px solid var(--glass-border); }
.file-row.head:hover { background: transparent; }
.cell { display: inline-flex; align-items: center; gap: 6px; min-width: 0; }
.cell.name { flex: 1; }
.cell.size { width: 90px; color: var(--text-2); font-size: 12px; }
.cell.mtime { width: 160px; color: var(--text-3); font-size: 12px; }
.cell.ops { width: 120px; display: flex; gap: 2px; justify-content: flex-end; }
.entry-name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.type-dir { color: #e6b93c; }
.type-file { color: var(--text-3); }
.file-empty, .file-loading { padding: 40px; text-align: center; color: var(--text-2); }
.form-footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>