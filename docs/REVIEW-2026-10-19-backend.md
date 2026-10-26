# JNClub 后端技术评审：还能优化什么

> 评审对象：`/Users/jiangnan/Documents/workspace/JNClub` 后端（`jnclub-common` / `jnclub-gateway` / `jnclub-module-bookmark` / `jnclub-module-music`）
> 评审方式：全量代码阅读 + 生产环境只读核验（gm 服务器，2026-10-19）
> 约束：**本文只做诊断与建议，未修改任何业务代码、未执行部署、未做任何数据库写操作**
> 只读核验范围：`SELECT` / `SHOW INDEX` / `information_schema` 查询、`ps` / `ss` / `systemctl` / `journalctl`、`curl` 只读 GET（认证探测与目录列举，未做任何上传/删除/修改）

---

## 0. 先看数据基线（重要：它决定了优先级）

评审开始时的原假设是"中型生产系统"，**实测证伪**。生产库（`jnclub`，MySQL 8.0.46）实际规模：

| 表 | 行数 | 表 | 行数 |
|---|---:|---|---:|
| `t_bookmark` | 20 | `music_track` | 36 |
| `t_note` | 3 | `music_lyrics_cache` | 22 |
| `t_directory` | 8 | `music_play_history` | 12 |
| `t_user_preference` | 21 | `music_play_queue` | 4 |
| `t_share` | 2 | `music_playlist` / `_item` | 0 / 0 |
| `t_vault` / `_meta` | 2 / 2 | `music_user_favorite` | 0 |
| `sa_token_data` | 18 | `t_file` / `t_note_asset` | 0 / 0 |

配套实测：

- JVM：`-Xmx1g -XX:MaxMetaspaceSize=320m`，常驻 475 MB，systemd 单实例 `User=root`
- MySQL：`max_connections=151`、`innodb_buffer_pool_size=128MB`、**`slow_query_log=0`（慢查询日志关闭）**、`long_query_time=10s`
- MySQL 状态：`Threads_connected=21`、`Max_used_connections=21`、`Slow_queries=0`、`Innodb_buffer_pool_reads/read_requests≈0.12%`（缓冲池命中良好）
- nginx 1.24.0：`client_max_body_size 200M`、`proxy_read_timeout 3600s`（两处）、`proxy_buffering off`、**无 `limit_req` / `limit_conn`**
- 无 APM、无 Actuator 暴露（`/actuator*` 全部 404）、无 Prometheus、无压测数据
- 文件服务 dufs：`serve-path=/home/jiangnan/file_server`、`port=5000`、`allow-all: true`、`enable-cors: true`、`auth: [jiangnan:******@/:rw]`；公网经 `jn_file.88933.vip` 反代
- `ufw status` = **inactive**（无主机防火墙，仅依赖云安全组）

**这直接推翻了原报告里"批量写 N+1 很痛"的定级**：20 条收藏循环 20 次 `updateById` 的实际耗时是毫秒级。因此本文把所有"随数据量增长才恶化"的问题统一降到 P2，并把"与数据量无关、随时会咬人"的问题提到 P0/P1。

### 假设声明（缺少信息时的推断，请按需修正）

1. **单实例部署**（systemd 单进程，未上多节点）——依据：`systemctl cat jnclub` 只有一个 `ExecStart`，无容器化、无 LB 配置。若未来多实例，P0-6（Vault 内存会话）与 P2-6（定时任务）会升级。
2. **单租户/少数用户**——依据：`t_bookmark` 20 行、`t_user_preference` 21 行，`user_id` 基数极小。
3. **文件服务是唯一对象存储**——依据：`jnclub.dufs.*` 是唯一文件配置，`file_server` 总大小 488 KB。
4. **无外部攻击面监控**——依据：无 WAF 日志、`ufw inactive`、nginx 无限流。

---

## 1. 结论速览

| 级别 | 数量 | 一句话 |
|---|---:|---|
| **P0** | 8 | 6 条是"匿名/弱口令即可读写或提权"，2 条是"加密实现不安全" |
| **P1** | 11 | 异常体系、日志泄露、会话表膨胀、配置失效、可观测性缺口 |
| **P2** | 12 | 批量写/全表 LIKE/内存分页等——**当前数据量下不必做** |

**如果本周只做一件事**：改掉 dufs 的弱口令并收回 `allow-all`（P0-1）。它是当前唯一"公网 + 弱口令 + 可写 + 密码与数据库 root 复用"的入口，一次命中即可拿到全部用户文件，且同一口令可进一步尝试数据库。

---

## 2. P0（严重，建议 1–2 周内处置）

### P0-1 文件服务公网弱口令 + 可写 WebDAV（生产实测确认）

**位置**
- `jnclub-gateway/src/main/resources/application.yml:35-40`（`dufs.base-url` / `public-url` / `username` / `password`）
- `/etc/dufs.conf`（服务器：`allow-all: true`、`auth: [jiangnan:******@/:rw]`、`enable-cors: true`）
- `jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/controller/FileProxyController.java:45-48`（后端用同一组凭据代访问）

**证据（实测，只读）**

```
$ curl -o /dev/null -w "%{http_code}" http://jn_file.88933.vip/
401
$ curl -o /dev/null -w "%{http_code}" -u jiangnan:****** http://jn_file.88933.vip/
200
$ curl -u jiangnan:****** "http://jn_file.88933.vip/?json"
{"kind":"Index","allow_upload":true,"allow_delete":true,"allow_search":true,"allow_archive":true,...}
```

响应头同时暴露：

```
Allow: GET,HEAD,PUT,OPTIONS,DELETE,PATCH,PROPFIND,COPY,MOVE,CHECKAUTH,LOGOUT
Dav: 1, 2, 3
Access-Control-Allow-Origin: *
Access-Control-Allow-Credentials: true
```

**影响**
- 口令为 **8 位弱口令，且与 MySQL `root` 密码、蓝奏云账号密码复用**（`application.yml:8`、`:75`、`:40` 三处同口令）。一次泄露同时击穿：全部用户上传文件（便签附件、云盘文件、头像）+ 数据库 + 音乐源站。
- dufs 允许 `PUT/DELETE/MOVE/COPY` 与 `allow_archive`（整目录打包下载），即**可读取、可篡改、可清空**。
- `enable-cors: true` 输出 `ACAO: *` 且 `Allow-Credentials: true`，浏览器虽会因 `*` 与 credentials 冲突而忽略凭据，但服务端并未限制来源，配合 WebDAV 客户端可被任意站点驱动。
- `/home/jiangnan/file_server/` 下还有 `jai-config*.json`、`jai-e2e-*` 等非 JNClub 文件，属于**跨项目共用目录**，爆炸半径超出本系统。

