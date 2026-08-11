# JNClub 便签 Markdown 导入导出设计

日期：2026-08-11
状态：已获用户确认（"可以"）

## 1. 背景与目标

为便签增加 Markdown 导入/导出，重点处理图片：导出时本地图片嵌 base64、导入时 data URI 落地为线上图片（走资产认领体系），UI 延续玻璃风格。

已确认决策：**导出图片=嵌入 base64**（外链 https 原样保留）；**导入 data URI=落地上传**（替换为 `/api/files/...`，外链保留）；**范围=单便签 + 目录全部**。

## 2. 现状（已探索确认）

- 本地图片 URL 格式：`![](/api/files/jnclub/images/yyyy/MM/dd/<uuid>.<ext>)`（相对路径，存 dufs）
- `/api/files/**` 公开无鉴权 + CORS 全开 → 客户端可 `fetch` 图片 blob
- 资产认领：后端正则 `!\[[^]]*]\(/api/files/([^)\s]+)\)` 从便签正文提取，保存时把 note_id 为 NULL 的未认领资产归给该便签；孤儿每晚清理
- 便签列表/详情 API 均返回完整 `content`
- 无任何现有导出/导入基础设施；md-editor-v3 无内置导出导入
- 项目无 jszip 等依赖（目录全部导出用多文件批量下载，不引 zip）

## 3. 设计

### 3.1 架构

纯前端实现，零后端改动、零新依赖。新增共享工具 + 两处 UI 接入。

### 3.2 共享工具 `jnclub-web/src/modules/bookmark/composables/markdownIO.ts`

| 函数 | 签名 | 说明 |
|---|---|---|
| `extractLocalImages(md)` | `(md: string) => string[]` | 正则提取 `/api/files/...` 图片 URL（与后端资产认领同款正则） |
| `extractDataUris(md)` | `(md: string) => string[]` | 正则提取 `data:image/...;base64,...` 完整 URI |
| `mdToBase64(url)` | `(url: string) => Promise<string>` | `fetch(url) → blob → FileReader → data URI`；失败抛错 |
| `downloadFile(name, content, mime)` | `(name: string, content: string, mime: string) => void` | Blob + `<a download>` 触发下载 |
| `dataUriToFile(uri)` | `(uri: string) => File \| null` | data URI → Blob → File（非图片返回 null） |

### 3.3 导出流程

1. 取便签 content（编辑器内取 `content.value`；列表导出从 store 取）
2. `extractLocalImages` 找 `/api/files/` 图片 → 逐个 `mdToBase64` 嵌入 `![](data:image/...;base64,...)`
3. 外链 `https://` 图片原样保留
4. fetch 失败的本地图保留原 URL 并 `message.warning` 提示
5. `downloadFile(\`${title}.md\`, md, 'text/markdown')`

### 3.4 导入流程

1. `<input type="file" accept=".md,.markdown">` 选文件，`FileReader` 读文本
2. `extractDataUris` 找 data URI 图片 → 逐个 `dataUriToFile` → `POST /api/upload/image` 上传 → 替换为返回的 `/api/files/...` URL（**走资产认领体系，可被孤儿清理**）
3. 外链 `https://` 与 `/api/files/...` URL 原样保留（JNClub 备份往返无损）
4. 非图片 data URI 跳过
5. 落点：
   - **编辑器导入**：替换当前 `content`，置 dirty 触发自动保存
   - **列表导入**：`POST /api/notes` 新建到当前目录（标题=文件名或正文首行）
6. 含图时 `message.loading('正在处理 N 张图片…')`，完成/失败提示

### 3.5 UI（玻璃风格）

- **编辑器 topbar**（NoteEditor.vue，与返回/帮助按钮同排）：【导出】【导入】玻璃按钮（Download/Upload 图标，`glass-ghost-btn` 风格）
- **便签列表 toolbar**（Home.vue notes 区）：【导出全部】玻璃按钮（当前目录全部便签逐个导出 `.md`，浏览器批量下载，零依赖）
- 导入 .md 用隐藏 `<input type="file">`（按钮触发）

### 3.6 边界处理

- 导出 fetch 失败：保留原 URL + warning 提示
- 导入空标题：自动用文件名（去扩展名）作标题
- data URI 非图片类型：跳过
- 目录导出空目录：toast 提示无便签

## 4. 验证

1. `cd jnclub-web && npx vue-tsc --noEmit` 零错误
2. `npm run build` 成功
3. `git push` → JNClub auto-deploy → 部署成功
4. 线上浏览器实测：
   - 导出含本地图便签 → 检查 .md 中为 data URI 且 `<img>` 可渲染
   - 导入含 data URI 图 .md → 图片落地为 `/api/files/...`、正文 URL 被替换、保存后可显示
   - 导入后资产认领（功能正常）
   - 外链图导入导出原样保留
   - 导出全部（多便签批量下载）、编辑器/列表两处 UI 正常

## 5. 不做（YAGNI）

- 不引 jszip（目录导出用多文件下载，非 zip 打包）
- 不做相对路径图片（./img/xxx.png）的文件夹导入
- 不做后端导出/导入端点
- 不做书签/密码等其他模块的导入导出
