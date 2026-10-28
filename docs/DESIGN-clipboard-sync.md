# 剪贴板跨端同步协议（jnclub-module-clipboard）

jn-clipboard 桌面端与 PWA 共用一套同步协议，服务端是 JNClub 里的 `jnclub-module-clipboard` 模块，
认证复用 JNClub 的 Sa-Token / JNSSO 登录态。本文是客户端实现的契约。

## 为什么不用现成的 WebDAV 同步

桌面端原有的 `src-tauri/src/services/cloud_sync.rs` 走 WebDAV，把整份快照写成一个文件。
那条路在单用户场景够用，但接上账号体系后有三个硬伤：

1. **多用户只能靠分目录隔离**，目录泄漏就是数据泄漏，服务端无法审计。
2. **没有账号概念**，PWA 端要自己实现 WebDAV 客户端（含 Digest 认证、PROPFIND 解析）。
3. **快照式同步**每次都要传全量，条目过千后每次同步都在搬几 MB 的无变化数据。

因此同步走自建接口：增量拉取 + 服务端分配游标。桌面端的 WebDAV 通路保留不动，
两者可以并存——用户用哪个取决于是否登录了 JNClub 账号。

## 数据模型

四张表，全部按 `user_id` 隔离（`user_id` 取自登录态，**不接受请求体传入**）。

| 表 | 作用 | 关键列 |
| --- | --- | --- |
| `t_clipboard_record` | 剪贴板条目 | `sync_id`(客户端生成的 UUID)、`rev`(客户端版本号)、`seq`(服务端游标)、`deleted_at`(墓碑) |
| `t_clipboard_setting` | 可同步的设置键值 | `setting_key`、`rev`、`seq`、`deleted_at` |
| `t_clipboard_blob` | 附件元数据（内容存 dufs） | `sha256`、`byte_size`、`mime`、`ref_count` |
| `t_clipboard_user_seq` | 每用户游标分配器 | `user_id`(主键)、`seq` |

条目与设置**共用一个 seq 空间**。这样一次 `pull` 就能拿到合并后的增量，
客户端不需要维护两个游标，也不会出现「条目拉完了但设置落后」的中间态。

### seq 为什么由服务端分配

客户端时钟不可信：设备间可能差几分钟甚至几小时。若用客户端时间戳做增量游标，
**慢时钟设备写入的数据会被快时钟设备「已经拉过」的游标永久跳过**——数据静默丢失，
且没有任何报错。服务端分配单调递增 seq 就没有这个问题，顺带给出跨设备的稳定全序。

### 删除为什么是墓碑

物理删除后，已经拉过这条记录的设备永远不知道它被删了，会一直显示幽灵条目。
墓碑（`deleted_at` 非空）同样占用 seq，所以对端能通过 `pull` 拿到「这条没了」。

## 接口

前缀 `/api/clipboard`，全部要求登录态。请求头带 `jn-token: <token>`（JNClub 的 token-name）。

> **注意前缀选择**：`WebMvcConfig` 的 Sa-Token 拦截器把 `/api/v1/**` 列进了匿名白名单
> （音乐模块路径重写需要）。若挂在该前缀下，拦截器会静默放行，鉴权形同虚设。
> 本模块用 `/api/clipboard`，且控制器内仍显式调用 `StpUtil.checkLogin()`——
> 前缀规则将来被改动时鉴权不会跟着失效。

### GET /api/clipboard/sync/pull

| 参数 | 说明 |
| --- | --- |
| `since` | 客户端上次拿到的最大 seq，首次同步传 0 |
| `limit` | 本批最多条数（条目 + 设置合计），夹到 1..500，默认 200 |

```json
{
  "code": 200,
  "data": {
    "records": [ { "syncId": "...", "contentType": "text", "content": "...", "rev": 2, "seq": 12, "deletedAt": null, "...": "..." } ],
    "settings": [ { "key": "app.theme", "value": "mica", "rev": 1, "seq": 3, "deletedAt": null } ],
    "nextSeq": 12,
    "hasMore": false
  },
  "message": "success"
}
```

`nextSeq` 是**本页最后一条的 seq**，客户端用它做下一轮的 `since`。
多推一格就会永久漏数据，所以服务端严格不跳号。`hasMore` 为真时客户端应立即续拉，
不要等定时轮询——首次全量可能上千条。

### POST /api/clipboard/sync/push

```json
{
  "records": [ { "syncId": "...", "contentType": "text", "content": "...", "rev": 3, "...": "..." } ],
  "settings": [ { "key": "app.theme", "value": "mica", "rev": 2 } ],
  "blobs": [ { "sha256": "...", "byteSize": 12345, "mime": "image/png" } ]
}
```

