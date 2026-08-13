package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.common.cache.CacheKey;
import com.jnclub.common.cache.CacheService;
import com.jnclub.bookmark.entity.UserPreference;
import com.jnclub.bookmark.mapper.UserPreferenceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户偏好服务 — 通用 KV（JSON 值）
 * 供模块记忆、视图记忆等场景复用；新增记忆点只需约定 prefKey 规范（模块.场景）
 */
@Service
public class UserPreferenceService extends ServiceImpl<UserPreferenceMapper, UserPreference> {

    @Autowired
    private CacheService cacheService;

    /**
     * 获取当前用户全部偏好（JSON 反序列化后的值）
     */
    public Map<String, Object> getAllMap() {
        String userId = StpUtil.getLoginIdAsString();
        String cacheKey = CacheKey.pref(userId);
        Map<String, Object> cached = cacheService.getMap(cacheKey);
        if (cached != null) return cached;
        List<UserPreference> list = list(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId));
        Map<String, Object> map = new HashMap<>();
        for (UserPreference p : list) {
            try {
                map.put(p.getPrefKey(), JSONUtil.parse(p.getPrefValue()));
            } catch (Exception e) {
                // 非 JSON 值（异常数据）原样返回
                map.put(p.getPrefKey(), p.getPrefValue());
            }
        }
        cacheService.setMap(cacheKey, map, CacheService.DEFAULT_TTL);
        return map;
    }

    /**
     * 批量 upsert（key 存在则更新，否则插入）
     */
    @Transactional
    public void batchUpsert(List<Map<String, Object>> prefs) {
        String userId = StpUtil.getLoginIdAsString();
        for (Map<String, Object> item : prefs) {
            String key = String.valueOf(item.get("key"));
            Object value = item.get("value");
            // 基本类型直接 toString；对象/数组/Map 走 JSON 序列化（hutool toJsonStr 对 Number 会返回 {}，需特殊处理）
            String json;
            if (value instanceof String) {
                json = (String) value;
            } else if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
                json = String.valueOf(value);
            } else {
                json = JSONUtil.toJsonStr(value);
            }

            UserPreference exist = getOne(new LambdaQueryWrapper<UserPreference>()
                    .eq(UserPreference::getUserId, userId)
                    .eq(UserPreference::getPrefKey, key));
            if (exist != null) {
                exist.setPrefValue(json);
                updateById(exist);
            } else {
                UserPreference p = new UserPreference();
                p.setUserId(userId);
                p.setPrefKey(key);
                p.setPrefValue(json);
                save(p);
            }
        }
        cacheService.evict(CacheKey.pref(userId));
    }
}
