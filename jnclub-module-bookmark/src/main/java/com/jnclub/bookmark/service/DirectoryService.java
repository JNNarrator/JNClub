package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.mapper.BookmarkMapper;
import com.jnclub.bookmark.mapper.DirectoryMapper;
import com.jnclub.bookmark.mapper.FileMapper;
import com.jnclub.bookmark.mapper.NoteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 目录服务 — 支持收藏夹(type=1)与便签(type=2)共用
 */
@Service
public class DirectoryService extends ServiceImpl<DirectoryMapper, Directory> {

    @Autowired
    private BookmarkMapper bookmarkMapper;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private FileMapper fileMapper;

    public List<Directory> getDirectoryTree(Integer type) {
        String userId = StpUtil.getLoginIdAsString();
        LambdaQueryWrapper<Directory> wrapper = new LambdaQueryWrapper<Directory>()
                .eq(Directory::getUserId, userId)
                .orderByAsc(Directory::getSortOrder);
        if (type != null) {
            wrapper.eq(Directory::getType, type);
        }
        List<Directory> directories = list(wrapper);
        return buildTree(directories, null);
    }

    public Directory createDirectory(Directory directory) {
        String userId = StpUtil.getLoginIdAsString();
        directory.setUserId(userId);
        if (directory.getType() == null) {
            directory.setType(1);
        }
        save(directory);
        return directory;
    }

    public void renameDirectory(Long id, String name, String icon) {
        String userId = StpUtil.getLoginIdAsString();
        Directory directory = getById(id);
        if (directory == null || !directory.getUserId().equals(userId)) {
            throw new RuntimeException("目录不存在");
        }
        directory.setName(name);
        if (icon != null) {
            directory.setIcon(icon);
        }
        updateById(directory);
    }

    public Map<String, Long> getContentCount(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Directory directory = getById(id);
        if (directory == null || !directory.getUserId().equals(userId)) {
            throw new RuntimeException("目录不存在");
        }

        List<Long> allIds = new ArrayList<>();
        collectDescendantIds(id, userId, allIds);
        allIds.add(id);

        Long bookmarkCount = bookmarkMapper.selectCount(
                new LambdaQueryWrapper<Bookmark>()
                        .in(Bookmark::getDirectoryId, allIds)
                        .eq(Bookmark::getUserId, userId));

        Long noteCount = noteMapper.selectCount(
                new LambdaQueryWrapper<Note>()
                        .in(Note::getDirectoryId, allIds)
                        .eq(Note::getUserId, userId));

        Long fileCount = fileMapper.selectCount(
                new LambdaQueryWrapper<FileRecord>()
                        .in(FileRecord::getDirectoryId, allIds)
                        .eq(FileRecord::getUserId, userId));

        Map<String, Long> result = new HashMap<>();
        result.put("bookmarkCount", bookmarkCount);
        result.put("noteCount", noteCount);
        result.put("fileCount", fileCount);
        return result;
    }

    @Transactional
    public void deleteDirectory(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Directory directory = getById(id);
        if (directory == null || !directory.getUserId().equals(userId)) {
            throw new RuntimeException("目录不存在");
        }

        List<Long> descendantIds = new ArrayList<>();
        collectDescendantIds(id, userId, descendantIds);
        descendantIds.add(id);

        // 删除保护：自身+所有后代目录存在便签/收藏时禁止删除
        Long bookmarkCount = bookmarkMapper.selectCount(
                new LambdaQueryWrapper<Bookmark>()
                        .in(Bookmark::getDirectoryId, descendantIds)
                        .eq(Bookmark::getUserId, userId));
        Long noteCount = noteMapper.selectCount(
                new LambdaQueryWrapper<Note>()
                        .in(Note::getDirectoryId, descendantIds)
                        .eq(Note::getUserId, userId));
        Long fileCount = fileMapper.selectCount(
                new LambdaQueryWrapper<FileRecord>()
                        .in(FileRecord::getDirectoryId, descendantIds)
                        .eq(FileRecord::getUserId, userId));
        if (bookmarkCount > 0 || noteCount > 0 || fileCount > 0) {
            throw new com.jnclub.common.exception.BizException("目录下存在条目，请先清空或移动后再删除");
        }


        deleteChildren(id, userId);
        removeById(id);
    }

    @Transactional
    public void updateSortOrder(List<Map<String, Object>> sortList) {
        String userId = StpUtil.getLoginIdAsString();
        for (Map<String, Object> item : sortList) {
            Long id = Long.parseLong(item.get("id").toString());
            Integer sortOrder = Integer.parseInt(item.get("sortOrder").toString());
            Directory directory = getById(id);
            if (directory != null && directory.getUserId().equals(userId)) {
                directory.setSortOrder(sortOrder);
                updateById(directory);
            }
        }
    }

    private List<Directory> buildTree(List<Directory> directories, Long parentId) {
        return directories.stream()
                .filter(d -> (parentId == null && d.getParentId() == null) ||
                     (parentId != null && parentId.equals(d.getParentId())))
                .map(d -> {
                    d.setChildren(buildTree(directories, d.getId()));
                    return d;
                })
                .collect(Collectors.toList());
    }

    private void collectDescendantIds(Long parentId, String userId, List<Long> result) {
        List<Directory> children = list(new LambdaQueryWrapper<Directory>()
                .eq(Directory::getParentId, parentId)
                .eq(Directory::getUserId, userId));
        for (Directory child : children) {
            result.add(child.getId());
            collectDescendantIds(child.getId(), userId, result);
        }
    }

    private void deleteChildren(Long parentId, String userId) {
        List<Directory> children = list(new LambdaQueryWrapper<Directory>()
                .eq(Directory::getParentId, parentId)
                .eq(Directory::getUserId, userId));
        for (Directory child : children) {
            deleteChildren(child.getId(), userId);
            removeById(child.getId());
        }
    }
}