```json
{
  "code": 200,
  "data": {
    "accepted": ["sync-id-1", "app.theme"],
    "conflicts": [ { "syncId": "...", "serverRev": 5, "serverRecord": { "...": "..." } } ],
    "rejectedSettings": ["app.window_width"],
    "nextSeq": 14
  },
  "message": "success"
}
```

### GET /api/clipboard/sync/state

返回当前用户可见的最大 seq（纯数字）。客户端把本地 `lastSeq` 与它比较：
相等说明已同步到底，不等则说明有增量，可以立刻 pull。

注意这里返回的是**已落库数据的最大 seq**，而不是「已分配游标的上界」。
批量分配会预留整段区间，冲突、幂等重放、被白名单拒掉的设置都会在区间里留下空洞；
拿上界做判断，客户端会永远觉得自己落后，于是反复 pull 空页。

## 乐观锁

每条记录带客户端维护的 `rev`。push 时服务端按四条分支判定：

| 服务端状态 | 判定 |
| --- | --- |
| 没有该条目 | 插入，分配新 seq |
| `incomingRev > serverRev` | 覆盖，分配新 seq |
| `incomingRev == serverRev` 且内容一致 | **幂等重放**，接受但不消耗 seq |
| 其余（rev 相等但内容不同，或 rev 更小） | 冲突，回传服务端版本 |

「内容一致」的比较前会先把入参按服务端默认值归一（例如省略 `isPinned` 等价于 `false`）。
不归一的话，一次正常重试会被判成冲突，客户端拿到「服务端版本」后又覆盖不掉，
于是每轮同步都在同一批条目上报冲突。

设置不做冲突回传：`rev` 更小的直接丢弃，客户端下次 pull 自然会拿到服务端版本。
设置冲突没有「保留谁的」这个问题——界面上改一个开关，后改的就是对的。

## 设置白名单

只有白名单内的键允许跨端。规则与桌面端 Rust 的 `is_setting_sync_eligible` 是同一套语义。

拒绝的三类：

- **设备私有键**：窗口尺寸、匿名 ID、本机路径、启动行为。同步过去只会互相覆盖。
- **设备私有前缀**：`cloud_sync_*`、`clipboard_*`。
- **敏感键**：`mqtt_password`、`cloud_sync_api_key`、`cloud_sync_webdav_password`、`e2e_key`。
  凭据不该以任何形式落进服务端数据库。
- **已删功能的遗留键**：老库里还有这些行，但不该因为没人读就开始跨端旅行。

被拒的键不会静默消失，会出现在 `rejectedSettings` 里——客户端可以据此提示用户
「这个设置只在当前设备生效」。

## 附件

`push` 的 `blobs` 只登记元数据（sha256 / 大小 / MIME），不传内容。
内容按 sha256 内容寻址存 dufs，`t_clipboard_blob.ref_count` 记引用数，
定时任务清理 ref_count 归零超过一定时间的条目。

**当前状态**：附件上传/下载端点尚未实现，`blobs` 只做元数据登记。
带图片的条目在阶段 1 只能同步文字部分。

## 测试

```bash
mvn -o -pl jnclub-module-clipboard test
```

22 个用例覆盖三块：

- `ClipboardSettingWhitelistTest` — 白名单规则（放过设备私有键 → 窗口尺寸互相覆盖；放过密码键 → 凭据明文落库）
- `ClipboardPushConflictTest` — 乐观锁四条分支（误判冲突 → 用户编辑被静默丢弃；误判接受 → 覆盖别人的修改且无人知晓）
- `ClipboardPullPaginationTest` — 跨表归并、`hasMore` 判定、`nextSeq` 不跳号

## 手工验证

```bash
# 1. SSO 登录拿 token
curl -s -c cj.txt -o /dev/null -X POST "http://localhost:8080/sso/doLogin" \
  -d "name=<user>&pwd=<pwd>&redirect=http://localhost:19005/sso/login"
TICKET=$(curl -s -b cj.txt -c cj.txt -o /dev/null -w "%{redirect_url}" -X POST \
  "http://localhost:8080/sso/doLogin" \
  -d "name=<user>&pwd=<pwd>&redirect=http://localhost:19005/sso/login" \
  | sed -n 's/.*ticket%3D\([a-f0-9]*\).*/\1/p')
TOKEN=$(curl -s -b cj.txt -c cj.txt -o /dev/null -w "%{redirect_url}" \
  "http://localhost:19005/sso/login?ticket=$TICKET" | sed -n 's/.*token=\(.*\)/\1/p')

# 2. 未带 token 应 401
curl -s "http://localhost:19005/api/clipboard/sync/pull?since=0"

# 3. push + pull
curl -s -H "jn-token: $TOKEN" -H "Content-Type: application/json" \
  -X POST -d @payload.json "http://localhost:19005/api/clipboard/sync/push"
curl -s -H "jn-token: $TOKEN" "http://localhost:19005/api/clipboard/sync/pull?since=0"
```