**建议（按顺序）**
1. 立即改 dufs 口令为 32 位随机串，与 DB/蓝奏云**彻底不同**；`auth` 改为独立账号。
2. `allow-all: true` → 删除；保留 `@/:rw` 但仅限该账号；若无需公网写入，改为 `@/:r` 并另建受限上传账号。
3. 关闭 `enable-cors`（同源访问不需要；跨域访问走 nginx 精确白名单）。
4. 架构上让 dufs **只监听 `127.0.0.1` 或内网地址**，公网只保留后端 `/api/files` 代理 + 鉴权；`jn_file.88933.vip` 若必须保留，前置 nginx `auth_request` 或加 IP 白名单。
5. 轮换全部复用口令（DB root、蓝奏云、服务器 root），并核查历史泄露面（访问日志、`.git`、备份、聊天记录）。

**工作量 / 风险**：0.5 人日；**风险低**（改配置+重启 dufs），但**必须做**——这是当前最高 ROI 的一条。

---

### P0-2 管理端点匿名可达：可覆盖服务端蓝奏云会话

**位置**
- `jnclub-module-music/src/main/java/com/jnclub/music/admin/controller/AdminLanzouController.java:28`（`@RequestMapping("/api/v1/admin/lanzou")`，全类无鉴权注解）
- 放行来源：`jnclub-common/src/main/java/com/jnclub/common/config/WebMvcConfig.java:24`（`"/api/v1/**"`）
- 危险方法：`:59-72` `POST /cookie`、`:74-94` `POST /login`、`:100-108` `POST /refresh-cache`

**证据**

```java
@RequestMapping("/api/v1/admin/lanzou")   // :28
public class AdminLanzouController {
    @PostMapping("/cookie")
    public ApiResponse<?> setCookie(@RequestBody CookieRequest req) {
        lanzouClient.setSessionCookie(req.cookie().trim());   // :64
        lanzouClient.saveCookieCache();                        // :65
    }
    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody LoginRequest req) {
        lanzouClient.login(req.username(), req.password());    // :80
    }
```

配合 `WebMvcConfig.java:24` 的 `"/api/v1/**"` 放行，等价于：

```
curl -X POST http://<host>/music/api/v1/admin/lanzou/cookie -d '{"cookie":"<攻击者会话>"}'
```

**影响**
- 匿名者可用自己的蓝奏云会话**替换服务端全局会话**，之后所有用户的播放直链都指向攻击者账号（流量劫持/内容替换）。
- `POST /login` 可用任意账号覆盖服务端凭据；`POST /refresh-cache` 可反复触发全量刷新（放大上游压力、触发蓝奏云风控）。
- `:88` 日志记录 `username`（`log.info("蓝奏云登录: username={}...")`），登录凭据进入日志。

**说明**：你已说明"音乐模块暂时公开匿名"。但"听歌匿名"不等于"匿名可改服务端凭据"——该控制器是**运维动作**，不属于对访客开放的业务接口。这条与产品定位无关。

**建议**
1. 类级加 `StpUtil.checkLogin()`，并在路由上收紧为 `/api/v1/admin/**` 显式前缀（避免继续被 `/**` 通配覆盖）。
2. 退一步至少加 `X-Admin-Token`（环境变量注入）比对 + 调用频率限制。
3. `:88` 日志改为只记 `userId` 或打码手机号。

**工作量 / 风险**：0.5 人日；风险低（需同步检查管理端前端是否依赖匿名调用）。

---

### P0-3 文件代理：匿名 + 路径直接拼接 + 无超时

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/controller/FileProxyController.java:25`（`/api/files`）、`:37`（`/**`）、`:40-43`、`:45`

**证据**

```java
String uri = request.getRequestURI();                      // :40
String path = uri.substring("/api/files/".length());       // :41
String dufsUrl = dufsBaseUrl + "/" + path;                 // :43
var req = HttpRequest.get(dufsUrl);                        // :45  ← 无 timeout
```

放行依据：`WebMvcConfig.java:23` 的 `"/api/files/**"`。

**影响**
- `path` 未做 `normalize`、未校验是否落在期望前缀内：`/api/files/..%2f..%2f` 类请求具备穿越条件（**代码层结论，未实施利用验证**）。爆炸半径受 dufs 根限制，但该根下就是**全部用户文件**（见 P0-1）。
- 匿名可遍历：即便不知道文件名，dufs 的目录结构可枚举，配合 `?json` 可直接列目录（P0-1 已证实）。
- hutool `HttpRequest` 默认**无超时**，dufs 卡住时请求线程永久占用，随后连接池耗尽。

**建议**
1. `path = Paths.get(path).normalize().toString()`，并断言结果以 `jnclub/images/` 开头（仅允许本系统目录前缀）。
2. 该端点改为**必须登录**（`StpUtil.checkLogin()`）；若需匿名分享，走 `ShareController` 的签名 token，而不是裸路径。
3. 显式超时：`HttpRequest.get(url).timeout(5000)`；并复用 `FileProxyController` 已有的 8KB 流式拷贝（这部分实现是对的）。
4. 关闭目录列举：`?json` 请求直接 403（服务端判断 `path` 结尾是否为 `/`）。

**工作量 / 风险**：0.5 人日；风险中（需同步确认前端图片 URL 是否依赖匿名访问）。

---

### P0-4 分片上传 `uploadId` 未校验 → 本地路径穿越

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/CloudDiskService.java:653-663`

**证据**

```java
private Path uploadDir(String userId, String uploadId) {
    return Paths.get(tempDirBase).resolve(userId).resolve(uploadId);   // :653-655
}
private Path chunksDir(String uploadId) {
    return Paths.get(tempDirBase).resolve(userId()).resolve(uploadId).resolve("chunks");  // :658-660
}
```

`uploadId` 来自请求（`saveChunk` / `getUploadStatus` / `complete` 均直接透传），`initUpload` 只在**创建时**用 UUID 生成，读取时未做格式校验。

**影响**：传 `uploadId=../../../../etc` 即可让 `Files.write` / `Files.move` 写向任意可写目录；`complete` 阶段的 `Files.delete` 还能删目录。虽然实际利用需要先构造 meta 文件，但结合 P0-3 的匿名读可先探测路径。

**建议**：在 Controller 边界统一校验 `uploadId` 必须匹配 `^[a-f0-9]{32}$`（自增的只有 UUID 去横线）；所有 `Paths.get(...).resolve(...)` 后追加 `normalize().startsWith(base)` 断言。**这是防御性编码的标准动作，成本极低。**

**工作量 / 风险**：0.2 人日；风险低。

---

### P0-5 公开分享下载：整文件读入堆内存（大文件 OOM）

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/controller/ShareController.java:85-109`

**证据**

```java
HttpResponse dufsResp = req.execute();          // :96  ← 无 timeout
response.setHeader("Content-Disposition", ...); // :105
try (OutputStream out = response.getOutputStream()) {
    out.write(dufsResp.bodyBytes());            // :107 ← 全量进堆
}
```

**影响**：`jnclub.disk.max-size-mb: 500`，一次 500MB 下载即至少 500MB 堆占用（`-Xmx1g`），两三个并发即 OOM；且因为无 timeout，慢速客户端会把连接和内存一起挂住。对比 `FileProxyController.java:74-82` 已经实现了 8KB 流式拷贝 + Range 透传——**同一系统内两种写法，这里是退化版本**。

**建议**：直接复用 `FileProxyController` 的流式实现（`IOUtils.copyLarge` 或 8KB 循环），透传 `Range`/`Accept-Ranges`，并设置 `timeout(10_000)`。**这是一次复制粘贴级修复，收益极高。**

**工作量 / 风险**：0.3 人日；风险低。

---

### P0-6 密码库解锁会话永不过期（安全常数是死代码）

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/VaultService.java:62`、`:70`、`:457`

**证据**

```java
/** 主密钥空闲过期（毫秒）：30 分钟 */
private static final long SESSION_TTL_MS = 30 * 60 * 1000L;   // :62 ← 全仓库无使用点

