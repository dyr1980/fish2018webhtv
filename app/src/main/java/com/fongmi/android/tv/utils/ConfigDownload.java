package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.Response;

public class ConfigDownload {

    public interface Callback {
        void success(String dirName);
        void error(String msg);
    }

    public static boolean shouldShow(String url) {
        boolean empty = TextUtils.isEmpty(url);
        boolean startsHttp = !empty && url.toLowerCase(Locale.ROOT).startsWith("http");
        boolean local = startsHttp && (url.startsWith("http://127.") || url.startsWith("https://127."));
        return !empty && startsHttp && !local;
    }

    private static String cleanJar(String jar) {
        if (TextUtils.isEmpty(jar)) return jar;
        int idx = jar.indexOf(";md5;");
        return idx >= 0 ? jar.substring(0, idx) : jar;
    }

    private static String jarFingerprint(String rawJar) {
        if (TextUtils.isEmpty(rawJar)) return "";
        int idx = rawJar.indexOf(";md5;");
        if (idx >= 0) {
            String md5 = rawJar.substring(idx + 5).trim();
            if (md5.startsWith("http")) {
                try { md5 = OkHttp.string(md5).trim(); } catch (Exception ignored) {}
            }
            if (!TextUtils.isEmpty(md5)) return md5.toLowerCase(Locale.ROOT);
        }
        return Util.md5(cleanJar(rawJar));
    }

    private static String computeGlobalSpider() {
        Map<String, Integer> counts = new HashMap<>();
        for (Site site : VodConfig.get().getSites()) {
            String jar = cleanJar(site.getJar());
            if (!TextUtils.isEmpty(jar)) counts.merge(jar, 1, Integer::sum);
        }
        if (counts.isEmpty()) return null;
        String best = null;
        int max = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    public static void start(String url, Callback callback) {
        new Thread(() -> {
            try {
                String dirName = extractDirName(url);
                String outDir = "tvbox/api/" + dirName;
                String clanPrefix = "clan://api/" + dirName;
                File outRoot = new File(Path.root(), outDir);
                outRoot.mkdirs();

                JsonObject root = new JsonObject();
                Set<String> savedNames = new HashSet<>();
                Map<String, String> jarCache = new HashMap<>();

                String globalSpider = computeGlobalSpider();
                if (!TextUtils.isEmpty(globalSpider)) {
                    String jarRel = ensureJar(globalSpider, outDir, clanPrefix, jarCache);
                    if (jarRel != null) root.addProperty("spider", jarRel);
                }

                JsonArray sitesArr = new JsonArray();
                for (Site site : VodConfig.get().getSites()) {
                    JsonObject so = new JsonObject();
                    so.addProperty("key", safeStr(site.getKey()));
                    so.addProperty("name", safeStr(site.getName()));
                    so.addProperty("type", site.getType());

                    String api = safeStr(site.getApi());
                    boolean isJsPy = isJsOrPyFile(api);
                    if (isJsPy) {
                        String ext = api.endsWith(".js") ? "js" : "py";
                        String fname = uniqueName(stripSlash(getBasename(api)), savedNames);
                        downloadText(api, outDir + "/" + ext + "/" + fname);
                        api = clanPrefix + "/" + ext + "/" + fname;
                    }
                    so.addProperty("api", api);

                    String jar = cleanJar(site.getJar());
                    if (!isJsPy && !TextUtils.isEmpty(jar) && !jar.equals(globalSpider)) {
                        String jarRel = ensureJar(site.getJar(), outDir, clanPrefix, jarCache);
                        if (jarRel != null) so.addProperty("jar", jarRel);
                    }

                    String extRaw = site.getExt();
                    if (!TextUtils.isEmpty(extRaw)) {
                        String trimmed = extRaw.trim();
                        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                            try {
                                JsonElement el = App.gson().fromJson(trimmed, JsonElement.class);
                                so.add("ext", el);
                            } catch (Exception ignored) {
                                so.addProperty("ext", extRaw);
                            }
                        } else if (isJsonPath(trimmed)) {
                            String fname = uniqueName(stripSlash(getBasename(trimmed)), savedNames);
                            downloadText(trimmed, outDir + "/json/" + fname);
                            so.addProperty("ext", clanPrefix + "/json/" + fname);
                        } else {
                            so.addProperty("ext", extRaw);
                        }
                    }
                    sitesArr.add(so);
                }
                root.add("sites", sitesArr);

                JsonArray livesArr = new JsonArray();
                for (Live live : LiveConfig.get().getLives()) {
                    String liveUrl = safeStr(live.getUrl());
                    if (TextUtils.isEmpty(liveUrl)) continue;
                    if (liveUrl.startsWith("clan://lives/")) {
                        String fname = uniqueName(stripSlash(getBasename(liveUrl)), savedNames);
                        downloadText(liveUrl, outDir + "/lives/" + fname);
                        liveUrl = clanPrefix + "/lives/" + fname;
                    }
                    JsonObject lo = new JsonObject();
                    lo.addProperty("name", safeStr(live.getName()));
                    lo.addProperty("url", liveUrl);
                    livesArr.add(lo);
                }
                root.add("lives", livesArr);

                String pretty = prettyPrint(root);
                File outFile = new File(outRoot, dirName + ".json");
                Path.write(outFile, pretty.getBytes("UTF-8"));

                if (callback != null) App.post(() -> callback.success(dirName));
            } catch (Throwable e) {
                if (callback != null) App.post(() -> callback.error(e.getMessage() == null ? "unknown" : e.getMessage()));
            }
        }).start();
    }

