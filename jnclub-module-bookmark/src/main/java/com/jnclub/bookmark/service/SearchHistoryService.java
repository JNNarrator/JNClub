package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.SearchHistory;
import com.jnclub.bookmark.mapper.SearchHistoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 搜索历史服务 — 每用户最多保留 50 条，按最近使用排序
 */
@Service
public class SearchHistoryService extends ServiceImpl<SearchHistoryMapper, SearchHistory> {

    private static final int MAX_HISTORY = 50;

    public List<SearchHistory> recent(int limit) {
        String userId = StpUtil.getLoginIdAsString();
        return list(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getCreateTime)
                .orderByDesc(SearchHistory::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit <= 0 ? 20 : limit, 50))));
    }

    public void record(String keyword) {
        if (keyword == null || keyword.isBlank()) return;
        String userId = StpUtil.getLoginIdAsString();
        String kw = keyword.trim();
        // 相同关键词先删除旧记录，再插入新记录（去重 + 更新时间前移）
        remove(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getKeyword, kw));
        SearchHistory h = new SearchHistory();
        h.setUserId(userId);
        h.setKeyword(kw);
        save(h);
        // 超出上限删除最旧记录
        List<SearchHistory> all = list(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getCreateTime)
                .orderByDesc(SearchHistory::getId));
        if (all.size() > MAX_HISTORY) {
            for (SearchHistory old : all.subList(MAX_HISTORY, all.size())) {
                removeById(old.getId());
            }
        }
    }

    public void clear() {
        String userId = StpUtil.getLoginIdAsString();
        remove(new LambdaQueryWrapper<SearchHistory>().eq(SearchHistory::getUserId, userId));
    }
}