private final Map<String, UnlockSession> sessions = new ConcurrentHashMap<>();  // :70
...
session.lastAccess = System.currentTimeMillis();               // :457 ← 只写不读
```

**影响**
- 类注释宣称"密钥仅存内存、30 分钟空闲过期"，**实际解锁后永久有效**，直到进程重启。
- 内存泄漏：`sessions` 只增不删（仅显式 `lock()` 才移除），长期运行持续增长。
- 多实例场景下解锁态不共享（重启/切实例即锁定），但当前是单实例，影响次要。

**建议**
1. 增加 `@Scheduled(fixedDelay=60_000)` 清理 `now - lastAccess > SESSION_TTL_MS` 的条目（注意用 `remove(key, value)` 避免并发误删）。
2. 每次 `getSession` 时刷新 `lastAccess`（读路径也要写）。
3. 补一个单测：解锁 → 模拟 31 分钟 → 断言 session 已失效。

**工作量 / 风险**：0.5 人日；风险低。

---

### P0-7 密码库加密使用 AES/ECB（确定性加密）

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/crypto/VaultCrypto.java:19`（注释自述）、`:73-74`

**证据**

```java
/** AES/ECB/PKCS5Padding，32 字节密钥 */    // :19
private static AES aes(byte[] key) {
    return SecureUtil.aes(key);               // :73-74 ← Hutool 默认 ECB
}
```

**影响**
- ECB 无 IV：**相同明文 → 相同密文**。密码库中重复的密码、重复的 URL/用户名在密文层面直接可比对，等于泄漏"哪些条目相同"。
- 分组重放：ECB 的块可被重排/拼接（跨条目替换字段），完整性无保护（无 MAC/GCM tag）。
- 这是密码管理器的核心资产，属于"设计缺陷"而非"配置问题"。

**建议**：迁移到 AES-256-GCM（Hutool `SecureUtil.aes` 不直接提供，用 `Cipher.getInstance("AES/GCM/NoPadding")` + 12 字节随机 IV + 16 字节 tag）。因为已有密文，**必须做一次性重加密迁移**（解密 → 重新加密 → 写回），并在 `vault_meta` 里记录加密版本号，支持回滚。迁移脚本要幂等、可重入、有断点。

**工作量 / 风险**：1.5 人日；**风险高**（数据不可逆），建议单独排期、先在备份库演练、双人复核。

---

### P0-8 敏感凭据明文与口令复用

**位置**
- `jnclub-gateway/src/main/resources/application.yml:8`（DB root 口令）、`:40`（dufs 口令）、`:75`（蓝奏云账号口令）、`:37`（`secret-key`）、`:44`（DAO 加密密钥自带弱默认值）
- `/etc/dufs.conf` 的 `auth` 段
- 生产 `systemd` unit：`User=root`

**证据**

```
:8    password: ******            ← 与 :40、:75 同值
:44   crypto-key: ${JNCLUB_DAO_CRYPTO_KEY:jnclub-dev-crypto-key-change-me}
```

（该文件已 gitignore，README.md:155 说明线上用外置配置——**这点做得对**，但口令本身仍是弱口令堆叠。）

**影响**
- 三处系统（DB / 文件服务 / 音乐源站）共用同一口令，任何一处泄露即横向打通。
- `crypto-key` 的默认兜底值意味着**若环境变量缺失，会用公开的默认密钥加密 sa-token 数据**——默认值必须视为"生产禁用"。
- Java 进程以 root 运行：一旦 RCE，攻击者直接拿到 root（dufs 也是 root）。

**建议**
1. 三套口令全部换成独立强随机（≥24 位），用 `systemd Environment=` 或 `/etc/jnclub.env`（`chmod 600`）注入，`application.yml` 只留 `${...}` 占位。
2. 删除 `crypto-key` 的默认值，改为启动时缺失即 `fail-fast`（`@Value("${JNCLUB_DAO_CRYPTO_KEY}")` 无默认）。
3. 新建 `jnclub` 系统用户运行 Java，`systemd User=jnclub`；dufs 同理（其当前 `pid 1101` 由 root 启动）。
4. 轮换后核查历史日志/备份中是否残留旧口令。

**工作量 / 风险**：0.5 人日；风险中（改 systemd/权限需谨慎，先备份 unit）。

---

## 3. P1（重要，1–2 个月内）

### P1-1 异常与响应体系分裂：同一类错误三种表现

**位置**
- `jnclub-common/src/main/java/com/jnclub/common/exception/GlobalExceptionHandler.java:37-42`（`BizException` → `@ResponseStatus(HttpStatus.OK)`）
- 同文件 `:44-49`（兜底 `Exception` → 500）
- `jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/BookmarkService.java:156`、`:248`、`:303`、`:314`、`:317`（抛 `new RuntimeException("收藏不存在")`）
- `jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/NoteService.java:140`、`:212`、`:273`、`:350`、`:362`、`:365`（同样 `RuntimeException`）

**证据**

```java
@ExceptionHandler(BizException.class)
@ResponseStatus(HttpStatus.OK)          // :38 ← 业务异常 HTTP 200
public R<Void> handleBizException(BizException e) { ... }

@ExceptionHandler(Exception.class)      // :44
public R<Void> handleException(Exception e) { return R.fail(500, "系统内部错误"); }
```

**影响**
- "收藏不存在"这类正常业务分支走 500（因为抛的是 `RuntimeException`），前端只能拿到"系统内部错误"，且会触发 5xx 监控告警——**噪声掩盖真实故障**。
- 真正的业务错误反而返回 HTTP 200（body 里 `code=500`），网关/nginx/监控无法区分。
- bookmark 模块**零参数校验**（全仓 `@Valid/@Validated/@NotNull` 命中数为 0），controller 全用 `Map<String,Object>` + 手工 `Long.parseLong`（如 `ShareController.java:48`），`NumberFormatException` 直接落到兜底 500。

