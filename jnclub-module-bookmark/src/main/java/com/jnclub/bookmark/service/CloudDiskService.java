package com.jnclub.bookmark.service;

import cn.hutool.core.codec.Base64;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.mapper.FileMapper;
import com.jnclub.bookmark.mapper.DirectoryMapper;
import com.jnclub.common.cache.CacheKey;
import com.jnclub.common.cache.RedisLock;
import com.jnclub.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 云盘服务 — 分片上传 + 断点续传 + 合并推 dufs + 文件管理
 * <p>
 * 针对小带宽/不稳定网络设计：
 * <ul>
 *   <li>前端按固定 chunkSize（默认 2MB）切片，逐片上传；单片失败仅重传该片，无需整文件重传。</li>
 *   <li>分片先落本地临时目录（幂等，乱序/重复均可接收），服务端落盘即视为已完成——断点续传以云端为准。</li>
 *   <li>complete 时校验完整性并流式合并，一次性 PUT 到 dufs；未成功不产生半成品对象。</li>
 *   <li>合并失败残留仅存在于后端临时目录，由定时任务兜底清理。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudDiskService {

    private final FileMapper fileMapper;
    private final DirectoryMapper directoryMapper;

    private final RedisLock redisLock;

    /** 单分片写锁 TTL：覆盖 2MB 分片落盘时间（默认 30 秒，足够） */
    private static final Duration CHUNK_LOCK_TTL = Duration.ofSeconds(30);
    /** 定时清理锁 TTL */
    private static final Duration SCHEDULED_LOCK_TTL = Duration.ofMinutes(10);

    @Value("${jnclub.dufs.base-url}")
    private String dufsBaseUrl;

    @Value("${jnclub.dufs.username:}")
    private String dufsUser;

    @Value("${jnclub.dufs.password:}")
    private String dufsPass;

    @Value("${jnclub.dufs.disk-path:/jnclub/disk/}")
    private String diskPath;

    @Value("${jnclub.disk.max-size-mb:500}")
    private long maxSizeMb;

    @Value("${jnclub.disk.chunk-size-mb:2}")
    private long chunkSizeMb;

    @Value("${jnclub.disk.temp-dir:${java.io.tmpdir}/jnclub-upload}")
    private String tempDirBase;

    private static final String META_FILE = "meta.json";

    /** 扩展名 → MIME（仅用于下载/列表展示回退；上传时以客户端 content-type 为准） */
    private static final Map<String, String> EXT_MIME = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("zip", "application/zip"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
    );

    // ============================================================
    // 分片上传
    // ============================================================

    /**
     * 初始化上传，返回 uploadId 与分片配置。元信息持久化到临时目录，支持跨请求续传。
     */
    public Map<String, Object> initUpload(String filename, long totalSize, Long directoryId, Integer chunkSizeMb) {
        String userId = userId();
        // 合法性校验
        if (filename == null || filename.isBlank()) throw new BizException("文件名不能为空");
        if (totalSize <= 0) throw new BizException("文件大小必须大于 0");
        long maxBytes = maxSizeMb * 1024 * 1024;
        if (totalSize > maxBytes) {
            throw new BizException("文件超过大小上限 " + maxSizeMb + "MB");
        }
        if (directoryId == null) throw new BizException("请选择云盘目录");
        checkDirOwnership(directoryId, userId);

        int chunkSize = (chunkSizeMb == null || chunkSizeMb <= 0)
                ? (int) chunkSizeMb()
                : chunkSizeMb;
        long chunkBytes = chunkSize * 1024 * 1024L;
        int totalChunks = (int) Math.max(1, (totalSize + chunkBytes - 1) / chunkBytes);

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Path dir = uploadDir(userId, uploadId);

        JSONObject meta = new JSONObject()
                .set("userId", userId)
                .set("filename", filename)
                .set("totalSize", totalSize)
                .set("totalChunks", totalChunks)
                .set("chunkSize", chunkSize)
                .set("directoryId", directoryId)
                .set("mime", contentTypeGuess(filename))
                .set("createTime", System.currentTimeMillis());
        writeMeta(dir, meta);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadId", uploadId);
        result.put("chunkSize", chunkSize);
        result.put("totalChunks", totalChunks);
        result.put("totalSize", totalSize);
        return result;
    }

    /**
     * 保存单个分片。幂等：已存在的分片直接返回成功。不校验顺序，支持乱序/重传。
     */
    public void saveChunk(String uploadId, int chunkIndex, InputStream in) throws IOException {
        Path meta = metaPath(uploadId);
        if (!Files.exists(meta)) throw new BizException("上传任务不存在或已过期");
        JSONObject m = JSONUtil.parseObj(Files.readString(meta, StandardCharsets.UTF_8));
        int totalChunks = m.getInt("totalChunks");
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new BizException("分片序号非法");
        }
        Path dest = chunksDir(uploadId).resolve(chunkIndex + ".part");
        File destFile = dest.toFile();
        if (destFile.exists() && destFile.length() > 0) {
            return; // 已存在，幂等跳过
        }
        // 分布式锁兜底：多实例共享同一临时目录时，防止并发写同一分片的 .tmp 冲突
        String lockKey = CacheKey.lock("chunk", uploadId + ":" + chunkIndex);
        String token = redisLock.tryLock(lockKey, CHUNK_LOCK_TTL);
        if (token == null) {
            return; // 已有实例正在写该分片，幂等返回（前端重传兜底）
        }
        try {
            Path dir = dest.getParent();
            if (!Files.exists(dir)) Files.createDirectories(dir);
            // 先写到临时 .tmp，再原子改名，避免半截文件被当成成功分片
            Path tmp = dest.resolveSibling(chunkIndex + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
                in.transferTo(fos);
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    /**
     * 查询分片进度，返回已成功落盘的分片 index 与期望 totalChunks（供断点续传跳过已传分片）。
     */
    public Map<String, Object> getUploadStatus(String uploadId) throws IOException {
        Path meta = metaPath(uploadId);
        if (!Files.exists(meta)) throw new BizException("上传任务不存在或已过期");
        JSONObject m = JSONUtil.parseObj(Files.readString(meta, StandardCharsets.UTF_8));
        int totalChunks = m.getInt("totalChunks");

        Path chunkDir = chunksDir(uploadId);
        Set<Integer> uploaded = new TreeSet<>();
        if (Files.exists(chunkDir)) {
            try (Stream<Path> s = Files.list(chunkDir)) {
                for (Path p : s.collect(Collectors.toList())) {
                    String name = p.getFileName().toString();
                    if (name.endsWith(".part")) {
                        uploaded.add(Integer.parseInt(name.substring(0, name.length() - 5)));
                    }
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadId", uploadId);
        result.put("totalChunks", totalChunks);
        result.put("uploaded", uploaded);
        return result;
    }

    /**
     * 合并分片，推入 dufs，登记 t_file，清理临时目录；返回文件记录。
     */
    public FileRecord complete(String uploadId) throws IOException {
        Path meta = metaPath(uploadId);
        if (!Files.exists(meta)) throw new BizException("上传任务不存在或已过期");
        JSONObject m = JSONUtil.parseObj(Files.readString(meta, StandardCharsets.UTF_8));

        String userId = m.getStr("userId");
        String filename = m.getStr("filename");
        long totalSize = m.getLong("totalSize");
        int totalChunks = m.getInt("totalChunks");
        int chunkSize = m.getInt("chunkSize");
        long directoryId = m.getLong("directoryId");
        String mime = m.getStr("mime");

        Path chunkDir = chunksDir(uploadId);

        // 1. 校验分片完整性：数量齐全，且累计大小 == totalSize
        List<Integer> present = new ArrayList<>();
        long sum = 0;
        if (Files.exists(chunkDir)) {
            try (Stream<Path> s = Files.list(chunkDir)) {
                for (Path p : s.collect(Collectors.toList())) {
                    String name = p.getFileName().toString();
                    if (name.endsWith(".part")) {
                        int idx = Integer.parseInt(name.substring(0, name.length() - 5));
                        present.add(idx);
                        sum += Files.size(p);
                    }
                }
            }
        }
        if (present.size() != totalChunks) {
            throw new BizException("分片不完整，已接收 " + present.size() + "/" + totalChunks + "，请续传缺失分片");
        }
        if (sum != totalSize) {
            throw new BizException("分片大小校验失败，请重新上传");
        }
        Collections.sort(present);

        // 2. 合并到临时完整文件（按 index 顺序流式拼接）
        Path merged = chunkDir.getParent().resolve("merged_" + uploadId);
        try (FileOutputStream out = new FileOutputStream(merged.toFile())) {
            for (int idx : present) {
                Files.copy(chunkDir.resolve(idx + ".part"), out);
            }
        }

        // 3. 生成 dufs 存储路径并上传
        String ext = getExtension(filename);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String storedKey = diskPath + datePath + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        boolean uploaded = pushToDufs(storedKey, merged, mime);
        if (!uploaded) {
            Files.deleteIfExists(merged);
            throw new BizException("文件保存失败，请稍后重试");
        }

        // 4. 入库
        FileRecord record = new FileRecord();
        record.setDirectoryId(directoryId);
        record.setUserId(userId);
        record.setOriginalName(filename);
        record.setStoredKey(storedKey);
        record.setUrl("/api/files" + storedKey);
        record.setSize(totalSize);
        record.setMime(mime);
        fileMapper.insert(record);

        // 5. 清理临时目录（合并且推送成功后才删）
        try {
            Files.deleteIfExists(merged);
            deleteRecursively(chunkDir.getParent());
        } catch (IOException e) {
            log.warn("清理上传临时目录失败: {}", uploadId, e);
        }

        log.info("云盘文件完成: id={} name={} size={} key={}", record.getId(), filename, totalSize, storedKey);
        return record;
    }

    // ============================================================
    // 文件管理（列表 / 删除 / 下载 / 定时清理）
    // ============================================================

    public List<FileRecord> listFiles(Long directoryId) {
        String userId = userId();
        return fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getDirectoryId, directoryId)
                .eq(FileRecord::getUserId, userId)
                .eq(FileRecord::getDeleted, 0)
                .orderByAsc(FileRecord::getSortOrder)
                .orderByDesc(FileRecord::getCreateTime));
    }

    /**
     * 云盘文件排序：批量更新同一目录下文件的 sortOrder
     *
     * @param sortList [{id, sortOrder}, ...]
     */
    public void updateSortOrder(List<Map<String, Object>> sortList) {
        String userId = userId();
        for (Map<String, Object> item : sortList) {
            Long fileId = item.get("id") == null
                    ? null : Long.parseLong(String.valueOf(item.get("id")));
            Integer sortOrder = item.get("sortOrder") == null
                    ? 0 : Integer.parseInt(String.valueOf(item.get("sortOrder")));
            if (fileId == null) continue;
            FileRecord record = fileMapper.selectById(fileId);
            if (record == null || !record.getUserId().equals(userId)) {
                throw new BizException("文件不存在");
            }
            FileRecord update = new FileRecord();
            update.setId(fileId);
            update.setSortOrder(sortOrder);
            fileMapper.updateById(update);
        }
    }

    /**
     * 软删除文件：进入回收站（dufs 对象暂不删除，恢复后立即可用）
     */
    public void deleteFile(Long id) {
        String userId = userId();
        FileRecord record = fileMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException("文件不存在");
        }
        FileRecord update = new FileRecord();
        update.setId(id);
        update.setDeleted(1);
        fileMapper.updateById(update);
        log.info("云盘文件移入回收站: id={} name={}", id, record.getOriginalName());
    }

    /** 批量软删除文件 */
    @Transactional
    public void deleteFilesBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            deleteFile(id);
        }
    }

    /**
     * 永久删除文件（回收站清空/到期清理用）：dufs 对象 + t_file 记录
     */
    public void permanentlyDeleteFile(Long id) {
        String userId = userId();
        FileRecord record = fileMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException("文件不存在");
        }
        boolean ok = deleteDufsFile(record.getStoredKey());
        if (!ok) {
            log.warn("云盘删除 dufs 失败，key={}", record.getStoredKey());
        }
        fileMapper.deleteById(id);
        log.info("云盘文件永久删除: id={} name={}", id, record.getOriginalName());
    }

    /**
     * 从回收站恢复文件
     */
    public void restoreFile(Long id) {
        String userId = userId();
        FileRecord record = fileMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException("文件不存在");
        }
        if (record.getDeleted() == null || record.getDeleted() != 1) {
            throw new BizException("文件不在回收站中");
        }
        // 校验原目录仍存在且为云盘目录（被删除则恢复失败）
        checkDirOwnership(record.getDirectoryId(), userId);
        FileRecord update = new FileRecord();
        update.setId(id);
        update.setDeleted(0);
        fileMapper.updateById(update);
    }

    /** 回收站文件列表（deleted=1，倒序） */
    public List<FileRecord> listRecycle(String userId) {
        return fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getUserId, userId)
                .eq(FileRecord::getDeleted, 1)
                .orderByDesc(FileRecord::getCreateTime));
    }

    /** 无鉴权永久删除（回收站定时清理用，跳过登录态校验） */
    public void purgeByIdNoAuth(Long id) {
        FileRecord record = fileMapper.selectById(id);
        if (record == null) return;
        boolean ok = deleteDufsFile(record.getStoredKey());
        if (!ok) {
            log.warn("云盘删除 dufs 失败，key={}", record.getStoredKey());
        }
        fileMapper.deleteById(id);
        log.info("云盘文件永久删除(定时): id={} name={}", id, record.getOriginalName());
    }

    /** 重命名文件（仅改 originalName，不动 dufs 物理路径） */
    public void renameFile(Long id, String name) {
        String userId = userId();
        if (name == null || name.isBlank()) throw new BizException("文件名不能为空");
        if (name.length() > 500) throw new BizException("文件名过长（最多 500 字符）");
        FileRecord record = fileMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException("文件不存在");
        }
        FileRecord update = new FileRecord();
        update.setId(id);
        update.setOriginalName(name.trim());
        fileMapper.updateById(update);
    }

    /** 移动文件到其他云盘目录 */
    public void moveFile(Long id, Long directoryId) {
        String userId = userId();
        FileRecord record = fileMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException("文件不存在");
        }
        if (directoryId == null) throw new BizException("请选择目标目录");
        checkDirOwnership(directoryId, userId);
        FileRecord update = new FileRecord();
        update.setId(id);
        update.setDirectoryId(directoryId);
        fileMapper.updateById(update);
    }

    /** 批量移动文件 */
    @Transactional
    public void moveFilesBatch(List<Long> ids, Long directoryId) {
        if (ids == null || ids.isEmpty()) return;
        if (directoryId == null) throw new BizException("请选择目标目录");
        checkDirOwnership(directoryId, userId());
        for (Long id : ids) {
            moveFile(id, directoryId);
        }
    }

    /**
     * 获取下载文件信息（用于通过 /api/files 代理 + Content-Disposition 恢复原始文件名）
     */
    public FileRecord getFile(Long id) {
        String userId = userId();
        FileRecord record = fileMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException("文件不存在");
        }
        return record;
    }

    /** 按 ids 批量取文件（校验归属当前用户），供打包下载等场景 */
    public List<FileRecord> listFilesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String userId = userId();
        return fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getUserId, userId)
                .eq(FileRecord::getDeleted, 0)
                .in(FileRecord::getId, ids));
    }

    // ============================================================
    // 定时清理孤儿临时目录（中断/失败残留）
    // ============================================================

    /**
     * 清理超过指定天数的孤儿上传临时目录，在启动与定时任务中被调用。
     * @param maxAgeDays 最大保留天数
     */
    public int cleanTempDirs(int maxAgeDays) {
        Path base = Paths.get(tempDirBase);
        if (!Files.exists(base)) return 0;
        long cutoff = System.currentTimeMillis() - maxAgeDays * 24L * 3600 * 1000;
        int removed = 0;
        try (Stream<Path> users = Files.list(base)) {
            for (Path userDir : users.collect(Collectors.toList())) {
                if (!Files.isDirectory(userDir)) continue;
                try (Stream<Path> uploads = Files.list(userDir)) {
                    for (Path uploadDir : uploads.collect(Collectors.toList())) {
                        Path meta = uploadDir.resolve(META_FILE);
                        if (Files.exists(meta)) {
                            try {
                                JSONObject m = JSONUtil.parseObj(Files.readString(meta, StandardCharsets.UTF_8));
                                long createTime = m.getLong("createTime", 0L);
                                if (createTime > 0 && System.currentTimeMillis() - createTime > cutoff) {
                                    deleteRecursively(uploadDir);
                                    removed++;
                                }
                            } catch (Exception e) {
                                log.warn("清理临时目录异常 {}", uploadDir, e);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("清理临时目录失败", e);
        }
        if (removed > 0) log.info("已清理孤儿上传临时目录 {} 个", removed);
        return removed;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 完整分片字节数（配置换算，dir 元信息持久化用） */
    private long chunkSizeMb() {
        return chunkSizeMb <= 0 ? 2 : chunkSizeMb;
    }

    private String userId() {
        return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
    }

    private void checkDirOwnership(Long directoryId, String userId) {
        var dir = directoryMapper.selectById(directoryId);
        if (dir == null || !dir.getUserId().equals(userId)) {
            throw new BizException("云盘目录不存在");
        }
        if (dir.getType() == null || dir.getType() != 3) {
            throw new BizException("目标目录不是云盘目录");
        }
    }

    private Path uploadDir(String userId, String uploadId) {
        return Paths.get(tempDirBase).resolve(userId).resolve(uploadId);
    }

    private Path chunksDir(String uploadId) {
        return Paths.get(tempDirBase).resolve(userId()).resolve(uploadId).resolve("chunks");
    }

    private Path metaPath(String uploadId) {
        return Paths.get(tempDirBase).resolve(userId()).resolve(uploadId).resolve(META_FILE);
    }

    private void writeMeta(Path dir, JSONObject meta) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(META_FILE), meta.toJSONString(0), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException("初始化上传失败");
        }
    }

    /** 上传合并文件到 dufs（一次性 PUT，带 Basic Auth） */
    private boolean pushToDufs(String storedKey, Path merged, String mime) throws IOException {
        String uploadUrl = dufsBaseUrl + storedKey;
        var req = HttpRequest.put(uploadUrl)
                .header("Content-Type", mime == null || mime.isBlank() ? "application/octet-stream" : mime)
                .body(Files.readAllBytes(merged));
        if (dufsUser != null && !dufsUser.isBlank()) {
            String auth = dufsUser + ":" + dufsPass;
            req.header("Authorization", "Basic " +
                    Base64.encode(auth.getBytes(StandardCharsets.UTF_8)));
        }
        HttpResponse response = req.execute();
        boolean ok = response.getStatus() == 200 || response.getStatus() == 201;
        if (!ok) {
            log.error("云盘 dufs 上传失败: status={} body={} url={}",
                    response.getStatus(), response.body(), uploadUrl);
        }
        return ok;
    }

    private boolean deleteDufsFile(String storedKey) {
        try {
            String deleteUrl = dufsBaseUrl + "/" + storedKey;
            var req = HttpRequest.delete(deleteUrl);
            if (dufsUser != null && !dufsUser.isBlank()) {
                String auth = dufsUser + ":" + dufsPass;
                req.header("Authorization", "Basic " +
                        Base64.encode(auth.getBytes(StandardCharsets.UTF_8)));
            }
            HttpResponse response = req.execute();
            int status = response.getStatus();
            return status == 200 || status == 204 || status == 404;
        } catch (Exception e) {
            log.error("云盘 dufs 删除失败: key={}", storedKey, e);
            return false;
        }
    }

    private static String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }

    private static String contentTypeGuess(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        if (dot >= 0) {
            String ext = filename.substring(dot + 1).toLowerCase();
            if (EXT_MIME.containsKey(ext)) return EXT_MIME.get(ext);
        }
        return "application/octet-stream";
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.deleteIfExists(p);
            }
        }
    }

    /**
     * 每天凌晨清理超过 1 天的孤儿上传临时目录，防止中断/失败上传残留占用磁盘。
     */
    @Scheduled(cron = "0 20 3 * * ?")
    public void scheduledCleanTemp() {
        String lockKey = CacheKey.lock("scheduled", "clouddisk-temp-clean");
        String token = redisLock.tryLock(lockKey, SCHEDULED_LOCK_TTL);
        if (token == null) {
            log.info("云盘临时目录定时清理已被其他实例执行，跳过");
            return;
        }
        try {
            cleanTempDirs(1);
        } catch (Exception e) {
            log.error("定时清理云盘临时目录失败", e);
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }
}
