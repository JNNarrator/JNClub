# JNClub - 个人工作台服务

JNClub 是一个前后端分离的个人工作台 Web 服务，本次实现的是「收藏与便签」模块。

## 技术栈

### 后端
- Spring Boot 4.0.0 (JDK 21)
- MySQL 8.x
- MyBatis-Plus
- Sa-Token SSO

### 前端
- Vue 3 + TypeScript + Vite
- Naive UI
- Pinia
- md-editor-v3

## 项目结构

```
JNClub/
├── pom.xml                    # 父 POM
├── jnclub-common/            # 公共模块
├── jnclub-module-bookmark/   # 收藏与便签模块
├── jnclub-gateway/           # API 网关
├── jnclub-web/               # 前端项目
└── docs/                     # 文档
```

## 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < docs/init.sql
```

### 2. 启动后端

```bash
# 在 JNClub 目录下
mvn clean install
cd jnclub-gateway
mvn spring-boot:run
```

后端将在 http://localhost:19005 启动

### 3. 启动前端

```bash
cd jnclub-web
npm install
npm run dev
```

前端将在 http://localhost:5173 启动

## SSO 配置

本项目使用 JN_SSO 进行单点登录。启动前需要在 SSO 数据库中注册应用：

```sql
INSERT INTO jn_sso.sso_client_app (app_name, app_code, redirect_url, homepage_url, type, status)
VALUES ('JNClub', 'app-jnclub', 'http://localhost:19005/sso/login', 'http://localhost:5173', 'web', 1);
```

## 功能特性

- ✅ 目录管理（二级目录、CRUD、拖拽排序、级联删除）
- ✅ 网页收藏（CRUD、Favicon 自动提取、拖拽排序）
- ✅ 便签（CRUD、Markdown 编辑、图片上传、拖拽排序）
- ✅ SSO 单点登录
- ✅ 日/夜间模式（跟随系统）
- ✅ 空状态插图

## 配置说明

后端配置文件：`jnclub-gateway/src/main/resources/application.yml`

```yaml
server:
  port: 19005

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/jnclub?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: jiangnan123

sa-token:
  sso:
    mode: client
    server-url: http://localhost:8080/sso   # 本地联调；生产环境为 https://jiangnan.88933.vip
    client-url: http://localhost:19005
    client-id: app-jnclub
    secret-key: jn-sso-secret-key-2026
  token-name: jn-token

jnclub:
  dufs:
    base-url: http://localhost:8000
    upload-path: /jnclub/images/
```

## API 文档

### 认证
- `GET /sso/login` - SSO 回调端点
- `GET /api/auth/userinfo` - 获取当前用户信息
- `POST /api/auth/logout` - 登出

### 目录
- `GET /api/directories` - 获取目录树
- `POST /api/directories` - 创建目录
- `PUT /api/directories/{id}` - 重命名
- `DELETE /api/directories/{id}` - 删除（级联）
- `PUT /api/directories/sort` - 批量排序

### 收藏
- `GET /api/bookmarks?directoryId=` - 获取列表
- `POST /api/bookmarks` - 添加（自动提取icon）
- `PUT /api/bookmarks/{id}` - 编辑
- `DELETE /api/bookmarks/{id}` - 删除
- `PUT /api/bookmarks/sort` - 批量排序

### 便签
- `GET /api/notes?directoryId=` - 获取列表
- `GET /api/notes/{id}` - 获取详情
- `POST /api/notes` - 新建
- `PUT /api/notes/{id}` - 编辑
- `DELETE /api/notes/{id}` - 删除
- `PUT /api/notes/sort` - 批量排序

### 文件上传
- `POST /api/upload/image` - 上传图片到 dufs

## 许可证

MIT