**建议**
1. 统一错误通道：业务失败一律抛 `BizException`，HTTP 状态码按语义映射（400/403/404/409），删除 `@ResponseStatus(HttpStatus.OK)`。
2. 把 bookmark 侧 11 处 `RuntimeException` 改为 `BizException`（可先做 `grep -n "new RuntimeException" ` 全量替换 + 编译验证）。
3. bookmark 引入 `spring-boot-starter-validation`，controller 换 DTO + `@Valid`；至少给 `Long.parseLong` 包一层 400 映射。
4. 参考 music 模块的 `MusicGlobalExceptionHandler`（它已正确返回 HTTP 码，:91-98），把两套合并到 common。

**工作量 / 风险**：2 人日；风险中（前端可能依赖 `code` 而非 HTTP 状态，需同步回归）。

---

### P1-2 会话 token 进入日志与 URL；HTTP 未强制 HTTPS

**位置**
- `jnclub-common/src/main/java/com/jnclub/common/sso/SsoClientController.java:97`（响应体入日志）、`:133-136`（token 明文入日志）、`:139-147`（`?token=` 拼到 URL）
- `jnclub-module-music/src/main/java/com/jnclub/music/common/logging/RequestLoggingInterceptor.java:48-49`（GET query 原文入日志）
- 生产 nginx：HTTP 200 与 HTTPS 200 **并存，无 301 跳转**

**证据**

```java
log.info("SSO ticket 交换返回: status={}, body={}", status, rawBody);       // :97
log.info("JNClub 登录成功, userId={}, ssoToken={}, jnclubToken={}", ...);   // :133-136
redirectUrl += "?token=" + jnclubToken;                                     // :139-147
```

**影响**
- 任何能读日志的人（运维、日志采集、备份）都能直接接管会话；token 有效期 604800 秒 = **7 天**。
- token 在 URL 中会进入 nginx access log、浏览器历史、`Referer`（若页面外链资源）。配合 HTTP 明文，同网段可嗅探。
- `/sso/login?ticket=xxx`、`/api/share/{token}?pwd=xxx` 的密码/票据随 query 进入日志。

**建议**
1. :97/:133-136 改为只记 `userId` 与 `traceId`，token 打码（保留前 4 后 4）。
2. token 改走 `HttpOnly` Cookie 或 `POST` body，不再拼 URL；若必须兼容，`history.replaceState` 清理 + `Referrer-Policy: no-referrer`。
3. nginx 增加 `return 301 https://$host$request_uri;`（HTTP→HTTPS 强制）。
4. `RequestLoggingInterceptor` 对 query 做键名白名单（只放行 `page/size/q`）。

**工作量 / 风险**：1 人日；风险中（改登录链路，需端到端回归 SSO）。

---

### P1-3 `sa_token_data` 只增不删，且无 `expire` 索引

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/sso/SaTokenDaoCaffeinePlusMysql.java`（`searchData` 用 `ORDER BY expire ... LIMIT`）、`docs/init.sql:197-203`（建表只有主键）

**证据（生产实测）**

```
SHOW INDEX FROM sa_token_data;   →  只有 PRIMARY(id)
SELECT COUNT(*), SUM(expire < UNIX_TIMESTAMP()*1000) FROM sa_token_data;
18        14                     ← 77.8% 是过期垃圾行
FROM_UNIXTIME(MIN(expire)/1000) = 2026-08-20（两个月前）
```

**影响**
- 每次登录写 2 行（`login:token` + `login:session`），7 天后仍不删除；`searchData` 走 `ORDER BY expire` 无索引 = 全表 + filesort。
- 现在 18 行时无感，但这是**唯一会随时间单调恶化**的问题：按每天几次登录，一年后是数千行垃圾，且全表扫描每次都跑。属于"延迟爆炸"型。

**建议**
1. `ALTER TABLE sa_token_data ADD INDEX idx_expire (expire);`（幂等迁移）。
2. 增加每日清理任务：`DELETE FROM sa_token_data WHERE expire < ? AND expire > 0 LIMIT 1000`（分批，避免长事务），或 `SaTokenDao` 实现 `delete` 自动清理。
3. 顺带修 `saveToDb` 的"先 insert 捕获 DuplicateKey 再 update"（`:229-247`）：改为 `INSERT ... ON DUPLICATE KEY UPDATE`，每次 set 省一次失败写。

**工作量 / 风险**：0.5 人日；风险低（`DELETE` 分批 + 限速）。

---

### P1-4 死链检测同步阻塞请求线程

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/BookmarkService.java:428-467`、`:456`

**证据**

```java
for (Bookmark b : list) {
    ...
    if (probeUrl(b.getUrl()) == 2) { ... }
    try { Thread.sleep(120); } catch (InterruptedException ignored) { ... }   // :456
    bookmarkMapper.updateById(b);                                             // :454
}
```

**影响**
- 逐个串行探测 + 每条固定 sleep 120ms：**20 条收藏 = 2.4s 纯睡眠**，100 条 = 12s+；每次探测 `timeout(10_000)`，最坏 100 条 = 1000s。HTTP 请求线程全程占用，Tomcat 默认 200 线程很快被打满。
- 全模块**无 `@Async`、无线程池**（grep 零命中），这条路径没有任何异步兜底。

**建议**
1. 改为异步任务：接口只落库"检测中"状态并立即返回 202，后台线程池（`ThreadPoolTaskExecutor`，core 4 / max 8 / queue 500）分片探测，完成后回写。
2. 去掉固定 `Thread.sleep`，改用限速器（`RateLimiter` 已有，`RateLimiter.java:18-20`）或每批之间 sleep。
3. 探测并发度 ≤ 8，`timeout` 降到 3s；失败重试 1 次即标记 `dead`。
4. 前端轮询 `GET /api/bookmarks/dead` 拿结果（该端点已存在）。

**工作量 / 风险**：1.5 人日；风险中（需改前端交互，从同步等待改为轮询）。

---

### P1-5 上传只校验客户端声明的 Content-Type

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/UploadService.java:63-66`、`:76`、`:84`、`:121`

**证据**

```java
String contentType = file.getContentType();       // :63  ← 客户端可任意伪造
if (!ALLOWED_MIME.contains(contentType)) { ... }  // :64
...
String ext = getFileExtension(originalFilename);  // :76  ← 扩展名不校验
HttpRequest.put(url).body(file.getBytes())        // :84  ← 整文件入内存 + 无 timeout
```

`ALLOWED_MIME`（`:49-52`）包含 `image/svg+xml`、`image/tiff`；`guessType`（`FileProxyController.java:96-124`）对 `.svg` 返回 `image/svg+xml`，**同源返回 SVG 可执行脚本 = 存储型 XSS**。

**影响**
- 攻击者上传 `.html`/`.svg`（伪造 `Content-Type: image/png`），经 `/api/files/...` 同源返回 → 在 JNClub 域下执行任意 JS（窃取 token）。
- `file.getBytes()` 整文件进堆：10MB 限制下尚可，但与 P0-5 同类风险叠加。

**建议**
1. 校验 magic number（`Files.probeContentType` 不够，用 Apache Tika 或手写 PNG/JPEG/GIF/WebP 文件头判断），不信任 `getContentType()`。
2. 扩展名白名单（`.png/.jpg/.jpeg/.gif/.webp`），**移除 svg/tiff**；若必须支持 SVG，`Content-Disposition: attachment` + `Content-Security-Policy: sandbox`。
3. `body(file.getBytes())` → `body(InputStream)` + `timeout(10_000)`。
4. 上传目录与下载域分离（`jn_file` 子域 + `X-Content-Type-Options: nosniff`）。

**工作量 / 风险**：1 人日；风险中（可能影响前端已上传的 svg 头像）。

---

### P1-6 手写 `SqlSessionFactory` 导致 MyBatis 配置整体失效（生产日志已证实）

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/config/MybatisPlusConfig.java:37-56`、`:43-45`、`:48-49`、`:51-53`

