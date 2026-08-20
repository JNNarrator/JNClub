package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.mapper.BookmarkMapper;
import com.jnclub.bookmark.mapper.DirectoryMapper;
import com.jnclub.bookmark.mapper.NoteMapper;
import com.jnclub.common.cache.CacheKey;
import com.jnclub.common.cache.CacheService;
import com.jnclub.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 数据导入服务 — 收藏 JSON / 浏览器书签 HTML / 便签 Markdown ZIP
 * merge=合并（目录按名复用、条目按 URL/标题去重）/ replace=清空后导入
 */
@Slf4j
@Service
public class ImportService {

    private final BookmarkMapper bookmarkMapper;
    private final NoteMapper noteMapper;
    private final DirectoryMapper directoryMapper;
    private final TagService tagService;
    private final CacheService cacheService;

    public ImportService(BookmarkMapper bookmarkMapper, NoteMapper noteMapper,
                         DirectoryMapper directoryMapper, TagService tagService,
                         CacheService cacheService) {
        this.bookmarkMapper = bookmarkMapper;
        this.noteMapper = noteMapper;
        this.directoryMapper = directoryMapper;
        this.tagService = tagService;
        this.cacheService = cacheService;
    }

    // ============================================================
    // 收藏 JSON 导入（与 ExportService.exportBookmarksJson 格式对应）
    // ============================================================

    @Transactional
    public Map<String, Object> importBookmarks(String jsonContent, String mode) {
        String userId = StpUtil.getLoginIdAsString();
        JSONObject payload;
        try {
            payload = JSONUtil.parseObj(jsonContent);
        } catch (Exception e) {
            throw new BizException("收藏 JSON 解析失败，请确认为导出文件格式");
        }
        if (!"JNClub".equals(payload.getStr("app")) || !"bookmarks".equals(payload.getStr("type"))) {
            throw new BizException("不是 JNClub 收藏导出文件");
        }

        if ("replace".equalsIgnoreCase(mode)) {
            clearUserBookmarks(userId);
        }

        // 建目录：type=1 收藏目录，按名复用；两轮处理保证子目录 parentId 映射正确
        Map<String, Long> idMap = new HashMap<>();
        JSONArray dirs = payload.containsKey("directories") ? payload.getJSONArray("directories") : new JSONArray();
        // 第一轮：一级目录（parentId 为空或不在备份中）
        for (Object o : dirs) {
            JSONObject d = (JSONObject) o;
            Long parentId = d.getLong("parentId");
            boolean parentInBackup = parentId != null && hasDirId(dirs, parentId);
            if (parentId == null || !parentInBackup) {
                Long newId = findOrCreateDir(userId, null, d.getStr("name"), d.getStr("icon"),
                        d.getInt("sortOrder", 0), 1);
                idMap.put(String.valueOf(d.getLong("id")), newId);
            }
        }
        // 第二轮：子目录（parentId 映射到新建 id）
        for (Object o : dirs) {
            JSONObject d = (JSONObject) o;
            Long parentId = d.getLong("parentId");
            boolean parentInBackup = parentId != null && hasDirId(dirs, parentId);
            if (parentId != null && parentInBackup) {
                Long parentNewId = idMap.get(String.valueOf(parentId));
                Long newId = findOrCreateDir(userId, parentNewId, d.getStr("name"), d.getStr("icon"),
                        d.getInt("sortOrder", 0), 1);
                idMap.put(String.valueOf(d.getLong("id")), newId);
            }
        }

        // 导入收藏
        JSONArray bookmarks = payload.containsKey("bookmarks") ? payload.getJSONArray("bookmarks") : new JSONArray();
        int imported = 0, skipped = 0;
        for (Object o : bookmarks) {
            JSONObject b = (JSONObject) o;
            String url = b.getStr("url");
            String title = b.getStr("title");
            if (url == null || url.isBlank()) { skipped++; continue; }
            Long directoryId = idMap.get(String.valueOf(b.getLong("directoryId")));
            if (directoryId == null) directoryId = findOrCreateDir(userId, null, "未分类", null, 0, 1);

            if ("merge".equalsIgnoreCase(mode) && existsBookmark(userId, directoryId, url)) {
                skipped++;
                continue;
            }

            Bookmark bookmark = new Bookmark();
            bookmark.setUserId(userId);
            bookmark.setDirectoryId(directoryId);
            bookmark.setTitle(title == null || title.isBlank() ? fallbackTitle(url) : title.trim());
            bookmark.setUrl(url);
            bookmark.setIcon(b.getStr("icon"));
            bookmark.setSortOrder(b.getInt("sortOrder", 0));
            bookmarkMapper.insert(bookmark);

            // 标签
            JSONArray tags = b.containsKey("tags") ? b.getJSONArray("tags") : new JSONArray();
            if (!tags.isEmpty()) {
                List<String> tagNames = new ArrayList<>();
                for (Object t : tags) tagNames.add(String.valueOf(t));
                tagService.setRelations("bookmark", bookmark.getId(), tagNames);
            }
            imported++;
        }

        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
        cacheService.evictByPrefix(CacheKey.dirPrefix(userId));
        cacheService.evictByPrefix(CacheKey.tagPrefix(userId));
        return Map.of("imported", imported, "skipped", skipped);
    }