    private static String ensureJar(String rawJar, String outDir, String clanPrefix, Map<String, String> jarCache) {
        String fp = jarFingerprint(rawJar);
        if (TextUtils.isEmpty(fp)) return null;
        String cached = jarCache.get(fp);
        if (cached != null) return cached;
        String fname = stripSlash(getBasename(cleanJar(rawJar)));
        if (TextUtils.isEmpty(fname)) fname = fp + ".jar";
        String relDir = outDir + "/jars/";
        try {
            downloadJar(cleanJar(rawJar), relDir + fname);
        } catch (Exception ignored) {
        }
        String rel = clanPrefix + "/jars/" + fname;
        jarCache.put(fp, rel);
        return rel;
    }

    private static String extractDirName(String url) {
        String host = url;
        try {
            int q = host.indexOf('?');
            if (q >= 0) host = host.substring(0, q);
            int s = host.indexOf("://");
            if (s >= 0) host = host.substring(s + 3);
            int slash = host.indexOf('/');
            if (slash > 0) host = host.substring(0, slash);
            int port = host.indexOf(':');
            if (port > 0) host = host.substring(0, port);
        } catch (Exception ignored) {}
        host = host.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (host.isEmpty()) host = "config";
        if (host.length() > 20) host = host.substring(0, 20);
        return host;
    }

    private static void downloadJar(String src, String relOut) throws Exception {
        File out = new File(Path.root(), relOut);
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        if (out.exists() && out.length() > 0) return;
        byte[] data = fetchBytes(src);
        if (data != null && data.length > 0) {
            Path.write(out, data);
        } else {
            File local = Path.local(src);
            if (local != null && local.exists()) {
                Path.write(out, Path.readToByte(local));
            }
        }
    }

    private static void downloadText(String src, String relOut) {
        try {
            File out = new File(Path.root(), relOut);
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            String content = null;
            if (src.startsWith("http")) {
                content = OkHttp.string(src);
            } else {
                File local = Path.local(src);
                if (local != null && local.exists()) {
                    content = Path.read(local);
                }
            }
            if (!TextUtils.isEmpty(content)) {
                Path.write(out, content.getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
        }
    }

    private static byte[] fetchBytes(String url) throws IOException {
        if (url.startsWith("http")) {
            Response res = null;
            try {
                res = OkHttp.newCall(url).execute();
                if (res.body() != null) {
                    return res.body().bytes();
                }
            } finally {
                if (res != null) res.close();
            }
        }
        return null;
    }

    private static boolean isJsOrPyFile(String api) {
        return api != null && (api.endsWith(".js") || api.endsWith(".py"));
    }

    private static boolean isJsonPath(String ext) {
        if (ext == null) return false;
        String t = ext.trim();
        if (t.endsWith(".json")) return true;
        if (t.startsWith("http") && t.contains(".json")) return true;
        return false;
    }

    private static String getBasename(String path) {
        if (path == null) return "resource";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String stripSlash(String s) {
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    private static String uniqueName(String name, Set<String> used) {
        if (!used.contains(name)) {
            used.add(name);
            return name;
        }
        String base = name;
        int dot = name.lastIndexOf('.');
        String ext = "";
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 2; ; i++) {
            String candidate = base + "_" + i + ext;
            if (!used.contains(candidate)) {
                used.add(candidate);
                return candidate;
            }
        }
    }

    private static String safeStr(String s) {
        return s == null ? "" : s;
    }

    private static String prettyPrint(JsonElement el) {
        StringBuilder sb = new StringBuilder();
        appendPretty(sb, el, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void appendPretty(StringBuilder sb, JsonElement el, int indent) {
        if (el == null || el.isJsonNull()) {
            sb.append("null");
        } else if (el.isJsonPrimitive()) {
            sb.append(el.getAsJsonPrimitive().toString());
        } else if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() == 0) {
                sb.append("[]");
            } else {
                sb.append("[\n");
                for (int i = 0; i < arr.size(); i++) {
                    indent(sb, indent + 1);
                    appendPretty(sb, arr.get(i), indent + 1);
                    if (i < arr.size() - 1) sb.append(',');
                    sb.append('\n');
                }
                indent(sb, indent);
                sb.append(']');
            }
        } else if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.entrySet().isEmpty()) {
                sb.append("{}");
            } else {
                sb.append("{\n");
                int i = 0;
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    indent(sb, indent + 1);
                    sb.append('"').append(escape(e.getKey())).append("\": ");
                    appendPretty(sb, e.getValue(), indent + 1);
                    if (i < obj.entrySet().size() - 1) sb.append(',');
                    sb.append('\n');
                    i++;
                }
                indent(sb, indent);
                sb.append('}');
            }
        }
    }

    private static void indent(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) sb.append("    ");
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