**证据**

```java
GlobalConfig globalConfig = new GlobalConfig();          // :43
globalConfig.setMetaObjectHandler(...);                  // 只 set 了这一项，其余默认值丢失
MybatisConfiguration configuration = new MybatisConfiguration();  // :51
configuration.setMapUnderscoreToCamelCase(true);         // :52
factory.setMapperLocations(resolver.getResources("classpath*:mapper/**/*.xml")); // :48
```

生产启动日志（`journalctl -u jnclub`，10:02:54）：

```
WARN c.b.m.s.MybatisSqlSessionFactoryBean : Property 'mapperLocations' was specified but matching resources are not found.
```

**影响**
- `application.yml` 的 `mybatis-plus.configuration.*`（含 `log-impl`、`default-enum-type-handler`、`cache-enabled` 等）**全部不生效**——因为手工 new 了 `MybatisConfiguration`，Spring Boot 的 `MybatisPlusProperties` 不再参与。
- `mapperLocations` 指向一个**不存在的目录**（全仓无 XML），每次启动一条 WARN，说明配置与代码脱节。
- 类注释自述"手动创建 SqlSessionFactory 解决 Spring Boot 4.0.0 兼容性问题"——兼容性问题可能真实存在，但代价是配置面整体失效，属于**用错方案掩盖问题**。

**建议**
1. 先确认 Spring Boot 4.0.8 + mybatis-plus 3.5.17 是否仍需要手工创建（3.5.17 已支持 Boot 4；若必须，用 `MybatisPlusPropertiesCustomizer` 或继承默认 `SqlSessionFactoryBean` 再覆盖单点）。
2. 删除不存在的 `mapperLocations` 配置（`:48` 与 `application.yml:63`），消除启动 WARN。
3. 若保留手工创建，显式把 `MybatisPlusProperties` 的 `configuration` 合并进 `MybatisConfiguration`，否则 `log-impl` 之类的设置永远无效（当前生产实际开的是 `StdOutImpl`，`application.yml:65`——但因为配置失效，**生产 SQL 是否打印取决于默认值，需实测确认**）。

**工作量 / 风险**：1 人日；风险中（动数据访问层，需回归全部 Mapper）。

---

