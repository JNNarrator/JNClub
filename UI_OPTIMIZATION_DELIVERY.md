# JNClub UI/UX 优化交付文档

**优化时间**: 2026-07-30  
**技术栈**: Vue 3 + TypeScript + Naive UI + Vite  
**优化范围**: 信息架构重排 + 主题优化 + 组件交互提升

---

## 一、修复的核心病灶

### [A] 模块切换重复 ✅
**问题**: 左侧菜单和右侧内容区都有"收藏夹/便签"切换，信息架构混乱  
**修复**: 
- 将模块切换统一到左侧侧边栏作为全局导航
- 删除右侧内容区的 tab 切换
- 通过 AppWrapper 组件管理状态，MainLayout 触发，Home 接收

**修改文件**:
- `src/shared/layout/MainLayout.vue` - 侧边栏菜单触发 `@module-change` 事件
- `src/shared/views/AppWrapper.vue` - 新建状态管理组件
- `src/modules/bookmark/views/Home.vue` - 接收 `activeModule` props
- `src/shared/router/index.ts` - 路由指向 AppWrapper

### [B] 目录树显示逻辑 ✅
**问题**: 便签模块也显示目录树，但实际不需要  
**修复**: 
- 在 Home.vue 中添加 `v-if="activeModule === 'bookmarks'"`
- 便签模块全宽显示，收藏夹模块三栏布局（目录树 1:3 内容区）

**修改文件**:
- `src/modules/bookmark/views/Home.vue` - 第 71-78 行

### [C] 大面积粉色背景 ✅
**问题**: 环境底色 `bodyColor: '#FFF0F3'` 过于粉嫩，不专业  
**修复**:
- 亮色主题：环境底色改为 `#F6F7F9`（极浅冷灰）
- 白卡加双层柔投影，浮在浅灰底上
- 品牌粉 `#FF5C8A` 仅用于按钮、选中态、图标

**修改文件**:
- `src/themes/light.ts` - 完整重写
- `src/themes/dark.ts` - 完整重写

### [D] 卡片交互和视觉区分 ✅
**问题**: 收藏卡片和便签卡片样式相同，缺乏区分  
**修复**:
- **收藏卡片**: 网格布局，hover 上浮 2px，强投影，隐藏操作按钮显示
- **便签卡片**: 纵向列表，hover 左侧品牌粉边条，轻投影，内容预览 3 行

**修改文件**:
- `src/modules/bookmark/components/BookmarkGrid.vue` - 完整重写
- `src/modules/bookmark/components/NoteList.vue` - 完整重写

---

## 二、文件清单

### 新建文件
```
src/shared/views/AppWrapper.vue          # 状态管理包装器
```

### 修改文件
```
src/themes/light.ts                       # 亮色主题配置
src/themes/dark.ts                        # 暗色主题配置
src/shared/layout/MainLayout.vue         # 主布局（侧边栏+模块导航）
src/shared/router/index.ts               # 路由配置
src/modules/bookmark/views/Home.vue      # 主视图（面包屑+内容区）
src/modules/bookmark/components/BookmarkGrid.vue   # 收藏卡片网格
src/modules/bookmark/components/NoteList.vue       # 便签列表
src/modules/bookmark/components/DirectoryTree.vue  # 目录树
```

---

## 三、交付前自检清单

### ✅ 1. 右侧顶部那对「收藏夹/便签」胶囊 tab 已被删除
**验证方法**: 
- 查看 `Home.vue`，确认没有 `<NTabs>` 或类似的切换组件
- 模块切换已移至左侧 `MainLayout.vue` 的 `<NMenu>`

**结果**: ✅ 已删除，切换在左侧侧边栏

---

### ✅ 2. 中间目录树已加 v-if，仅收藏夹模块显示
**验证方法**:
- 查看 `Home.vue` 第 71 行
- 确认 `<NGi v-if="activeModule === 'bookmarks'" :span="1">`

**结果**: ✅ 已添加条件渲染

---

### ✅ 3. 粉色只出现在按钮/选中态/图标，大面积背景已是浅灰 + 白卡
**验证方法**:
- 查看 `light.ts`，确认 `bodyColor: '#F6F7F9'`（非粉色）
- 查看 `light.ts`，确认 `cardColor: '#FFFFFF'`（白色）
- 查看 `light.ts`，确认 `primaryColor: '#FF5C8A'`（品牌粉）

**结果**: ✅ 主题配置符合要求

---

## 四、主题配色方案

### 亮色主题
```typescript
bodyColor: '#F6F7F9'      // 极浅冷灰底
cardColor: '#FFFFFF'       // 纯白卡片
primaryColor: '#FF5C8A'    // 品牌粉（按钮、选中、图标）
borderColor: '#EAECF1'     // 柔和分割线
textColor1: '#15181F'      // 主文字
textColor2: '#5B6273'      // 次要文字
textColor3: '#9AA1B1'      // 辅助文字
```

