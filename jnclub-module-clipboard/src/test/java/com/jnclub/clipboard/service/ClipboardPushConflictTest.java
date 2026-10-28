package com.jnclub.clipboard.service;

import com.jnclub.clipboard.dto.ClipboardRecordDto;
import com.jnclub.clipboard.dto.ClipboardSettingDto;
import com.jnclub.clipboard.dto.SyncPushRequest;
import com.jnclub.clipboard.dto.SyncPushResponse;
import com.jnclub.clipboard.entity.ClipboardRecord;
import com.jnclub.clipboard.entity.ClipboardSetting;
import com.jnclub.clipboard.mapper.ClipboardBlobMapper;
import com.jnclub.clipboard.mapper.ClipboardRecordMapper;
import com.jnclub.clipboard.mapper.ClipboardSettingMapper;
import com.jnclub.clipboard.mapper.ClipboardUserSeqMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * push 的乐观锁判定测试。
 *
 * <p>四条分支各有代价，必须逐条钉住：
 * 该接受却判冲突 → 用户的编辑被静默丢弃；
 * 该判冲突却接受 → 后写的设备覆盖别人的修改且无人知晓；
 * 幂等重放被当成新写入 → 每次网络重试都在服务端堆一条重复记录。
 */
class ClipboardPushConflictTest {

    private static final String USER = "1";

    private ClipboardRecordMapper recordMapper;

    private ClipboardSettingMapper settingMapper;

    private ClipboardUserSeqMapper seqMapper;

    private ClipboardSyncService service;

    @BeforeEach
    void setUp() {
        recordMapper = Mockito.mock(ClipboardRecordMapper.class);
        settingMapper = Mockito.mock(ClipboardSettingMapper.class);
        seqMapper = Mockito.mock(ClipboardUserSeqMapper.class);
        ClipboardBlobMapper blobMapper = Mockito.mock(ClipboardBlobMapper.class);

        // 分配器：从 0 开始，锁定返回 0
        Mockito.when(seqMapper.lockSeq(anyString())).thenReturn(0L);
        // push 末尾回报的游标
        Mockito.when(seqMapper.maxVisibleSeq(anyString())).thenReturn(1L);

        service = new ClipboardSyncService(recordMapper, settingMapper, blobMapper, seqMapper);
    }

    private static ClipboardRecordDto dto(String syncId, long rev, String content) {
        ClipboardRecordDto d = new ClipboardRecordDto();
        d.setSyncId(syncId);
        d.setContentType("text");
        d.setContent(content);
        d.setContentHash((long) content.hashCode());
        d.setRev(rev);
        return d;
    }

    private static ClipboardRecord serverRow(long id, long rev, String content) {
        ClipboardRecord r = new ClipboardRecord();
        r.setId(id);
        r.setUserId(USER);
        r.setSyncId("sync-1");
        r.setContentType("text");
        r.setContent(content);
        r.setIsPinned(false);
        r.setTags("[]");
        r.setRev(rev);
        r.setSeq(7L);
        return r;
    }

    private SyncPushResponse push(ClipboardRecordDto... records) {
        SyncPushRequest req = new SyncPushRequest();
        req.setRecords(List.of(records));
        return service.push(USER, req);
    }

    @Test
    void 服务端没有该条目时插入并分配seq() {
        Mockito.when(recordMapper.selectOne(any())).thenReturn(null);

        SyncPushResponse resp = push(dto("sync-1", 1, "hello"));

        ArgumentCaptor<ClipboardRecord> captor = ArgumentCaptor.forClass(ClipboardRecord.class);
        Mockito.verify(recordMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getSeq(), "seq 从 1 开始分配");
        assertEquals(USER, captor.getValue().getUserId(), "user_id 必须来自登录态，不能来自请求体");
        assertEquals(List.of("sync-1"), resp.getAccepted());
        assertTrue(resp.getConflicts().isEmpty());
    }

    @Test
    void rev更大时覆盖() {
        Mockito.when(recordMapper.selectOne(any())).thenReturn(serverRow(42L, 3, "old"));

        SyncPushResponse resp = push(dto("sync-1", 4, "new"));

        ArgumentCaptor<ClipboardRecord> captor = ArgumentCaptor.forClass(ClipboardRecord.class);
        Mockito.verify(recordMapper).updateById(captor.capture());
        assertEquals(42L, captor.getValue().getId(), "按主键更新，不能插新行");
        assertEquals("new", captor.getValue().getContent());
        assertEquals(List.of("sync-1"), resp.getAccepted());
    }