### P1-7 缓存失效策略写放大：13 处 `evictByPrefix` 全量 SCAN 删除

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/BookmarkService.java:100`、`:162`、`:181`、`:253`、`:269`、`:283`、`:295`、`:307`、`:321`、`:330`、`:344`、`:459`、`:502`（共 13 处）
实现：`jnclub-common/src/main/java/com/jnclub/common/cache/CacheService.java:81-90`

**证据**

```java
try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
    while (cursor.hasNext()) {
        redis.delete(cursor.next());     // :85 ← 逐条 DELETE，无 pipeline / UNLINK
    }
}
```

**影响**
- 单条收藏变更 → SCAN 整个 `jnclub:cache:bookmark:{userId}:*` 命名空间，逐条往返 Redis。缓存条目多时（列表 + 详情 + 标签）一次更新几十次 RTT。
- 当前用户量小、单用户收藏 20 条，实际耗时可忽略——**但这是"每次写都全量清"的架构性选择**，数据量上来后是第一批瓶颈。
- 好的一面：`evictByPrefix` 用 `SCAN` 而非 `KEYS`（`:82-86`），且全部 try-catch 降级——**这部分实现是对的**，别改成 `KEYS`。

**建议**（按投入产出）
1. 短期：`delete` 改 `unlink`（异步释放）并 pipeline 批量（`redis.executePipelined`），一次 RTT 删完。
2. 中期：把缓存键粒度从"用户级前缀"改为"实体级 key"（`bookmark:{id}` + `bookmark:list:{userId}:{queryHash}`），更新时只删相关 key，不再前缀清空。
3. 长期：写路径改 `@CacheEvict(allEntries=false)` 或版本号（`bookmark:ver:{userId}`，读时拼版本号，更新即 +1，旧 key 自然过期）——**版本号法最省心，推荐**。

**工作量 / 风险**：1 人日（版本号法）；风险中（缓存一致性，需回归列表/详情）。

---

### P1-8 可观测性缺口：无慢查询、无连接池配置、无健康检查、无优雅停机

**位置**
- 生产 MySQL：`slow_query_log=0`、`long_query_time=10`（实测）
- `jnclub-gateway/src/main/resources/application.yml`：**无 `spring.datasource.hikari.*`、无 `server.tomcat.*`、无 `management.*`、无 `spring.lifecycle.timeout-per-shutdown-phase`**
- 生产 `systemctl`：`Restart=on-failure`，无 `SuccessExitStatus`/`TimeoutStopSec`
- Actuator 依赖在 `jnclub-gateway/pom.xml`，但 `/actuator/*` 全部 404（未暴露）

**影响**
- **无法回答"哪个接口慢"**：慢查询日志关闭 + 无 APM + Micrometer 未暴露。当前 `Slow_queries=0` 是"没开日志"的假象。
- 连接池用 Hikari 默认（max 10），`Threads_connected=21` 说明还有别的连接（可能是 JN_SSO），但**没有池参数意味着无法针对业务调优，也无法防"连接泄漏"**。
- 无健康检查：systemd 只能判断"进程在"，进程假死（如线程池打满）不会被发现、不会重启。
- 无优雅停机：`kill` 时正在处理的请求被硬断，分片上传/长事务可能损坏。

**建议**（全部是配置项，零业务改动）
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 15
      minimum-idle: 5
      connection-timeout: 3000
      max-lifetime: 1800000
  lifecycle:
    timeout-per-shutdown-phase: 20s
server:
  shutdown: graceful
  tomcat:
    threads:
      max: 100
    connection-timeout: 5s
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: never
```
- MySQL 打开慢查询：`slow_query_log=ON`、`long_query_time=1`、`log_queries_not_using_indexes=ON`。
- systemd 加 `ExecStart` 前置健康探针（curl `localhost:19005/actuator/health`）或改用 `Type=notify`。
- `Restart=always` + `RestartSec=5`（当前 `on-failure` 对 OOM 之外的退出不重启）。

**工作量 / 风险**：0.5 人日；风险低（纯配置，可灰度）。

---

### P1-9 文档 DDL 与生产库脱节：按 `init.sql` 新建环境必挂

**位置**：`docs/init.sql`

**证据（生产实测对比）**

| 项 | `docs/init.sql` 非注释 DDL | 生产库实际 |
|---|---|---|
| `t_bookmark.deleted` | **不存在** | 存在（`tinyint`） |
| `t_note.deleted` / `t_file.deleted` / `t_vault.deleted` | 不存在 | 存在 |
| `t_vault.password_fingerprint` | 只在 `:181` 注释态 ALTER | 存在（`varchar(64)`） |
| `t_share` / `t_share.visit_count` | 无建表（注释态 `:149`） | 存在 |
| `t_tag` / `t_tag_relation` / `t_vault` / `t_vault_meta` | **全部注释态** | 全部存在 |
| `idx_bookmark_deleted(user_id, deleted)` | 注释态 `:135-140` | 存在 |

同时库中还留着 `t_bookmark_snapshot`、`t_feed`、`t_feed_item`（代码已无模块）、`t_todo`/`t_webdav_server` 在本地库仍在。

**影响**：任何人按 `docs/init.sql` 起一个新环境（新机器、测试库、灾备恢复），`BookmarkService` 的 `eq(deleted, 0)` 会直接报 `Unknown column`，**服务起不来**。这是"文档即事实"失效的典型后果。

**建议**
1. 引入 Flyway（`flyway-core` + `flyway-mysql`），把现有结构导出为 `V1__baseline.sql`，`baselineOnMigrate=true`；后续 DDL 全部走 `V2__*.sql`。
2. 先做一次生产结构导出（`mysqldump --no-data`）作为 baseline，**不再手写 DDL**。
3. 用 `spring.sql.init.mode=never`（默认）避免与 Flyway 冲突；`MusicPlaylistTableInit` 等 9 个启动期 DDL 类逐步下线。
4. 清理遗留表（`t_feed*`、`t_bookmark_snapshot`）——**先确认无代码引用**，再单独迁移删除。

**工作量 / 风险**：2 人日；风险中（首次 baseline 需比对，避免误删）。

---

### P1-10 测试与交付链路：3 个测试类 / 200 个源文件

**位置**
- 全仓测试：`jnclub-common/src/test/java/com/jnclub/common/config/CorsConfigTest.java`、`jnclub-module-bookmark/src/test/java/com/jnclub/bookmark/crypto/VaultCryptoTest.java`、`jnclub-module-bookmark/src/test/java/com/jnclub/bookmark/service/BookmarkServiceSanitizeTest.java`
- `jnclub-module-music/pom.xml`：**无 `spring-boot-starter-test`** → music 模块无法写测试
- 部署：`crontab` 每 5 分钟跑 `/opt/auto-deploy/watcher.sh`（git 轮询），**无 CI、无 Dockerfile、无流水线门禁**
- `README.md:63` 部署命令带 `-DskipTests`

**影响**
- 覆盖率约 1.5%，且集中在工具类（CORS/sanitize），**核心链路（鉴权、分享、上传、Vault）零测试**。
- 无 CI 意味着"改动 → 部署"之间没有编译/单测门禁，靠 cron 轮询 + 人工观察。
- `music` 模块连测试依赖都没有，等于放弃了这块的回归能力。

**建议**
1. music pom 补 `spring-boot-starter-test`（scope test）。
2. 先补"安全回归 5 件套"单测：鉴权白名单、路径穿越拒绝、上传类型拒绝、分享密码校验、Vault 会话过期。**这 5 个用 MockMvc 即可，成本 1 天，能挡住绝大多数回归。**
3. 加最小 CI（GitHub Actions 或服务器上的 `mvn -q test` 前置到 `watcher.sh`），测试失败则中止部署。
4. 长期再谈覆盖率门槛（如核心模块 40%）。

**工作量 / 风险**：3 人日；风险低。

---

### P1-11 导入长事务：整个 JSON 导入包在一个事务里逐条查写

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/ImportService.java:61-62`、`:82-103`、`:108-120`

**证据**

```java
@Transactional
public Map<String, Object> importBookmarks(String jsonContent, String mode) {   // :61-62
    ...
    for (dir : dirs) { findOrCreateDir(...); }        // :82-103 每条一次 SELECT
    for (bm : bookmarks) {
        existsBookmark(...);                           // :108-120 每条一次 SELECT
        findOrCreateDir(...);                          // 又一次
        save(...);
    }
}
```

**影响**：1000 条导入 = 2000+ 次查询 + 1000 次写，全部在**一个事务**内（undo log 膨胀、行锁持有时间长、连接被独占）。当前数据量小，但导入是"用户主动大流量"操作，是最容易触发的场景。

**建议**
1. 事务边界缩到"每 200 条一批"，批间提交。
2. 目录与收藏先去重一次性 `selectList(in(...))`，内存建 Map（`hasDirId` 的 O(n) 扫描同时消掉）。
3. 收藏写入用 `saveBatch`（MyBatis-Plus 提供）。
4. 导入改为异步任务（返回 taskId，前端轮询），避免 HTTP 超时。

**工作量 / 风险**：1 人日；风险中（事务拆分要保证部分失败可回滚/可重试）。

---

## 4. P2（改进，数据量过千再启动）

> 以下问题**代码上确实存在**，但结合生产数据量（`t_bookmark` 20 行、`music_track` 36 首），当前收益接近于零。列出是为了"知道它在那儿"，不是"现在要动"。

### P2-1 批量写 N+1（bookmark 模块 54 处逐条写）

**位置**：`BookmarkService.java:258`（moveBookmarksBatch）、`:274`（deleteBookmarksBatch）、`:288`（setTagsBatch）、`:333`（updateSortOrder）；`NoteService.java:307-312`、`:321-326`、`:387`；`DirectoryService.java:173-183`；`NotificationService.java:83-86`；`TagService.java:203-211`
**现状**：全模块仅 3 处使用批量 API（`VaultService.java:373`、`VaultService.java:494`、`NoteService.java:398`）。
**建议**：`updateBatchById` / `saveBatch` / `deleteBatchIds` 替换 for 循环；同时把 `@Transactional` 范围从"整个循环"缩到"单条"或分批。
**工作量**：2 人日；风险低。

### P2-2 搜索全表 LIKE：`SearchService` 5 类实体 + 标签 N+1

**位置**：`jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/SearchService.java:87`、`:110`（含 `Note::getContent` MEDIUMTEXT）、`:133`、`:154-155`、`:180`；`:186-187`（循环内 `tagRelationMapper.selectCount`）
**建议**：`content` 用 MySQL 全文索引或外部（Meilisearch）；标签计数改一次 `GROUP BY`；前端搜索加 300ms 防抖。
**工作量**：3 人日；风险中。

### P2-3 目录统计 4 次 `selectCount` + 递归查子孙

**位置**：`DirectoryService.java:89-125`、`:130-160`、`collectDescendantIds`
**建议**：一次 `UNION ALL` 或 `GROUP BY directory_id` 聚合；子孙目录改路径枚举（`path` 列）或递归 CTE（MySQL 8 支持）。
**工作量**：1 人日；风险低。

### P2-4 内存分页 / 不分页

**位置**：`TrackServiceImpl.java:1026-1030`（`paginate(List<T>)` 全量后切页）、`BookmarkController.java:22-27`（列表接口无分页参数，全量返回）
**建议**：SQL 分页（`Page<T>`）替代内存切页；列表接口强制 `page/size` 上限 100。
**工作量**：1 人日；风险中（前端需改）。

### P2-5 匿名限流桶共享

**位置**：`jnclub-module-music/src/main/java/com/jnclub/music/common/ratelimit/MusicRateLimitInterceptor.java:40-46`
```java
String deviceId = request.getHeader("X-Device-Id");
if (deviceId == null || deviceId.isBlank()) deviceId = "anonymous";   // :42
```
**影响**：所有未带 header 的访客共享 120 次/分钟，一人刷脚本即可让全站匿名用户 429。
**建议**：退化 key 用 IP（`X-Forwarded-For` 首段），而不是常量。
**工作量**：0.2 人日；风险低。

### P2-6 定时任务单线程 + 无分批

**位置**：`RecycleService.java:238-275`（四类实体 `selectList` 全表后逐条 purge）、`AssetCleanService.java:126-192`（全表捞便签含 MEDIUMTEXT）、`CloudDiskService.java:739-752`
**做得好的部分**：三处都用了 `RedisLock` + `try/finally unlock`（多实例安全），**这点不要动**。
**建议**：`selectList` 加 `LIMIT 500` 分批循环；`AssetCleanService` 改"只查未认领 + 一次 join"而非全表。
**工作量**：1 人日；风险低。

### P2-7 `/api/v1/**` 通配放行（建议枚举化）

**位置**：`jnclub-common/src/main/java/com/jnclub/common/config/WebMvcConfig.java:24`
**说明**：音乐匿名是既定决策，但 `/**` 通配会让**未来任何挂在 `/api/v1/` 下的新接口默认裸奔**（P0-2 就是这么发生的）。
**建议**：改为只读端点枚举（`/api/v1/tracks/**`、`/api/v1/folders/**`、`/api/v1/lyrics/**`），新增接口默认受保护（fail-closed）。
**工作量**：0.2 人日；风险低。

### P2-8 日志中的 query 明文

**位置**：`RequestLoggingInterceptor.java:48-49`
**建议**：query 参数按白名单输出。
**工作量**：0.2 人日。

### P2-9 死代码与遗留物

- `WebMvcConfig.java:45-49`：生产配置类里的 `public static void main` 玩笑代码（`record 她()` / `Stream.iterate("含苞"…)`）
- `SsoConfig.java`：`@EventListener(ApplicationReadyEvent)` 空实现
- `FeatureTableInit.java:52-65`：`ensureTable` 定义但从未调用
- `AssetCleanService.java:272`、`:283-320`：`HttpRequest.delete` **无 timeout**
- `TrackServiceImpl.java:562-575`：`searchTracksWithUrl` 内存遍历全部曲目（36 首无所谓，1000 首要注意）
- 数据库遗留表：`t_feed`、`t_feed_item`、`t_bookmark_snapshot`
**工作量**：0.5 人日；风险低。

### P2-10 构建与依赖治理

- root pom 无 `maven-surefire-plugin` 配置（测试默认行为不可控）
- 无依赖版本锁定（`dependencyManagement` 只继承 parent，第三方版本散落各模块）
- music 模块无 test 依赖（见 P1-10）
- `springdoc-openapi-starter-webmvc-ui 2.8.6` 在生产也加载（`/swagger-ui` 可访问？需确认，若可访问则是信息泄露面）

**建议**：`mvn dependency:tree` 定期审计 + `versions-maven-plugin`；springdoc 用 `@Profile("dev")` 隔离。
**工作量**：1 人日。

---

## 5. 已经做得不错，建议**不要动**

避免过度优化，这几处是代码里真正的亮点：

1. **Redis 密码外置**：`application.yml:19` 用 `${JNCLUB_REDIS_PASSWORD:}` 占位，生产 `systemd Environment=` 注入。**不要**为了"配置齐全"把密码写回文件。
2. **CORS 策略**：`jnclub-common/.../config/CorsConfig.java:31`、`:46`、`:50` —— 显式白名单 + `setAllowCredentials(false)` + maxAge，且有 `CorsConfigTest` 单测。**不要**改成 `*`。
3. **批量查询消除 N+1**：`TagService.java:47-65`（一次 `selectList(in)` + groupBy）、`TagService.java:155-183`（`listTagsOfRefs` 注释明写"一次请求消除 N+1"）、`UserDataSupport.java:63-75`（`loadTrackMap`）、`TrackServiceImpl.java:470-490`（按 id 建索引替代双重循环）。这是全仓最规范的部分。
4. **定时任务多实例互斥**：`AssetCleanService.java:59`、`RecycleService.java:218`、`CloudDiskService.java:739`、`TrackCacheService.java:218-240` 全部 `RedisLock` + `try/finally`。**不要**改成裸 `@Scheduled`。
5. **`TrackCacheService` 的线程池设计**：`TrackCacheService.java:60-70`（续期池 fixed(4) 与轮次池 single 显式分离，避免同池排队饿死）、`:82-86`（`@PreDestroy` 关闭）、`:119-133`（`finally endRound`）、`:142-157`（非阻塞预热）、`:275-320`（`selectBatchIds` 批量判存在 + `CompletableFuture` 并发续期 + 全失败显式 `log.error`）。这是一份可以当范例的异步缓存实现。
6. **分享密码与幂等**：`ShareService.java:59` 用 `BCrypt.hashpw`、`:100-105` `checkpw`；`:49-65` 同一资源复用 token（幂等）。
7. **分片上传的原子性**：`CloudDiskService.java:154-188` 用 `.tmp` 写入 + `Files.move(REPLACE_EXISTING)`，避免半写文件被当成完整分片——**这是很多人写分片上传会漏的细节**。
8. **统一响应与 traceId**：`R.java` / `ApiResponse.java` 结构统一，异常日志带 `traceId`，`GlobalExceptionHandler` 兜底不泄漏堆栈。
9. **索引设计**：`schema.sql:19-89` 的 music 系列唯一键（`uk_favorite_device_track`、`uk_queue_device_position` 等）设计合理；生产 `t_bookmark` 的 `idx_bookmark_deleted(user_id, deleted)` 也在（只是没写进 init.sql）。
10. **文件代理的 Range 透传**：`FileProxyController.java:52-82` 正确处理 206 / `Content-Range` / 8KB 流式拷贝（可惜 `ShareController` 没复用它，见 P0-5）。

---

## 6. 落地顺序（按投入产出排序）

| 批次 | 内容 | 投入 | 收益 | 依赖 |
|---|---|---|---|---|
| **第 1 批（本周）** | P0-1 dufs 口令/权限、P0-2 管理端点鉴权、P0-3 文件代理校验、P0-8 口令轮换 | 1.5 人日 | 关掉"公网弱口令 + 匿名管理 + 路径穿越"三条可直接利用的洞 | 无 |
| **第 2 批（下周）** | P0-4 uploadId 校验、P0-5 下载改流式、P1-2 日志脱敏 + HTTPS 强制、P2-5/P2-8 小配置 | 1.5 人日 | 消除 OOM 与 token 泄露；全是小改动 | 无 |
| **第 3 批（月内）** | P1-1 异常体系统一、P1-3 token 表清理 + 索引、P1-6 MyBatis 配置修复、P1-8 可观测性配置 | 3 人日 | 让"错误可解释、性能可观测"；P1-6 会让 `log-impl` 等配置真正生效 | 需回归全部 Mapper |
| **第 4 批（月内）** | P1-9 Flyway baseline、P1-10 安全回归 5 件套、P1-5 上传校验 | 3 人日 | 新环境可一键起；核心链路有测试网 | P1-9 需要生产结构导出 |
| **第 5 批（季度）** | P0-6 Vault 会话过期、P1-4 死链异步化、P1-7 缓存版本号、P1-11 导入分批 | 4 人日 | 安全语义与长事务 | P0-7 需先设计迁移方案 |
| **第 6 批（单独排期）** | **P0-7 AES/ECB → GCM 迁移** | 1.5 人日 + 演练 | 密码库加密从"确定性"变为"语义安全" | 必须先备份 + 迁移脚本幂等 + 双人复核 |
| **技术债池（数据量过千再启动）** | P2-1、P2-2、P2-3、P2-4、P2-6、P2-9、P2-10 | — | 当前收益≈0，**明确暂不做** | — |

### 为什么 P0-7 排在最后

它是唯一"改错会丢用户数据"的项，且**没有外部攻击者能直接触发**（需要先拿到密文）。而 P0-1/P0-2/P0-3 是"今天就能被利用"的。安全修复的顺序应该是"先堵入口，再改内功"。

### 不建议做的事

- ❌ 不要因为"数据量小"就上分库分表/读写分离——20 行数据，纯负担。
- ❌ 不要引入 Redis 集群/多级缓存——单机 Redis DBSIZE 很小（实测），当前瓶颈不在缓存层。
- ❌ 不要重写 `LanzouApiClient`（1717 行）——它虽然长，但 `:69-72` 的重试参数、`:126-133` 的超时配置、`:142-156` 的会话代际管理是有实战沉淀的；只在 P0-2 收敛它的外部入口即可。
- ❌ 不要把 `TrackCacheService` 的线程池换成虚拟线程——`-Xmx1g` 下平台线程池（4+1）已经足够，虚拟线程对 IO 密集有益但会掩盖上游限流问题。

---

## 附录 A：生产实测数据（2026-10-19 11:20 CST）

```
主机      jiangnan (gm)  2026-10-19 11:21 CST
MySQL     8.0.46-0ubuntu0.24.04.4
          max_connections=151  innodb_buffer_pool_size=128MB
          slow_query_log=0     long_query_time=10
          Threads_connected=21 Max_used_connections=21  Slow_queries=0
          Uptime=1107024s      Innodb_buffer_pool_reads/read_requests≈0.116%
JVM       /usr/bin/java -Xmx1g -XX:MaxMetaspaceSize=320m -jar jnclub-gateway-...jar
          --spring.config.location=/home/jiangnan/JNClub/jnclub-gateway/application.yml
          RSS 475676 KB  运行 1h19m  MainPID 1114019
systemd   User=root  Restart=on-failure  RestartSec=5  Environment=JNCLUB_REDIS_PASSWORD=***
          ActiveEnterTimestamp=Mon 2026-10-19 10:02:23 CST  is-active=active
nginx     1.24.0  client_max_body_size 200M  proxy_read_timeout 3600s(×2)
          proxy_buffering off  gzip on  无 limit_req/limit_conn
dufs      pid 1101  root  0.0.0.0:5000  serve-path=/home/jiangnan/file_server
          allow-all=true  enable-cors=true  auth=[jiangnan:******@/:rw]
ufw       inactive
crontab   */5 * * * * /opt/auto-deploy/watcher.sh
actuator  /actuator /actuator/health /actuator/env /actuator/heapdump 全部 404
站点      http://jiangnan.88933.vip/ → 200（HTTP 未强制跳 HTTPS）
          https://jiangnan.88933.vip/ → 200
