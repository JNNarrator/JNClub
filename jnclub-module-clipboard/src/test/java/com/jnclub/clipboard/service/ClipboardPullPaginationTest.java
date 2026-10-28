package com.jnclub.clipboard.service;

import com.jnclub.clipboard.dto.SyncPullResponse;
import com.jnclub.clipboard.entity.ClipboardRecord;
import com.jnclub.clipboard.entity.ClipboardSetting;
import com.jnclub.clipboard.mapper.ClipboardBlobMapper;
import com.jnclub.clipboard.mapper.ClipboardRecordMapper;
import com.jnclub.clipboard.mapper.ClipboardSettingMapper;
import com.jnclub.clipboard.mapper.ClipboardUserSeqMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

/**
 * pull 的分页与归并测试。
 *
 * <p>条目和设置共用一个 seq 空间，所以「本页取哪些」必须按 seq 全局排序后决定，
 * 不能先把记录填满再补设置。这里钉住三件事：
 * 跨表归并的顺序、{@code hasMore} 的判定、以及 {@code nextSeq} 必须停在本页最后一条。
 * 最后一条尤其关键——客户端用它做下一轮的 since，多推一格就会永久漏数据。
 */
class ClipboardPullPaginationTest {

    private static final String USER = "1";

    private ClipboardRecordMapper recordMapper;

    private ClipboardSettingMapper settingMapper;

    private ClipboardSyncService service;

    @BeforeEach
    void setUp() {
        recordMapper = Mockito.mock(ClipboardRecordMapper.class);
        settingMapper = Mockito.mock(ClipboardSettingMapper.class);
        ClipboardUserSeqMapper seqMapper = Mockito.mock(ClipboardUserSeqMapper.class);
        ClipboardBlobMapper blobMapper = Mockito.mock(ClipboardBlobMapper.class);
        service = new ClipboardSyncService(recordMapper, settingMapper, blobMapper, seqMapper);
    }

    private static ClipboardRecord record(long seq, String content) {
        ClipboardRecord r = new ClipboardRecord();
        r.setSyncId("rec-" + seq);
        r.setContentType("text");
        r.setContent(content);
        r.setContentHash(0L);
        r.setIsPinned(false);
        r.setTags("[]");
        r.setUseCount(0);
        r.setCreatedAt(0L);
        r.setLastUsedAt(0L);
        r.setSortAt(0L);
        r.setRev(1L);
        r.setSeq(seq);
        return r;
    }

    private static ClipboardSetting setting(long seq, String key) {
        ClipboardSetting s = new ClipboardSetting();
        s.setSettingKey(key);
        s.setSettingValue("v");
        s.setRev(1L);
        s.setSeq(seq);
        return s;
    }

    @Test
    void 条目与设置按seq全局归并() {
        // 记录在 1/3，设置在 2/4 —— 真按 seq 交错
        Mockito.when(recordMapper.selectList(any())).thenReturn(List.of(record(1, "a"), record(3, "c")));
        Mockito.when(settingMapper.selectList(any())).thenReturn(List.of(setting(2, "k2"), setting(4, "k4")));

        SyncPullResponse resp = service.pull(USER, 0, 10);

        // 按 seq 顺序应当是 rec1, set2, rec3, set4
        assertEquals(1L, resp.getRecords().get(0).getSeq());
        assertEquals(2L, resp.getSettings().get(0).getSeq());
        assertEquals(3L, resp.getRecords().get(1).getSeq());
        assertEquals(4L, resp.getSettings().get(1).getSeq());
        assertEquals(4L, resp.getNextSeq());
        assertFalse(resp.getHasMore());
    }

    @Test
    void 超过limit时截断并置hasMore() {
        Mockito.when(recordMapper.selectList(any()))
                .thenReturn(List.of(record(1, "a"), record(2, "b"), record(3, "c")));
        Mockito.when(settingMapper.selectList(any())).thenReturn(List.of());

        SyncPullResponse resp = service.pull(USER, 0, 2);

        assertEquals(2, resp.getRecords().size(), "limit=2 只能返回两条");
        assertEquals(2L, resp.getNextSeq(), "nextSeq 必须停在本页最后一条，不能跳到 3");
        assertTrue(resp.getHasMore(), "还有第三条没发出去，必须告诉客户端续拉");
    }

    @Test
    void 恰好等于limit时hasMore为假() {
        Mockito.when(recordMapper.selectList(any())).thenReturn(List.of(record(1, "a"), record(2, "b")));
        Mockito.when(settingMapper.selectList(any())).thenReturn(List.of());

        SyncPullResponse resp = service.pull(USER, 0, 2);

        assertEquals(2, resp.getRecords().size());
        assertFalse(resp.getHasMore(), "刚好取完时不该让客户端再拉一次空页");
        assertEquals(2L, resp.getNextSeq());
    }

    @Test
    void 空增量时nextSeq保持since() {
        Mockito.when(recordMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(settingMapper.selectList(any())).thenReturn(List.of());

        SyncPullResponse resp = service.pull(USER, 42, 10);

        assertTrue(resp.getRecords().isEmpty());
        assertTrue(resp.getSettings().isEmpty());
        assertEquals(42L, resp.getNextSeq(), "没有增量时游标不能倒退或清零");
        assertFalse(resp.getHasMore());
    }

    @Test
    void limit为0或负数时回落到默认值() {
        Mockito.when(recordMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(settingMapper.selectList(any())).thenReturn(List.of());

        // 不该抛异常，也不该变成「取 0 条」
        assertEquals(0L, service.pull(USER, 0, 0).getNextSeq());
        assertEquals(0L, service.pull(USER, 0, -5).getNextSeq());
    }

    @Test
    void limit超上限时被夹到500() {
        // 用 600 条记录喂进去，确认最多只返回 500 条
        List<ClipboardRecord> many = new ArrayList<>();
        for (int i = 1; i <= 600; i++) {
            many.add(record(i, "x" + i));
        }
        Mockito.when(recordMapper.selectList(any())).thenReturn(many);
        Mockito.when(settingMapper.selectList(any())).thenReturn(List.of());

        SyncPullResponse resp = service.pull(USER, 0, 9999);

        assertEquals(ClipboardSyncService.MAX_PULL_LIMIT, resp.getRecords().size());
        assertTrue(resp.getHasMore());
        assertEquals(500L, resp.getNextSeq());
    }
}
