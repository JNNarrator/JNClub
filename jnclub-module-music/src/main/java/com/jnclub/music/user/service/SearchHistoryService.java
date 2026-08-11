package com.jnclub.music.user.service;

import com.jnclub.music.user.dto.SearchKeywordDTO;
import java.util.List;

/**
 * 搜索历史业务接口。
 */
public interface SearchHistoryService {

    List<SearchKeywordDTO> listSearchHistory(String deviceId, Integer limit);

    void recordKeyword(String deviceId, String keyword);

    void clearSearchHistory(String deviceId);
}