    // ============================================================
    // 便签 JSON 导入（全量备份恢复用）
    // 格式：{ app:"JNClub", type:"notes", directories:[...], notes:[{title,content,directoryId,sortOrder,pinned,archived,tags:[...]}] }
    // ============================================================

    @Transactional
    public Map<String, Object> importNotesJson(String jsonContent, String mode) {
        String userId = StpUtil.getLoginIdAsString();
        JSONObject payload;
        try {
            payload = JSONUtil.parseObj(jsonContent);
        } catch (Exception e) {
            throw new BizException("便签数据解析失败，请确认为备份文件");
        }
        if (!"JNClub".equals(payload.getStr("app")) || !"notes".equals(payload.getStr("type"))) {
            throw new BizException("不是 JNClub 便签数据");
        }

        if ("replace".equalsIgnoreCase(mode)) {
            clearUserNotes(userId);
        }

        // 建目录：type=2 便签目录，按名复用；两轮处理保证子目录 parentId 映射正确
        Map<String, Long> idMap = new HashMap<>();
        JSONArray dirs = payload.containsKey("directories") ? payload.getJSONArray("directories") : new JSONArray();
        for (Object o : dirs) {
            JSONObject d = (JSONObject) o;
            Long parentId = d.getLong("parentId");
            boolean parentInBackup = parentId != null && hasDirId(dirs, parentId);
            if (parentId == null || !parentInBackup) {
                Long newId = findOrCreateDir(userId, null, d.getStr("name"), d.getStr("icon"),
                        d.getInt("sortOrder", 0), 2);
                idMap.put(String.valueOf(d.getLong("id")), newId);
            }
        }
        for (Object o : dirs) {
            JSONObject d = (JSONObject) o;
            Long parentId = d.getLong("parentId");
            boolean parentInBackup = parentId != null && hasDirId(dirs, parentId);
            if (parentId != null && parentInBackup) {
                Long parentNewId = idMap.get(String.valueOf(parentId));
                Long newId = findOrCreateDir(userId, parentNewId, d.getStr("name"), d.getStr("icon"),
                        d.getInt("sortOrder", 0), 2);
                idMap.put(String.valueOf(d.getLong("id")), newId);
            }
        }

        // 导入便签
        JSONArray notes = payload.containsKey("notes") ? payload.getJSONArray("notes") : new JSONArray();
        int imported = 0, skipped = 0;
        for (Object o : notes) {
            JSONObject n = (JSONObject) o;
            String title = n.getStr("title");
            if (title == null || title.isBlank()) { skipped++; continue; }
            Long directoryId = idMap.get(String.valueOf(n.getLong("directoryId")));
            if (directoryId == null) directoryId = findOrCreateDir(userId, null, "未分类", null, 0, 2);
            if ("merge".equalsIgnoreCase(mode) && existsNote(userId, directoryId, title.trim())) {
                skipped++;
                continue;
            }

            Note note = new Note();
            note.setUserId(userId);
            note.setDirectoryId(directoryId);
            note.setTitle(title.trim());
            note.setContent(n.getStr("content", ""));
            note.setSortOrder(n.getInt("sortOrder", 0));
            note.setPinned(n.getInt("pinned", 0));
            note.setArchived(n.getInt("archived", 0));
            noteMapper.insert(note);

            JSONArray tags = n.containsKey("tags") ? n.getJSONArray("tags") : new JSONArray();
            if (!tags.isEmpty()) {
                List<String> tagNames = new ArrayList<>();
                for (Object t : tags) tagNames.add(String.valueOf(t));
                tagService.setRelations("note", note.getId(), tagNames);
            }
            imported++;
        }

        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
        cacheService.evictByPrefix(CacheKey.dirPrefix(userId));
        cacheService.evictByPrefix(CacheKey.tagPrefix(userId));
        return Map.of("imported", imported, "skipped", skipped);
    }

    // ============================================================
    // 浏览器书签 HTML 导入（Chrome/Edge 导出格式）
    // ============================================================

