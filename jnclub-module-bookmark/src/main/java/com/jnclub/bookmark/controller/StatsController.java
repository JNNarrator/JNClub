package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.service.StatsService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据看板控制器 — 概览页聚合统计
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 概览摘要：counts / disk / recent / vault */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(statsService.summary());
    }
}
