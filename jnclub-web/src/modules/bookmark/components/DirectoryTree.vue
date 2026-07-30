<script setup lang="ts">
import { ref } from 'vue'
import { NTree, NButton, NIcon, NModal, NForm, NFormItem, NInput, NSpace, useMessage } from 'naive-ui'
import { AddOutline } from '@vicons/ionicons5'
import axios from 'axios'
import type { TreeOption } from 'naive-ui'

interface Directory {
  id: number
  parentId: number | null
  name: string
  sortOrder: number
  children?: Directory[]
}

const props = defineProps<{
  directories: Directory[]
  selectedId: number | null
}>()

const emit = defineEmits<{
  select: [id: number]
  refresh: []
}>()

const message = useMessage()

const showModal = ref(false)
const editingId = ref<number | null>(null)
const formValue = ref({ name: '' })

// 转换为树形结构
const treeData = (): TreeOption[] => {
  const map = new Map<number, TreeOption>()
  const roots: TreeOption[] = []
  
  // 先创建所有节点
  props.directories.forEach(dir => {
    map.set(dir.id, {
      key: dir.id,
      label: dir.name,
      children: [],
    })
  })
  
  // 构建树
  props.directories.forEach(dir => {
    const node = map.get(dir.id)!
    if (dir.parentId === null) {
      roots.push(node)
    } else {
      const parent = map.get(dir.parentId)
      if (parent) {
        if (!parent.children) parent.children = []
        parent.children.push(node)
      }
    }
  })
  
  return roots
}

const handleSelect = (keys: number[]) => {
  if (keys.length > 0) {
    emit('select', keys[0])
  }
}

const handleAdd = () => {
  editingId.value = null
  formValue.value = { name: '' }
  showModal.value = true
}

const handleSubmit = async () => {
  if (!formValue.value.name.trim()) {
    message.warning('请输入目录名称')
    return
  }
  
  try {
    if (editingId.value) {
      await axios.put(`/api/directories/${editingId.value}`, formValue.value)
      message.success('重命名成功')
    } else {
      await axios.post('/api/directories', {
        ...formValue.value,
        parentId: null,
      })
      message.success('创建成功')
    }
    showModal.value = false
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}
</script>

<template>
  <div class="directory-tree">
    <div style="margin-bottom: 12px;">
      <NButton size="small" @click="handleAdd">
        <template #icon>
          <NIcon :component="AddOutline" />
        </template>
        新建目录
      </NButton>
    </div>
    
    <NTree
      :data="treeData()"
      :selected-keys="selectedId ? [selectedId] : []"
      selectable
      default-expand-all
      @update:selected-keys="handleSelect"
    />
    
    <NModal v-model:show="showModal" preset="dialog" title="目录">
      <NForm :model="formValue">
        <NFormItem label="目录名称" path="name">
          <NInput v-model:value="formValue.name" placeholder="请输入目录名称" />
        </NFormItem>
      </NForm>
      <template #action>
        <NSpace>
          <NButton @click="showModal = false">取消</NButton>
          <NButton type="primary" @click="handleSubmit">确定</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.directory-tree {
  min-height: 300px;
}
</style>