    private static final Pattern H3_TAG = Pattern.compile("<H3[^>]*>(.*?)</H3>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern A_TAG = Pattern.compile("<A[^>]*HREF\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</A>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DL_BLOCK = Pattern.compile("<DL[^>]*>.*?</DL>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Transactional
    public Map<String, Object> importBookmarkHtml(String htmlContent, String mode) {
        String userId = StpUtil.getLoginIdAsString();
        if (htmlContent == null || !htmlContent.contains("<DT>")) {
            throw new BizException("不是有效的浏览器书签 HTML 文件");
        }

        if ("replace".equalsIgnoreCase(mode)) {
            clearUserBookmarks(userId);
        }

        // 解析书签树：递归解析 <H3> 文件夹 + <A> 链接
        List<Bookmark> flat = new ArrayList<>();
        Map<String, Long> dirNameOf = new HashMap<>(); // 目录路径 -> id
        parseBookmarkNodes(htmlContent, userId, null, "", flat, dirNameOf);

        int imported = 0, skipped = 0;
        for (Bookmark b : flat) {
            if ("merge".equalsIgnoreCase(mode) && existsBookmark(userId, b.getDirectoryId(), b.getUrl())) {
                skipped++;
                continue;
            }
            bookmarkMapper.insert(b);
            imported++;
        }

        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
        cacheService.evictByPrefix(CacheKey.dirPrefix(userId));
        return Map.of("imported", imported, "skipped", skipped);
    }

    /** 递归解析书签 HTML（线性栈式）：<DT><H3> 文件夹入栈，</DL> 出栈，<DT><A> 归属栈顶目录 */
    private void parseBookmarkNodes(String html, String userId, Long parentId, String folderPath,
                                    List<Bookmark> flat, Map<String, Long> dirNameOf) {
        // 按行解析（Chrome 书签 HTML 每行一个 <DT> 或 <DL>/</DL>）
        List<String> dirStack = new ArrayList<>(); // 当前文件夹路径栈（含完整路径）
        for (String rawLine : html.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // </DL> 出栈：当前文件夹结束
            if (line.startsWith("</DL>")) {
                if (!dirStack.isEmpty()) dirStack.remove(dirStack.size() - 1);
                continue;
            }

            // <DT><H3> 文件夹：入栈（下一层的链接归它）
            Matcher h3 = H3_TAG.matcher(line);
            if (line.contains("<DT>") && h3.find()) {
                String name = stripHtml(h3.group(1));
                if (name.isBlank()) continue;
                // 路径：基于栈顶（父文件夹路径），无栈则基于入口 folderPath
                String basePath = !dirStack.isEmpty() ? dirStack.get(dirStack.size() - 1) : folderPath;
                String path = basePath.isEmpty() ? name : basePath + "/" + name;
                // 父目录：栈顶文件夹（若在文件夹内），否则入口 parentId
                Long parentDirId = !dirStack.isEmpty()
                        ? dirNameOf.get(dirStack.get(dirStack.size() - 1))
                        : parentId;
                Long dirId = dirNameOf.computeIfAbsent(path,
                        k -> findOrCreateDir(userId, parentDirId, name, null, 0, 1));
                dirNameOf.put(path, dirId);
                dirStack.add(path);
                continue;
            }

            // <DT><A HREF> 链接：归属当前目录（栈顶 or 顶层）
            Matcher a = A_TAG.matcher(line);
            if (line.contains("<DT>") && a.find()) {
                String url = a.group(1).trim();
                String title = stripHtml(a.group(2)).trim();
                if (url.isBlank()) continue;
                Long dirId;
                if (!dirStack.isEmpty()) {
                    dirId = dirNameOf.get(dirStack.get(dirStack.size() - 1));
                } else {
                    dirId = parentId != null ? parentId : findOrCreateDir(userId, null, "未分类", null, 0, 1);
                }
                Bookmark b = new Bookmark();
                b.setUserId(userId);
                b.setDirectoryId(dirId);
                b.setTitle(title.isBlank() ? fallbackTitle(url) : title);
                b.setUrl(url);
                b.setIcon(null);
                flat.add(b);
            }
        }
    }

    // ============================================================
    // 便签 Markdown ZIP 导入
    // ============================================================

    @Transactional
    public Map<String, Object> importNotesZip(byte[] zipBytes, String mode) {
        String userId = StpUtil.getLoginIdAsString();
        if ("replace".equalsIgnoreCase(mode)) {
            clearUserNotes(userId);
        }

        Map<String, Long> dirIdOf = new HashMap<>(); // 目录名 -> id
        int imported = 0, skipped = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".md")) continue;
                String md = readZipEntry(zis);
                if (md == null || md.isBlank()) { skipped++; continue; }

                // 解析 frontmatter
                String title = null, directory = null;
                String content = md;
                if (md.startsWith("---")) {
                    int end = md.indexOf("\n---", 4);
                    if (end > 0) {
                        String fm = md.substring(3, end);
                        content = md.substring(end + 4);
                        for (String line : fm.split("\n")) {
                            int colon = line.indexOf(':');
                            if (colon <= 0) continue;
                            String key = line.substring(0, colon).trim();
                            String val = line.substring(colon + 1).trim();
                            if ("title".equals(key)) title = val;
                            else if ("directory".equals(key)) directory = val;
                        }
                    }
                }
                if (title == null || title.isBlank()) title = deriveTitleFromContent(content, entry.getName());

                Long directoryId;
                if (directory != null && !directory.isBlank()) {
                    directoryId = dirIdOf.computeIfAbsent(directory,
                            k -> findOrCreateDir(userId, null, k, null, 0, 2));
                } else {
                    directoryId = findOrCreateDir(userId, null, "未分类", null, 0, 2);
                }

                if ("merge".equalsIgnoreCase(mode) && existsNote(userId, directoryId, title)) {
                    skipped++;
                    continue;
                }

                Note note = new Note();
                note.setUserId(userId);
                note.setDirectoryId(directoryId);
                note.setTitle(title);
                note.setContent(content);
                noteMapper.insert(note);
                imported++;
            }
        } catch (Exception e) {
            throw new BizException("便签 ZIP 解析失败: " + e.getMessage());
        }

        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
        cacheService.evictByPrefix(CacheKey.dirPrefix(userId));
        return Map.of("imported", imported, "skipped", skipped);
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 目录：同 user + type + name + parent 已存在则复用 */
    private Long findOrCreateDir(String userId, Long parentId, String name, String icon, int sortOrder, int type) {
        if (name == null || name.isBlank()) name = "未分类";
        LambdaQueryWrapper<Directory> qw = new LambdaQueryWrapper<Directory>()
                .eq(Directory::getUserId, userId)
                .eq(Directory::getType, type)
                .eq(Directory::getName, name);
        if (parentId == null) qw.isNull(Directory::getParentId);
        else qw.eq(Directory::getParentId, parentId);
        Directory existing = directoryMapper.selectOne(qw);
        if (existing != null) return existing.getId();
        Directory d = new Directory();
        d.setUserId(userId);
        d.setParentId(parentId);
        d.setName(name);
        d.setIcon(icon);
        d.setType(type);
        d.setSortOrder(sortOrder);
        directoryMapper.insert(d);
        return d.getId();
    }

    private boolean existsBookmark(String userId, Long directoryId, String url) {
        return bookmarkMapper.selectCount(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getUserId, userId)
                .eq(Bookmark::getDirectoryId, directoryId)
                .eq(Bookmark::getUrl, url)
                .eq(Bookmark::getDeleted, 0)) > 0;
    }

    private boolean existsNote(String userId, Long directoryId, String title) {
        return noteMapper.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(Note::getDirectoryId, directoryId)
                .eq(Note::getTitle, title)
                .eq(Note::getDeleted, 0)) > 0;
    }

    private void clearUserBookmarks(String userId) {
        bookmarkMapper.delete(new LambdaQueryWrapper<Bookmark>().eq(Bookmark::getUserId, userId));
        directoryMapper.delete(new LambdaQueryWrapper<Directory>()
                .eq(Directory::getUserId, userId).eq(Directory::getType, 1));
    }

    private void clearUserNotes(String userId) {
        noteMapper.delete(new LambdaQueryWrapper<Note>().eq(Note::getUserId, userId));
        directoryMapper.delete(new LambdaQueryWrapper<Directory>()
                .eq(Directory::getUserId, userId).eq(Directory::getType, 2));
    }

    private boolean hasDirId(JSONArray dirs, Long id) {
        for (Object o : dirs) {
            if (((JSONObject) o).getLong("id") != null && ((JSONObject) o).getLong("id").equals(id)) return true;
        }
        return false;
    }

    private String fallbackTitle(String url) {
        try {
            String u = url.replaceAll("^https?://", "");
            int slash = u.indexOf('/');
            return slash > 0 ? u.substring(0, slash) : u;
        } catch (Exception e) {
            return url;
        }
    }

    private String deriveTitleFromContent(String content, String filename) {
        if (content != null) {
            for (String line : content.strip().split("\n")) {
                String t = line.trim();
                if (t.startsWith("# ")) return t.substring(2).trim();
                if (t.startsWith("## ")) return t.substring(3).trim();
            }
        }
        String base = filename.replaceAll("\\.md$", "");
        int lastSlash = base.lastIndexOf('/');
        return lastSlash >= 0 ? base.substring(lastSlash + 1) : base;
    }

    private String readZipEntry(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString(StandardCharsets.UTF_8);
    }

    private String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }
}
