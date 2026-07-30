package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.mapper.DirectoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 目录服务
 */
@Service
public class DirectoryService extends ServiceImpl<DirectoryMapper, Directory> {

    /**
     * 获取当前用户的目录树
     */
    public List<Directory> getDirectoryTree() {
        String userId = StpUtil.getLoginIdAsString();
        List<Directory> directories = list(new LambdaQueryWrapper<Directory>()
                .eq(Directory::getUserId, userId)
                .orderByAsc(Directory::getSortOrder));
        
        return buildTree(directories, null);
    }

    /**
     * 创建目录
     */
    public Directory createDirectory(Directory directory) {
        String userId = StpUtil.getLoginIdAsString();
        directory.setUserId(userId);
        save(directory);
        return directory;
    }

    /**
     * 重命名目录
     */
    public void renameDirectory(Long id, String name) {
        String userId = StpUtil.getLoginIdAsString();
        Directory directory = getById(id);
        if (directory == null || !directory.getUserId().equals(userId)) {
            throw new RuntimeException("目录不存在");
        }
        directory.setName(name);
        updateById(directory);
    }

    /**
     * 删除目录（级联删除子目录）
     */
    @Transactional
    public void deleteDirectory(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Directory directory = getById(id);
        if (directory == null || !directory.getUserId().equals(userId)) {
            throw new RuntimeException("目录不存在");
        }
        
        // 递归删除子目录
        deleteChildren(id, userId);
        
        // 删除自身
        removeById(id);
    }

    /**
     * 批量更新排序
     */
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

    /**
     * 构建目录树
     */
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

    /**
     * 递归删除子目录
     */
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
