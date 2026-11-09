package com.jnclub.clipboard.service;

import com.jnclub.clipboard.dto.ClipboardRecordDto;
import com.jnclub.clipboard.dto.SyncPushRequest;
import com.jnclub.clipboard.dto.SyncPushResponse;
import com.jnclub.clipboard.entity.ClipboardBlob;
import com.jnclub.clipboard.entity.ClipboardRecord;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 附件引用计数维护测试。
 *
 * <p>这组规则直接决定 GC 会不会误删图片：
 * 漏加 → 还在被引用的字节被清掉，所有对端拉取 404；
 * 漏减 → 计数永远归不了零，磁盘上的死图片越积越多。
 */
class ClipboardBlobRefCountTest {

    private static final String USER = "1";
    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);

    private ClipboardRecordMapper recordMapper;
    private ClipboardBlobMapper blobMapper;
    private ClipboardSyncService service;

    @BeforeEach
    void setUp() {
        recordMapper = Mockito.mock(ClipboardRecordMapper.class);
        blobMapper = Mockito.mock(ClipboardBlobMapper.class);
        ClipboardSettingMapper settingMapper = Mockito.mock(ClipboardSettingMapper.class);
        ClipboardUserSeqMapper seqMapper = Mockito.mock(ClipboardUserSeqMapper.class);
        Mockito.when(seqMapper.lockSeq(anyString())).thenReturn(0L);
        Mockito.when(seqMapper.maxVisibleSeq(anyString())).thenReturn(1L);
        ClipboardBlobService blobService = new ClipboardBlobService(blobMapper);
        service = new ClipboardSyncService(recordMapper, settingMapper, blobMapper, seqMapper, blobService);
    }

    private static ClipboardRecordDto image(String syncId, long rev, String sha, Long deletedAt) {
        ClipboardRecordDto d = new ClipboardRecordDto();
        d.setSyncId(syncId);
        d.setContentType("image");
        d.setContent(sha);
        d.setPreview("图片");
        d.setRev(rev);
        d.setDeletedAt(deletedAt);
        return d;
    }

    private static ClipboardRecord imageRow(long id, long rev, String sha, Long deletedAt) {
        ClipboardRecord r = new ClipboardRecord();
        r.setId(id);
        r.setUserId(USER);
        r.setSyncId("sync-1");
        r.setContentType("image");
        r.setContent(sha);
        r.setIsPinned(false);
        r.setTags("[]");
        r.setRev(rev);
        r.setDeletedAt(deletedAt);
        r.setSeq(7L);
        return r;
    }

    /** 供 retain/release 读到的元数据行；引用计数按参数初始。 */
    private ClipboardBlob blobRow(String sha, int refCount) {
        ClipboardBlob blob = new ClipboardBlob();
        blob.setId(1L);
        blob.setUserId(USER);
        blob.setSha256(sha);
        blob.setByteSize(1024L);
        blob.setMime("image/png");
        blob.setRefCount(refCount);
        return blob;
    }

    private SyncPushResponse push(ClipboardRecordDto... records) {
        SyncPushRequest req = new SyncPushRequest();
        req.setRecords(List.of(records));
        return service.push(USER, req);
    }

    @Test
    void 新增图片记录时引用加一() {
        Mockito.when(recordMapper.selectOne(any())).thenReturn(null);
        Mockito.when(blobMapper.selectOne(any())).thenReturn(blobRow(SHA_A, 0));

        push(image("sync-1", 1, SHA_A, null));

        ArgumentCaptor<ClipboardBlob> captor = ArgumentCaptor.forClass(ClipboardBlob.class);
        Mockito.verify(blobMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getRefCount(), "插入引用 digest 的记录，计数必须 +1");
        assertNull(captor.getValue().getZeroedAt(), "重新被引用时清掉归零时间，防止被 GC 误扫");
    }

    @Test
    void 图片换成另一张时旧引用减新引用加() {
        Mockito.when(recordMapper.selectOne(any())).thenReturn(imageRow(42L, 3, SHA_A, null));
        // 先 release 旧图，再 retain 新图：两次 selectOne 依次返回各自的行
        Mockito.when(blobMapper.selectOne(any()))
                .thenReturn(blobRow(SHA_A, 1))
                .thenReturn(blobRow(SHA_B, 0));

        push(image("sync-1", 4, SHA_B, null));

        ArgumentCaptor<ClipboardRecord> recordCaptor = ArgumentCaptor.forClass(ClipboardRecord.class);
        Mockito.verify(recordMapper).updateById(recordCaptor.capture());
        assertEquals(SHA_B, recordCaptor.getValue().getContent());
        // 两次 blob 更新：SHA_A release → 0，SHA_B retain → 1
        ArgumentCaptor<ClipboardBlob> blobCaptor = ArgumentCaptor.forClass(ClipboardBlob.class);
        Mockito.verify(blobMapper, Mockito.times(2)).updateById(blobCaptor.capture());
        List<ClipboardBlob> updates = blobCaptor.getAllValues();
        assertEquals(SHA_A, updates.get(0).getSha256());
        assertEquals(0, updates.get(0).getRefCount());
        assertNotNull(updates.get(0).getZeroedAt(), "归零时必须记下时间，GC 才有依据");
        assertEquals(SHA_B, updates.get(1).getSha256());
        assertEquals(1, updates.get(1).getRefCount());
    }

    @Test
    void 同一张图内容不变时不重记引用() {
        Mockito.when(recordMapper.selectOne(any())).thenReturn(imageRow(42L, 3, SHA_A, null));

        push(image("sync-1", 4, SHA_A, null));

        // rev 4 > 3：记录被接受；引用没变，一个 blob 更新都不该有
        Mockito.verify(blobMapper, Mockito.never()).updateById(any(ClipboardBlob.class));
    }

    @Test
    void 图片记录被墓碑删除时引用减一() {
        Mockito.when(recordMapper.selectOne(any())).thenReturn(imageRow(42L, 3, SHA_A, null));
        Mockito.when(blobMapper.selectOne(any())).thenReturn(blobRow(SHA_A, 1));

        push(image("sync-1", 4, "", 1761602000000L));

        ArgumentCaptor<ClipboardBlob> captor = ArgumentCaptor.forClass(ClipboardBlob.class);
        Mockito.verify(blobMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getRefCount(), "删除图片记录必须释放引用");
        assertNotNull(captor.getValue().getZeroedAt());
    }

    @Test
    void 文本记录不碰引用计数() {
        Mockito.when(recordMapper.selectOne(any())).thenReturn(null);

        ClipboardRecordDto text = new ClipboardRecordDto();
        text.setSyncId("sync-text");
        text.setContentType("text");
        text.setContent("hello");
        text.setRev(1L);
        SyncPushResponse resp = push(text);

        assertTrue(resp.getAccepted().contains("sync-text"));
        Mockito.verify(blobMapper, Mockito.never()).updateById(any(ClipboardBlob.class));
        Mockito.verify(blobMapper, Mockito.never()).selectOne(any());
    }
}