文件站    http://jn_file.88933.vip/ → 401
          -u jiangnan:****** → 200（可列目录、allow_upload/delete/search/archive 全 true）
          Dav: 1,2,3   ACAO: *   Allow-Credentials: true
启动日志  WARN MybatisSqlSessionFactoryBean: mapperLocations ... not found（每次启动）
          WARN TrackServiceImpl: 蓝奏云非会话类错误 file share cancelled（T3dd71228/T600875e6，每 5 分钟）
日志量    journalctl -u jnclub --since today | wc -l = 291
```

## 附录 B：本文引用的文件清单

```
jnclub-gateway/src/main/resources/application.yml
jnclub-common/src/main/java/com/jnclub/common/config/WebMvcConfig.java
jnclub-common/src/main/java/com/jnclub/common/config/CorsConfig.java
jnclub-common/src/main/java/com/jnclub/common/exception/GlobalExceptionHandler.java
jnclub-common/src/main/java/com/jnclub/common/cache/CacheService.java
jnclub-common/src/main/java/com/jnclub/common/sso/SsoClientController.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/config/MybatisPlusConfig.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/controller/FileProxyController.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/controller/ShareController.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/BookmarkService.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/SearchService.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/NoteService.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/VaultService.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/CloudDiskService.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/UploadService.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/ImportService.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/AssetCleanService.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/RecycleService.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/DirectoryService.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/service/TagService.java
jnclub-module-bookmark/src/main/java/com/jnclub/bookmark/crypto/VaultCrypto.java
jnclub-module-bookmark/src/main/java/com/jnclub/sso/SaTokenDaoCaffeinePlusMysql.java
jnclub-module-music/src/main/java/com/jnclub/music/admin/controller/AdminLanzouController.java
jnclub-module-music/src/main/java/com/jnclub/music/common/ratelimit/MusicRateLimitInterceptor.java
jnclub-module-music/src/main/java/com/jnclub/music/common/logging/RequestLoggingInterceptor.java
jnclub-module-music/src/main/java/com/jnclub/music/common/exception/MusicGlobalExceptionHandler.java
jnclub-module-music/src/main/java/com/jnclub/music/track/service/TrackCacheService.java
jnclub-module-music/src/main/java/com/jnclub/music/track/service/impl/TrackServiceImpl.java
docs/init.sql
```
