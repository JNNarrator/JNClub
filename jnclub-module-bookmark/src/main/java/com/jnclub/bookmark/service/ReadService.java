package com.jnclub.bookmark.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

/**
 * 收藏阅读模式服务 — 抓取网页 → jsoup 提取正文 → Safelist 清洗 → 返回可安全渲染的 HTML。
 * 任何失败返回 { success:false, reason }，前端回退为在新标签页打开原文。
 */
@Slf4j
@Service
public class ReadService {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    /** 抓取并提取文章正文；任何失败返回 { success:false, reason } */
    public JSONObject readArticle(String url) {
        JSONObject result = JSONUtil.createObj();
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            result.set("success", false);
            result.set("reason", "仅支持 http/https 链接");
            return result;
        }
        HttpResponse res = null;
        try {
            res = HttpRequest.get(url)
                    .timeout(12000)
                    .header("User-Agent", UA)
                    .execute();
            if (res.getStatus() != 200) {
                result.set("success", false);
                result.set("reason", "页面返回状态码 " + res.getStatus());
                return result;
            }
            String contentType = res.header("Content-Type");
            if (contentType != null
                    && !contentType.toLowerCase().contains("text/html")
                    && !contentType.toLowerCase().contains("application/xhtml")) {
                result.set("success", false);
                result.set("reason", "非 HTML 页面，无法阅读");
                return result;
            }

            // 字节流交给 jsoup 自动识别编码（中文站 GBK/UTF-8 都可靠）
            Document doc = Jsoup.parse(res.bodyStream(), null, url);
            String title = doc.title();
            if (title == null || title.isBlank()) {
                Element og = doc.selectFirst("meta[property=og:title]");
                title = og == null ? "" : og.attr("content");
            }

            Element main = extractMain(doc);
            if (main == null) {
                result.set("success", false);
                result.set("reason", "未能提取到正文内容");
                return result;
            }

            Safelist safelist = Safelist.relaxed()
                    .addAttributes("img", "src", "alt", "width", "height")
                    .addAttributes("a", "href", "title", "target", "rel")
                    .addAttributes("code", "class")
                    .addAttributes("pre", "class");
            String clean = Jsoup.clean(main.html(), url, safelist,
                    new Document.OutputSettings().prettyPrint(false));

            result.set("success", true);
            result.set("url", url);
            result.set("title", title.isBlank() ? url : title);
            result.set("content", clean);
            return result;
        } catch (Exception e) {
            log.warn("阅读模式抓取失败 url={}: {}", url, e.getMessage());
            result.set("success", false);
            result.set("reason", "抓取失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return result;
        } finally {
            if (res != null) res.close();
        }
    }

    /** 提取正文：优先 article/main，其次按正文密度评分 */
    private Element extractMain(Document doc) {
        // 移除噪声节点
        doc.select("script,style,noscript,iframe,nav,header,footer,aside,form,button,svg,canvas,ins,"
                + ".ad,.ads,.advertisement,[class*=advert],.sidebar,.menu,.comment,.comments,"
                + ".footer,.header,.banner,.recommend,.related").remove();

        Element article = doc.selectFirst("article");
        if (article != null && textLen(article) > 200) return article;

        Element main = doc.selectFirst(
                "[role=main],main,#content,.content,.article-content,.article,.post,"
                        + ".entry-content,.md-content,.markdown-body,.rich_media_content,.article-body");
        if (main != null && textLen(main) > 200) return main;

        // 兜底：<p> 密度最高的块级元素
        Element best = null;
        int bestScore = 0;
        for (Element el : doc.select("div,section,td")) {
            int score = scoreElement(el);
            if (score > bestScore) {
                bestScore = score;
                best = el;
            }
        }
        return bestScore >= 200 ? best : null;
    }

    private int scoreElement(Element el) {
        int pCount = el.select("p").size();
        int links = el.select("a[href]").size();
        return pCount * 50 + textLen(el) - links * 10;
    }

    private int textLen(Element el) {
        return el.text().trim().length();
    }
}
