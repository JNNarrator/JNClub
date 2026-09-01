package com.jnclub.bookmark.service;

import com.jnclub.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * WebDAV 客户端 — 基于 JDK 内置 HttpClient（支持 PROPFIND/MKCOL/MOVE 等自定义方法）
 * <p>
 * 用于个人 WebDAV 站点的简单文件管理：
 * 列目录(PROPFIND) / 新建文件夹(MKCOL) / 上传(PUT) / 下载(GET) / 删除(DELETE) / 重命名(MOVE)
 */
@Slf4j
public class WebDavClient {

    private static final String DAV_NS = "DAV:";

    private final HttpClient http;
    private final String baseUrl;
    private final String authHeader;

    public WebDavClient(String baseUrl, String username, String password) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        // 规范化 baseUrl：去掉末尾斜杠
        String u = baseUrl == null ? "" : baseUrl.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        this.baseUrl = u;
        String raw = (username == null ? "" : username) + ":" + (password == null ? "" : password);
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** WebDAV 目录项 */
    public record Entry(String name, String path, boolean isDir, long size, String modified) {
    }

    // ============================================================
    // 公共操作
    // ============================================================

    /**
     * 列目录（PROPFIND Depth:1）
     *
     * @param path 目录路径，"" 或 "/" 表示根目录
     */
    public List<Entry> list(String path) {
        String url = resolveUrl(path, true);
        String body = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:">
                  <d:prop>
                    <d:resourcetype/>
                    <d:getcontentlength/>
                    <d:getlastmodified/>
                    <d:displayname/>
                    <d:getcontenttype/>
                  </d:prop>
                </d:propfind>
                """;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("PROPFIND", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .header("Authorization", authHeader)
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                throw new BizException("路径不存在或无法访问");
            }
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                throw new BizException("认证失败，请检查用户名/密码");
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new BizException("WebDAV 列目录失败: HTTP " + resp.statusCode());
            }
            return parsePropfind(resp.body(), path);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WebDAV 列目录失败 {} : {}", url, e.getMessage());
            throw new BizException("WebDAV 连接失败: " + e.getMessage());
        }
    }

    /** 新建文件夹（MKCOL） */
    public void mkdir(String path) {
        String url = resolveUrl(path, true);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("MKCOL", HttpRequest.BodyPublishers.noBody())
                .header("Authorization", authHeader)
                .timeout(Duration.ofSeconds(30))
                .build();
        sendExpect2xx(req, url, "新建文件夹");
    }

    /** 上传文件（PUT） */
    public void upload(String path, InputStream in) {
        String url = resolveUrl(path, false);
        byte[] bytes;
        try {
            bytes = readAll(in);
        } catch (Exception e) {
            throw new BizException("读取上传文件失败: " + e.getMessage());
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .header("Authorization", authHeader)
                .timeout(Duration.ofSeconds(120))
                .build();
        sendExpect2xx(req, url, "上传文件");
    }

    /** 下载文件（GET），返回字节流（由调用方写回响应） */
    public InputStream download(String path) {
        String url = resolveUrl(path, false);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Authorization", authHeader)
                .timeout(Duration.ofSeconds(120))
                .build();
        try {
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 404) {
                throw new BizException("文件不存在或无法访问");
            }
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                throw new BizException("认证失败，请检查用户名/密码");
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new BizException("WebDAV 下载失败: HTTP " + resp.statusCode());
            }
            return new ByteArrayInputStream(resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WebDAV 下载失败 {} : {}", url, e.getMessage());
            throw new BizException("WebDAV 连接失败: " + e.getMessage());
        }
    }

    /**
     * 删除（DELETE）。目录默认带 Depth: infinity 递归删除。
     */
    public void delete(String path, boolean isDir) {
        String url = resolveUrl(path, isDir);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE()
                .header("Authorization", authHeader)
                .timeout(Duration.ofSeconds(60));
        if (isDir) {
            builder.header("Depth", "infinity");
        }
        sendExpect2xx(builder.build(), url, "删除");
    }

    /** 重命名（MOVE），newName 为新的文件名（同一目录内） */
    public void rename(String path, String newName) {
        String oldUrl = resolveUrl(path, false);
        // 目标地址：父目录 + 新名
        String parent = "";
        int idx = path.lastIndexOf('/');
        if (idx >= 0) parent = path.substring(0, idx);
        String newPath = parent.isEmpty() ? newName : parent + "/" + newName;
        String destUrl = resolveUrl(newPath, false);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(oldUrl))
                .method("MOVE", HttpRequest.BodyPublishers.noBody())
                .header("Authorization", authHeader)
                .header("Destination", destUrl)
                .timeout(Duration.ofSeconds(60))
                .build();
        sendExpect2xx(req, oldUrl, "重命名");
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private void sendExpect2xx(HttpRequest req, String url, String action) {
        try {
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                throw new BizException("认证失败，请检查用户名/密码");
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new BizException(action + "失败: HTTP " + resp.statusCode());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WebDAV {} 失败 {} : {}", action, url, e.getMessage());
            throw new BizException("WebDAV " + action + "失败: " + e.getMessage());
        }
    }

    /**
     * 拼接完整 URL。
     *
     * @param path      相对路径（相对 baseUrl），"" 或 "/" 为根
     * @param isDir     是否为目录（目录拼接末尾 /，部分服务器必需）
     */
    private String resolveUrl(String path, boolean isDir) {
        String p = path == null ? "" : path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        StringBuilder sb = new StringBuilder(baseUrl);
        if (!p.isEmpty()) {
            sb.append('/').append(encodePath(p));
        }
        if (isDir && !p.isEmpty()) {
            sb.append('/');
        }
        return sb.toString();
    }

    /** 路径逐段 URL 编码（保留 / 分隔符） */
    private static String encodePath(String path) {
        String[] segs = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segs.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(URLEncoder.encode(segs[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** 解析 PROPFIND multistatus 响应 */
    private List<Entry> parsePropfind(String xml, String currentPath) throws Exception {
        List<Entry> result = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // 防 XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        NodeList responses = doc.getElementsByTagNameNS(DAV_NS, "response");
        for (int i = 0; i < responses.getLength(); i++) {
            Element resp = (Element) responses.item(i);
            String href = text(resp, "href");
            if (href == null || href.isBlank()) continue;

            // 该 response 的 href 转成相对路径
            String rel = hrefToRelPath(href);
            if (rel.isEmpty()) continue; // 根目录自身跳过

            String name = displayName(resp, rel);
            boolean isDir = containsCollection(resp);
            long size = 0;
            String sizeStr = text(resp, "getcontentlength");
            if (sizeStr != null && !sizeStr.isBlank()) {
                try {
                    size = Long.parseLong(sizeStr.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            String modified = text(resp, "getlastmodified");
            // 拼相对路径（相对 baseUrl）
            String entryPath = currentPathClean(currentPath);
            entryPath = entryPath.isEmpty() ? name : entryPath + "/" + name;
            result.add(new Entry(name, entryPath, isDir, size, modified == null ? "" : modified));
        }
        return result;
    }

    private String currentPathClean(String currentPath) {
        String p = currentPath == null ? "" : currentPath.trim();
        while (p.startsWith("/")) p = p.substring(1);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    /** href（可能是完整 URL 或路径）→ 解码后的相对路径 */
    private String hrefToRelPath(String href) {
        String h = href;
        // 去 query/fragment
        int q = h.indexOf('?');
        if (q >= 0) h = h.substring(0, q);
        // 取路径部分（兼容带 host 的完整 URL）
        if (h.startsWith("http://") || h.startsWith("https://")) {
            int scheme = h.indexOf("://") + 3;
            int slash = h.indexOf('/', scheme);
            h = slash >= 0 ? h.substring(slash) : "/";
        }
        // 去掉末尾斜杠
        while (h.endsWith("/")) h = h.substring(0, h.length() - 1);
        // 百分比解码
        h = percentDecode(h);
        // 去掉 baseUrl 前缀（若为完整路径）
        String basePath = pathOf(baseUrl);
        if (!basePath.isEmpty() && h.startsWith(basePath)) {
            h = h.substring(basePath.length());
        }
        while (h.startsWith("/")) h = h.substring(1);
        return h;
    }

    private static String pathOf(String url) {
        try {
            return new URI(url).getPath() == null ? "" : new URI(url).getPath();
        } catch (Exception e) {
            return "";
        }
    }

    private static String percentDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private String displayName(Element resp, String relPath) {
        String dn = text(resp, "displayname");
        if (dn != null && !dn.isBlank()) {
            return stripSlash(dn);
        }
        // 退化：取路径最后一段
        int idx = relPath.lastIndexOf('/');
        String last = idx >= 0 ? relPath.substring(idx + 1) : relPath;
        return last.isEmpty() ? relPath : stripSlash(last);
    }

    private static String stripSlash(String s) {
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private boolean containsCollection(Element resp) {
        NodeList types = resp.getElementsByTagNameNS(DAV_NS, "collection");
        return types.getLength() > 0;
    }

    private String text(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(DAV_NS, localName);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }
}