    @Test
    void rev相等且内容相同时幂等重放不消耗seq() {
        Mockito.when(recordMapper.selectOne(any())).thenReturn(serverRow(42L, 3, "same"));

        SyncPushResponse resp = push(dto("sync-1", 3, "same"));

        assertEquals(List.of("sync-1"), resp.getAccepted());
        assertTrue(resp.getConflicts().isEmpty());
        Mockito.verify(recordMapper, Mockito.never()).insert(any(ClipboardRecord.class));
        Mockito.verify(recordMapper, Mockito.never()).updateById(any(ClipboardRecord.class));
    }

    @Test
    void rev相等但内容不同判冲突并回传服务端版本() {
        Mockito.when(recordMapper.selectOne(any())).thenReturn(serverRow(42L, 3, "server-wins"));

        SyncPushResponse resp = push(dto("sync-1", 3, "client-differs"));

        assertTrue(resp.getAccepted().isEmpty());
        assertEquals(1, resp.getConflicts().size());
        assertEquals(3L, resp.getConflicts().get(0).getServerRev());
        assertEquals("server-wins", resp.getConflicts().get(0).getServerRecord().getContent());
        Mockito.verify(recordMapper, Mockito.never()).updateById(any(ClipboardRecord.class));
    }

    @Test
    void rev更小时判冲突不覆盖() {
        Mockito.when(recordMapper.selectOne(any())).thenReturn(serverRow(42L, 5, "newer"));

        SyncPushResponse resp = push(dto("sync-1", 2, "stale"));

        assertTrue(resp.getAccepted().isEmpty());
        assertEquals(1, resp.getConflicts().size());
        assertEquals(5L, resp.getConflicts().get(0).getServerRev());
        Mockito.verify(recordMapper, Mockito.never()).updateById(any(ClipboardRecord.class));
    }

    @Test
    void 设置白名单外的键被拒并回报() {
        SyncPushRequest req = new SyncPushRequest();
        ClipboardSettingDto bad = new ClipboardSettingDto();
        bad.setKey("app.window_width");
        bad.setValue("1200");
        bad.setRev(1L);
        req.setSettings(List.of(bad));

        SyncPushResponse resp = service.push(USER, req);

        assertEquals(List.of("app.window_width"), resp.getRejectedSettings());
        assertTrue(resp.getAccepted().isEmpty());
        Mockito.verify(settingMapper, Mockito.never()).insert(any(ClipboardSetting.class));
    }

    @Test
    void 设置旧版本被静默丢弃() {
        ClipboardSetting existing = new ClipboardSetting();
        existing.setId(9L);
        existing.setUserId(USER);
        existing.setSettingKey("app.theme");
        existing.setSettingValue("mica");
        existing.setRev(5L);
        Mockito.when(settingMapper.selectOne(any())).thenReturn(existing);

        SyncPushRequest req = new SyncPushRequest();
        ClipboardSettingDto stale = new ClipboardSettingDto();
        stale.setKey("app.theme");
        stale.setValue("macos");
        stale.setRev(2L);
        req.setSettings(List.of(stale));

        SyncPushResponse resp = service.push(USER, req);

        assertTrue(resp.getAccepted().isEmpty(), "旧版本不回报 accepted");
        assertTrue(resp.getRejectedSettings().isEmpty(), "旧版本不属于「白名单拒绝」");
        Mockito.verify(settingMapper, Mockito.never()).updateById(any(ClipboardSetting.class));
    }

    @Test
    void 设置墓碑能落库() {
        Mockito.when(settingMapper.selectOne(any())).thenReturn(null);

        SyncPushRequest req = new SyncPushRequest();
        ClipboardSettingDto deleted = new ClipboardSettingDto();
        deleted.setKey("app.theme");
        deleted.setValue(null);
        deleted.setDeletedAt(1761602000000L);
        deleted.setRev(2L);
        req.setSettings(List.of(deleted));

        service.push(USER, req);

        ArgumentCaptor<ClipboardSetting> captor = ArgumentCaptor.forClass(ClipboardSetting.class);
        Mockito.verify(settingMapper).insert(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt(), "删除必须留墓碑，否则对端永远不知道它没了");
        assertEquals(List.of("app.theme"), List.of(captor.getValue().getSettingKey()));
    }

    @Test
    void 空syncId的记录被跳过而不是写入脏数据() {
        SyncPushRequest req = new SyncPushRequest();
        ClipboardRecordDto blank = new ClipboardRecordDto();
        blank.setSyncId("  ");
        blank.setContent("x");
        req.setRecords(List.of(blank));

        SyncPushResponse resp = service.push(USER, req);

        assertTrue(resp.getAccepted().isEmpty());
        Mockito.verify(recordMapper, Mockito.never()).insert(any(ClipboardRecord.class));
    }
}
