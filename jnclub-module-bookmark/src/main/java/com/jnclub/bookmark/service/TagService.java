package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.common.cache.CacheKey;
import com.jnclub.common.cache.CacheService;
import com.jnclub.bookmark.entity.Tag;
import com.jnclub.bookmark.entity.TagRelation;
import com.jnclub.bookmark.mapper.TagMapper;
import com.jnclub.bookmark.mapper.TagRelationMapper;
import com.jnclub.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标签服务 — 标签 CRUD + 关联管理（通用 refType：bookmark=收藏 note=便签）
 */
@Service
@RequiredArgsConstructor
public class TagService extends ServiceImpl<TagMapper, Tag> {

    private final TagRelationMapper tagRelationMapper;
    private final CacheService cacheService;

    // ============================================================
    // 标签 CRUD
    // ============================================================

    /**
     * 我的全部标签（按 refType 统计关联数）。refType 为空时统计全部。
     */
    public List<Tag> listTags(String refType) {
        String userId = StpUtil.getLoginIdAsString();
        String cacheKey = CacheKey.tag(userId, refType);
        List<Tag> cached = cacheService.getList(cacheKey, Tag.class);
        if (cached != null) return cached;
        List<Tag> tags = list(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, userId)
                .orderByAsc(Tag::getName));

        if (tags.isEmpty()) {
            cacheService.setList(cacheKey, tags, CacheService.DEFAULT_TTL);
            return tags;
        }

        List<Long> tagIds = tags.stream().map(Tag::getId).toList();
        List<TagRelation> relations = tagRelationMapper.selectList(
                new LambdaQueryWrapper<TagRelation>()
                        .in(TagRelation::getTagId, tagIds)
                        .eq(refType != null, TagRelation::getRefType, refType));

        Map<Long, Long> countByTag = relations.stream()
                .collect(Collectors.groupingBy(TagRelation::getTagId, Collectors.counting()));
        tags.forEach(t -> t.setCount(countByTag.getOrDefault(t.getId(), 0L)));
        cacheService.setList(cacheKey, tags, CacheService.DEFAULT_TTL);
        return tags;
    }

    /** 新建标签（同名幂等：已存在直接返回） */
    @Transactional
    public Tag createTag(String name) {
        String userId = StpUtil.getLoginIdAsString();
        if (name == null || name.isBlank()) throw new BizException("标签名不能为空");
        String trimmed = name.trim();
        if (trimmed.length() > 50) throw new BizException("标签名过长（最多 50 字符）");

        Tag existing = getOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, userId)
                .eq(Tag::getName, trimmed), false);
        if (existing != null) return existing;

        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(trimmed);
        save(tag);
        cacheService.evictByPrefix(CacheKey.tagPrefix(userId));
        return tag;
    }

    /** 批量创建并返回 id（前端多选可创建场景） */
    @Transactional
    public List<Long> createTags(List<String> names) {
        if (names == null || names.isEmpty()) return Collections.emptyList();
        List<Long> ids = new ArrayList<>();
        for (String name : names) {
            ids.add(createTag(name).getId());
        }
        return ids;
    }

    @Transactional
    public void renameTag(Long id, String name) {
        String userId = StpUtil.getLoginIdAsString();
        Tag tag = getOwned(id, userId);
        if (name == null || name.isBlank()) throw new BizException("标签名不能为空");
        String trimmed = name.trim();

        Tag duplicate = getOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, userId)
                .eq(Tag::getName, trimmed)
                .ne(Tag::getId, id), false);
        if (duplicate != null) throw new BizException("同名标签已存在");

        tag.setName(trimmed);
        updateById(tag);
        cacheService.evictByPrefix(CacheKey.tagPrefix(userId));
    }

    /** 删除标签：级联清空所有关联 */
    @Transactional
    public void deleteTag(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        getOwned(id, userId);
        removeById(id);
        tagRelationMapper.delete(new LambdaUpdateWrapper<TagRelation>()
                .eq(TagRelation::getTagId, id));
        cacheService.evictByPrefix(CacheKey.tagPrefix(userId));
    }

    // ============================================================
    // 关联管理（全量覆盖式）
    // ============================================================

    /**
     * 查询单条记录关联的标签（带标签名）。
     */
    public List<Tag> listTagsOfRef(String refType, Long refId) {
        String userId = StpUtil.getLoginIdAsString();
        List<TagRelation> relations = tagRelationMapper.selectList(
                new LambdaQueryWrapper<TagRelation>()
                        .eq(TagRelation::getRefType, refType)
                        .eq(TagRelation::getRefId, refId));
        if (relations.isEmpty()) return Collections.emptyList();

        List<Long> tagIds = relations.stream().map(TagRelation::getTagId).toList();
        return list(new LambdaQueryWrapper<Tag>()
                .in(Tag::getId, tagIds)
                .eq(Tag::getUserId, userId)
                .orderByAsc(Tag::getName));
    }

    /**
     * 全量覆盖设置关联：body = {refType, refId, tagNames[]}。
     * 先清空旧关联，再按 tagNames 建立（同名标签复用）。
     */
    @Transactional
    public void setRelations(String refType, Long refId, List<String> tagNames) {
        String userId = StpUtil.getLoginIdAsString();
        if (refId == null) throw new BizException("refId 不能为空");
        if (!"bookmark".equals(refType) && !"note".equals(refType)) {
            throw new BizException("refType 非法");
        }

        tagRelationMapper.delete(new LambdaUpdateWrapper<TagRelation>()
                .eq(TagRelation::getRefType, refType)
                .eq(TagRelation::getRefId, refId));

        if (tagNames == null || tagNames.isEmpty()) return;

        for (String name : tagNames) {
            if (name == null || name.isBlank()) continue;
            Tag tag = createTag(name); // 复用或新建
            TagRelation relation = new TagRelation();
            relation.setTagId(tag.getId());
            relation.setRefType(refType);
            relation.setRefId(refId);
            tagRelationMapper.insert(relation);
        }
        cacheService.evictByPrefix(CacheKey.tagPrefix(userId));
    }

    /** 删除某记录的全部关联（永久删除收藏/便签时调用） */
    @Transactional
    public void deleteRelationsByRef(String refType, Long refId, String userId) {
        tagRelationMapper.delete(new LambdaUpdateWrapper<TagRelation>()
                .eq(TagRelation::getRefType, refType)
                .eq(TagRelation::getRefId, refId));
    }

    /**
     * 按标签查关联记录 id 集合（供按标签筛选列表使用）。
     */
    public List<Long> listRefIdsByTag(String refType, Long tagId, String userId) {
        // 先校验标签归属，防止跨用户枚举
        Tag tag = getById(tagId);
        if (tag == null || !tag.getUserId().equals(userId)) return Collections.emptyList();
        List<TagRelation> relations = tagRelationMapper.selectList(
                new LambdaQueryWrapper<TagRelation>()
                        .eq(TagRelation::getRefType, refType)
                        .eq(TagRelation::getTagId, tagId));
        if (relations.isEmpty()) return Collections.emptyList();
        return relations.stream().map(TagRelation::getRefId).toList();
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private Tag getOwned(Long id, String userId) {
        Tag tag = getById(id);
        if (tag == null || !tag.getUserId().equals(userId)) {
            throw new BizException("标签不存在");
        }
        return tag;
    }
}
