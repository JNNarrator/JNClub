# JNClub 收藏助手（Chrome 扩展）

一键收藏当前网页、批量收藏打开的标签页到 JNClub 个人工作台；支持稍后读、网页快照与保存去重提示。

## 功能

- **当前页一键收藏**：点工具栏图标 → 自动带出当前页标题/网址 → 选目录 → 保存
- **存为稍后读**：保存时勾选「存为稍后读」，或右键 → 「收藏为稍后读」，收藏后可在 JNClub 稍后读列表查看
- **保存网页快照**：保存时勾选「保存网页快照」，收藏成功后自动触发服务端快照抓取
- **保存去重提示**：保存前比对所选目录，若该链接已收藏会询问是否继续
- **批量收藏标签页**：列出全部打开的标签页，勾选批量收藏，已收藏的自动标记去重
- **右键菜单收藏**：任意网页右键 → 「收藏到 JNClub」→ 收藏到默认目录 + 桌面通知
- **网页转便签**：当前页提取正文转 Markdown 便签（右键或弹窗入口）
- **服务器可配置**：设置页可指向任意 JNClub 后端地址

## 安装（开发者模式加载）

1. **下载**：JNClub 工作台 → `/extension` 页面下载 `jnclub-extension-vX.Y.Z.zip`
2. **解压**：解压到一个固定目录（如 `~/Extensions/jnclub-extension`），不要删除
3. 浏览器地址栏打开 `chrome://extensions`
4. 右上角开启 **开发者模式**
5. 点 **「加载已解压的扩展程序」**，选择解压后的目录
6. 工具栏出现 JNClub 图标，点击 → 「登录 JNClub」→ 浏览器打开登录页 → 登录成功后回来自动同步登录态

## 首次使用

1. 点击工具栏 JNClub 图标
2. 点「登录 JNClub」（会打开 JNClub 登录页，走既有 SSO 单点登录）
3. 登录成功回到弹窗，即可一键收藏当前页
4. 需要时到设置页（⚙）修改服务器地址 / 退出登录

## 开发调试

```bash
# 重新打包 zip（产物到 jnclub-web/public/extension/）
./scripts/build-extension.sh
```

改动代码后在 `chrome://extensions` 点扩展的「刷新」按钮重新加载。

## 发布到 Chrome Web Store（可选）

- manifest 已含完整 name/version/description/icons/permissions
- 上架前需把 `host_permissions` 从 `<all_urls>` 收窄为 JNClub 域名（如 `https://your-domain/*`）
- 注册 Chrome 开发者账号（$5 一次性）→ 上传 zip → 审核
