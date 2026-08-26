package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.service.CalendarService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * 日历控制器 — 月/范围聚合（待办 + 便签）
 */
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    /** 某月聚合数据：{ todos[], overdueTodos[], notes[], days[] }；year/month 缺省为当前月 */
    @GetMapping("/month")
    public R<Map<String, Object>> month(@RequestParam(required = false) Integer year,
                                        @RequestParam(required = false) Integer month) {
        YearMonth now = YearMonth.now();
        int y = year == null ? now.getYear() : year;
        int m = month == null ? now.getMonthValue() : month;
        if (m < 1 || m > 12) {
            throw new com.jnclub.common.exception.BizException("月份必须在 1-12");
        }
        return R.ok(calendarService.month(y, m));
    }

    /** 任意范围聚合：start/end 格式 YYYY-MM-DD；返回 { todos[], overdueTodos[], notes[], days[] } */
    @GetMapping("/range")
    public R<Map<String, Object>> range(@RequestParam String start,
                                        @RequestParam String end) {
        return R.ok(calendarService.range(LocalDate.parse(start), LocalDate.parse(end)));
    }
}
