package com.jnclub.clipboard.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.jnclub.clipboard.dto.SyncPullResponse;
import com.jnclub.clipboard.dto.SyncPushRequest;
import com.jnclub.clipboard.dto.SyncPushResponse;
import com.jnclub.clipboard.service.ClipboardBlobService;
import com.jnclub.clipboard.service.ClipboardStreamService;
import com.jnclub.clipboard.service.ClipboardSyncService;
import com.jnclub.common.model.R;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 剪贴板跨端同步接口。
 *
 * <h2>为什么控制器内还要手动 checkLogin</h2>
 * {@code WebMvcConfig} 的 Sa-Token 拦截器把 {@code /api/v1/**} 列进了匿名白名单
 * （音乐模块的路径重写需要）。如果本控制器挂在该前缀下，拦截器会静默放行，
 * 鉴权就形同虚设。这里用 {@code /api/clipboard} 前缀，本身不在白名单内，
 * 但仍然显式调用 {@code StpUtil.checkLogin()}：前缀规则将来被改动时，
 * 鉴权不会跟着一起失效。
 */
@Slf4j
@RestController
@RequestMapping("/api/clipboard")
@RequiredArgsConstructor
public class ClipboardController {

    private final ClipboardSyncService syncService;
    private final ClipboardBlobService blobService;
    private final ClipboardStreamService streamService;

    /**
     * 拉取增量。
     *
     * @param since 上次同步拿到的最大 seq，首次传 0
     * @param limit 本批最多条数，服务端会夹到 1..500
     */
    @GetMapping("/sync/pull")
    public R<SyncPullResponse> pull(@RequestParam(defaultValue = "0") long since,
                                    @RequestParam(defaultValue = "200") int limit) {
        StpUtil.checkLogin();
        String userId = StpUtil.getLoginIdAsString();
        return R.ok(syncService.pull(userId, since, limit));
    }

    /**
     * 提交本地变更。
     */
    @PostMapping("/sync/push")
    public R<SyncPushResponse> push(@RequestBody SyncPushRequest request) {
        StpUtil.checkLogin();
        String userId = StpUtil.getLoginIdAsString();
        SyncPushResponse response = syncService.push(userId, request);
        log.debug("剪贴板 push: user={} accepted={} conflicts={} rejectedSettings={}",
                userId, response.getAccepted().size(), response.getConflicts().size(),
                response.getRejectedSettings().size());
        // 在事务提交后广播：空闲的误报（幂等重放）只让对方多探测一次 state，
        // 漏报则让对端等到下一拍。这里放宽到 accepted/conflicts 非空即报。
        if (!response.getAccepted().isEmpty() || !response.getConflicts().isEmpty()) {
            streamService.notifyUser(userId);
        }
        return R.ok(response);
    }

    /**
     * SSE 变更流：任何设备 push 落库后，登录用户的其它在线连接被立刻唤醒。
     *
     * <p>认证与其它接口一样走 Sa-Token 请求头（桌面端 reqwest 可带）；
     * 事件体只是「该看一眼了」的信号，不携带任何内容，所以无需鉴权外的
     * 权限区分。`X-Accel-Buffering: no` 让 nginx 不要缓冲这条响应。
     */
    @GetMapping("/sync/stream")
    public SseEmitter stream(HttpServletResponse response) {
        StpUtil.checkLogin();
        response.setHeader("X-Accel-Buffering", "no");
        return streamService.subscribe(StpUtil.getLoginIdAsString());
    }

    /**
     * 当前用户的同步游标，用于客户端判断自己是否落后。
     *
     * <p>客户端把本地 lastSeq 与这个值比较：相等说明已同步到底，
     * 不等则说明还有增量，不必等定时轮询就能立刻 pull。
     */
    @GetMapping("/sync/state")
    public R<Long> state() {
        StpUtil.checkLogin();
        String userId = StpUtil.getLoginIdAsString();
        return R.ok(syncService.currentSeq(userId));
    }

    /**
     * 上传一张图片。
     *
     * <p>响应里的 {@code data} 是服务端算出的 sha256：客户端必须用这个值作为条目的
     * {@code content} 再 push，而不是自己算的那个。两端各自算哈希会在
     * 「客户端重新编码过图片」时产生两个哈希，同一条记录在对端就取不到图。
     *
     * <p>内容已经存在时返回同样的 sha256，写入是幂等的。
     *
     * <p>用裸 body 而不是 multipart：multipart 的解析上限由
     * {@code spring.servlet.multipart.max-file-size} 全局控制（10MB），
     * 那个值是为分片上传调的，不该为了图床再抬高；裸 body 走的是
     * 容器读请求体的路径，不需要为它放宽任何全局配置。
     */
    @PostMapping("/blob")
    public R<String> uploadBlob(@RequestBody byte[] bytes,
                                @RequestHeader(value = "Content-Type", required = false) String mime) {
        StpUtil.checkLogin();
        String userId = StpUtil.getLoginIdAsString();
        if (bytes == null || bytes.length == 0) {
            return R.fail(400, "附件为空");
        }
        String sha = blobService.store(userId, bytes, mime);
        return R.ok(sha);
    }

    /**
     * 下载一张图片。
     *
     * <p>直接返回字节流而不是 JSON 里塞 base64：图片动辄几 MB，base64 会再涨三分之一，
     * 而且浏览器端 {@code <img src>} 需要能直接指向一个 URL。
     */
    @GetMapping("/blob/{sha256}")
    public ResponseEntity<byte[]> downloadBlob(@PathVariable String sha256) {
        StpUtil.checkLogin();
        String userId = StpUtil.getLoginIdAsString();
        // 路径参数直接进 dufs 的 URL，所以先确认它确实是 sha256；
        // 不校验的话 `../` 之类会拼到别人目录上。
        if (!ClipboardBlobService.isSha256Hex(sha256)) {
            return ResponseEntity.badRequest().build();
        }
        ClipboardBlobService.BlobPayload payload = blobService.load(userId, sha256);
        if (payload == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.mime()))
                .header("Cache-Control", "private, max-age=31536000, immutable")
                .body(payload.bytes());
    }
}
