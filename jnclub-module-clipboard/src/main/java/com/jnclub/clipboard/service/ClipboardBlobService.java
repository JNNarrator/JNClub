package com.jnclub.clipboard.service;

import cn.hutool.core.codec.Base64;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jnclub.clipboard.entity.ClipboardBlob;
import com.jnclub.clipboard.mapper.ClipboardBlobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 剪贴板附件（图片）实体的存取。
 *
 * <h2>为什么按 sha256 存</h2>
 * 同一张截图会出现在多个条目里，也会被多台设备各自推送一次。用内容哈希做键，
 * 重复上传不产生副本（dufs 的 PUT 是覆盖语义），下载方也不需要知道是谁上传的。
 *
 * <h2>为什么不直接让客户端读 dufs</h2>
 * dufs 端口不对外，只有网关能访问它；附件也要跟着 Sa-Token 的登录态走，
 * 否则知道哈希就能拿到别人的截图。所以读写都经过这里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClipboardBlobService {

    /** 单张图片的上限，与桌面端 WebDAV 同步的图片阈值保持一致 */
    public static final long MAX_BLOB_BYTES = 20L * 1024 * 1024;

    private final ClipboardBlobMapper blobMapper;

    @Value("${jnclub.dufs.base-url}")
    private String dufsBaseUrl;

    @Value("${jnclub.dufs.clipboard-path:/jnclub/clipboard/}")
    private String clipboardPath;

    @Value("${jnclub.dufs.username:}")
    private String dufsUser;

    @Value("${jnclub.dufs.password:}")
    private String dufsPass;

    /**
     * dufs 上的相对路径：{@code <clipboard-path><userId>/<sha 前两位>/<sha256>}。
     *
     * <p>分两级目录是为了别让一个用户的上千张图挤在一个目录里；dufs 是文件系统，
     * 单目录条目过多时列目录会明显变慢。
     */
    public String relativePath(String userId, String sha256) {
        String prefix = sha256.length() >= 2 ? sha256.substring(0, 2) : "xx";
        String root = clipboardPath == null || clipboardPath.isBlank() ? "/jnclub/clipboard/" : clipboardPath;
        if (!root.endsWith("/")) {
            root = root + "/";
        }
        return root + safeSegment(userId) + "/" + prefix + "/" + sha256;
    }

    /** userId 进路径前只保留安全字符，避免 `../` 之类拼进 URL */
    static String safeSegment(String userId) {
        if (userId == null || userId.isBlank()) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder(userId.length());
        for (char c : userId.toCharArray()) {
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            sb.append(ok ? c : '_');
        }
        return sb.toString();
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，走不到这里
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean isSha256Hex(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /**
     * 落库并写入 dufs。
     *
     * @return 实际写入的 sha256（由字节算出，不信任客户端给的路径参数）
     */
    public String store(String userId, byte[] bytes, String mime) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("附件为空");
        }
        if (bytes.length > MAX_BLOB_BYTES) {
            throw new IllegalArgumentException("附件超过 " + (MAX_BLOB_BYTES / 1024 / 1024) + "MB");
        }
        String sha = sha256Hex(bytes);
        String relative = relativePath(userId, sha);

        var req = HttpRequest.put(dufsBaseUrl + relative)
                .header("Content-Type", mime == null || mime.isBlank() ? "application/octet-stream" : mime)
                .body(bytes);
        if (dufsUser != null && !dufsUser.isBlank()) {
            String auth = dufsUser + ":" + dufsPass;
            req.header("Authorization", "Basic "
                    + Base64.encode(auth.getBytes(StandardCharsets.UTF_8)));
        }
        try (HttpResponse response = req.execute()) {
            int status = response.getStatus();
            if (status != 200 && status != 201 && status != 204) {
                log.error("dufs 附件上传失败: status={}, body={}", status, response.body());
                throw new IllegalStateException("附件上传失败");
            }
        }

        ClipboardBlob existing = blobMapper.selectOne(new QueryWrapper<ClipboardBlob>()
                .eq("user_id", userId)
                .eq("sha256", sha)
                .last("LIMIT 1"));
        if (existing == null) {
            ClipboardBlob entity = new ClipboardBlob();
            entity.setUserId(userId);
            entity.setSha256(sha);
            entity.setByteSize((long) bytes.length);
            entity.setMime(mime == null ? "" : mime);
            entity.setRefCount(0);
            blobMapper.insert(entity);
        } else if (existing.getByteSize() == null || existing.getByteSize() == 0L) {
            existing.setByteSize((long) bytes.length);
            existing.setMime(mime == null ? "" : mime);
            blobMapper.updateById(existing);
        }
        return sha;
    }

    /**
     * 读附件。返回 null 表示 dufs 上没有这份内容（例如被 GC 清掉）。
     */
    public BlobPayload load(String userId, String sha256) {
        if (!isSha256Hex(sha256)) {
            throw new IllegalArgumentException("附件标识不是 sha256");
        }
        String relative = relativePath(userId, sha256);
        var req = HttpRequest.get(dufsBaseUrl + relative);
        if (dufsUser != null && !dufsUser.isBlank()) {
            String auth = dufsUser + ":" + dufsPass;
            req.header("Authorization", "Basic "
                    + Base64.encode(auth.getBytes(StandardCharsets.UTF_8)));
        }
        try (HttpResponse response = req.execute()) {
            if (response.getStatus() != 200) {
                log.warn("dufs 附件读取失败: sha={}, status={}", sha256, response.getStatus());
                return null;
            }
            byte[] body = response.bodyBytes();
            ClipboardBlob meta = blobMapper.selectOne(new QueryWrapper<ClipboardBlob>()
                    .eq("user_id", userId)
                    .eq("sha256", sha256)
                    .last("LIMIT 1"));
            String mime = meta == null || meta.getMime() == null || meta.getMime().isBlank()
                    ? "application/octet-stream"
                    : meta.getMime();
            return new BlobPayload(body, mime);
        }
    }

    public record BlobPayload(byte[] bytes, String mime) {
    }
}
