package com.jnclub.clipboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 剪贴板同步的 SSE 变更流。
 *
 * <h2>为什么有它</h2>
 * 轮询的最小代价是一分钟几十次空 pull，换来的是最长一个轮询周期的延迟。
 * 客户端登录后长连这条流，任何一台设备 push 落库，其余设备被立刻唤醒
 * 去做一次有内容的 pull——延迟从「一个 tick」变成「一次网络往返」。
 * 连接断了也不影响正确性：客户端有轮询兜底，流只是把「等到下一拍」
 * 变成「马上」。
 *
 * <h2>保活</h2>
 * nginx 等代理默认会缓冲响应或掐断闲置连接。控制器会带上
 * {@code X-Accel-Buffering: no} 关掉缓冲，这里每 30 秒发一条 SSE 注释行，
 * 让代理与中间设备始终有字节可看。连接失效由发送失败暴露，直接摘除。
 */
@Slf4j
@Service
public class ClipboardStreamService {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> byUser = new ConcurrentHashMap<>();

    /**
     * 注册一个用户的连接。超时设为 0（服务端永不主动断），客户端自己
     * 在断开后重连；失效连接靠心跳发送失败发现。
     */
    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> list =
                byUser.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> drop(list, emitter));
        emitter.onTimeout(() -> drop(list, emitter));
        emitter.onError(error -> drop(list, emitter));
        try {
            emitter.send(SseEmitter.event().name("hello").data("jnclub clipboard stream"));
        } catch (IOException e) {
            drop(list, emitter);
        }
        return emitter;
    }

    /**
     * 某用户的数据变了：唤醒该用户所有在线连接。
     *
     * <p>通知不携带具体内容——客户端醒来后先探测 /sync/state 再决定
     * pull，事件本身只需表达「该看一眼了」。
     */
    public void notifyUser(String userId) {
        List<SseEmitter> list = byUser.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("sync").data("now"));
            } catch (Exception e) {
                drop(list, emitter);
            }
        }
    }

    /** 心跳：30 秒一条注释行，发送失败即摘除死连接。 */
    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : byUser.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (Exception e) {
                    drop(entry.getValue(), emitter);
                }
            }
        }
    }

    private void drop(List<SseEmitter> list, SseEmitter emitter) {
        list.remove(emitter);
    }
}
