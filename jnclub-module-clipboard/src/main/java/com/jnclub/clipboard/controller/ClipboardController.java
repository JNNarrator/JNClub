package com.jnclub.clipboard.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.jnclub.clipboard.dto.SyncPullResponse;
import com.jnclub.clipboard.dto.SyncPushRequest;
import com.jnclub.clipboard.dto.SyncPushResponse;
import com.jnclub.clipboard.service.ClipboardSyncService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        return R.ok(response);
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
}