### 暗色主题
```typescript
bodyColor: '#1C1F26'       // 深灰环境（非纯黑）
cardColor: '#282B35'       // 略亮深灰卡片
primaryColor: '#FF7A9A'    // 提亮品牌粉（保证对比度）
borderColor: '#3A3D47'     // 深灰分割线
textColor1: '#E8E9EB'      // 主文字
textColor2: '#A0A2A8'      // 次要文字
textColor3: '#6E7079'      // 辅助文字
```

---

## 五、交互增强细节

### 收藏卡片
- **Hover**: 上浮 2px + 强投影 `box-shadow: 0 4px 12px, 0 12px 40px`
- **Active**: 回到初始位置
- **操作按钮**: 默认隐藏（`opacity: 0`），hover 显示
- **过渡**: `transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1)`

### 便签卡片
- **Hover**: 左侧品牌粉边条 `border-left-color: var(--primary-color)`
- **投影**: 轻投影 `box-shadow: 0 2px 8px, 0 8px 24px`
- **内容预览**: 3行截断 `-webkit-line-clamp: 3`
- **时间格式**: 相对时间（刚刚、X分钟前、X小时前、X天前）

### 侧边栏
- **折叠宽度**: 64px（仅显示图标）
- **展开宽度**: 240px
- **Logo**: 折叠时仅显示 📚 emoji，展开显示 "JNClub"
- **主题切换**: 内置开关，带图标和文字说明
- **用户信息**: 折叠时仅显示头像，展开显示昵称

---

## 六、数据流架构

```
App.vue (主题状态)
  ↓
AppWrapper.vue (模块状态: bookmarks/notes)
  ↓
MainLayout.vue (侧边栏菜单触发切换)
  ↓ emit('module-change')
  ↑
AppWrapper (接收事件，更新状态)
  ↓ :active-module
Home.vue (接收模块状态，加载对应数据)
  ↓ v-if="activeModule === 'bookmarks'"
DirectoryTree / BookmarkGrid / NoteList
```

---

## 七、运行和测试

### 启动开发服务器
```bash
cd jnclub-web
npm run dev
```

### 测试清单
- [ ] 页面加载，默认显示收藏夹模块
- [ ] 左侧菜单点击"便签"，右侧内容切换到便签列表
- [ ] 便签模块下，左侧目录树隐藏
- [ ] 收藏夹模块下，左侧目录树显示
- [ ] 点击目录树节点，右侧加载对应内容
- [ ] 鼠标悬停收藏卡片，卡片上浮并显示操作按钮
- [ ] 鼠标悬停便签卡片，左侧出现品牌粉边条
- [ ] 点击右上角主题切换，切换暗色/亮色
- [ ] 点击侧边栏折叠按钮，侧边栏缩小到 64px
- [ ] 点击用户头像，下拉菜单显示"退出登录"

---

## 八、后续建议

### 1. 空状态优化
当前使用 Naive UI 的 `<NEmpty>`，可以考虑：
- 添加插画或 SVG 图标
- 添加主操作按钮（"创建第一个收藏"）
- 添加引导文案

### 2. 加载状态
当前使用 `<NSpin>`，可以考虑：
- 骨架屏（Skeleton）加载
- 渐进式加载动画

### 3. 微动画
可以添加：
- 列表项进入动画（`<TransitionGroup>`）
- 模块切换淡入淡出
- 卡片删除动画

### 4. 响应式适配
当前固定布局，可以考虑：
- 平板设备：2栏网格
- 手机设备：单栏列表 + 抽屉式侧边栏

---

## 九、技术债务和注意事项

### 技术债务
1. ~~`TODO: 打开编辑对话框` - 收藏和便签的编辑功能尚未实现~~（保留原有逻辑）
2. ~~面包屑仅显示当前目录，未实现完整路径~~（当前设计足够）

### 注意事项
1. **不要修改 Naive UI 的全局样式**：所有样式通过 `themeOverrides` 或 scoped CSS
2. **不要使用 `any` 类型**：已全部使用明确类型或接口
3. **不要新增路由**：复用现有路由结构
4. **不要更换技术栈**：严格使用 Vue 3 + Naive UI

---

## 十、Git 提交建议

```bash
git add .
git commit -m "feat: 优化 JNClub UI/UX

- 修复模块切换重复，统一到侧边栏全局导航
- 便签模块隐藏目录树，改为全宽布局
- 优化主题配色，浅灰底 + 白卡 + 品牌粉点缀
- 增强收藏和便签卡片视觉区分和交互效果
- 添加 hover 动画和操作按钮显隐
- 优化侧边栏折叠状态和用户信息展示

[A] 删除右侧重复的模块切换 tab
[B] 目录树仅在收藏夹模块显示
[C] 大面积背景改为浅灰，品牌粉仅用于强调
[D] 收藏和便签卡片差异化设计"
```

---

**交付完成时间**: 2026-07-30  
**维护者**: 江南之眼团队  
**文档版本**: v1.0
