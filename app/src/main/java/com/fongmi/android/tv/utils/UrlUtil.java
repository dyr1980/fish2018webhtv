package com.fongmi.android.tv.utils;

import android.net.Uri;

import com.fongmi.android.tv.server.Server;
import com.github.catvod.utils.UriUtil;
import com.google.common.net.HttpHeaders;

import java.io.File;

public class UrlUtil {

    public static Uri uri(String url) {
        return Uri.parse(url.trim().replace("\\", ""));
    }

    public static String scheme(String url) {
        return url == null ? "" : scheme(Uri.parse(url));
    }

    public static String scheme(Uri uri) {
        String scheme = uri.getScheme();
        return scheme == null ? "" : scheme.toLowerCase().trim();
    }

    public static String host(String url) {
        return url == null ? "" : host(Uri.parse(url));
    }

    public static String host(Uri uri) {
        String host = uri.getHost();
        return host == null ? "" : host;
    }

    public static String path(Uri uri) {
        String path = uri.getPath();
        return path == null ? "" : path;
    }

    public static String path(String url) {
        return path(uri(url));
    }

    public static String query(String url, String key) {
        return uri(url).getQueryParameter(key);
    }

    public static String convert(String url) {
        String scheme = scheme(url);
        if ("clan".equals(scheme)) {
            return Server.get().getAddress("/file/") + url.replace("clan://", "");
        } else if ("proxy".equals(scheme)) {
            return Server.get().getAddress("/proxy?") + url.replace("proxy://", "");
        }
        return url;
    }

    public static File toLocalFile(String url) {
        if (url == null) return null;
        if (url.startsWith("clan://")) return com.github.catvod.utils.Path.local(url);
        if (url.startsWith("file://")) {
            String path = url.substring(7);
            if (!path.startsWith("/")) path = "/" + path;
            return new File(path);
        }
        if (url.startsWith("file:/")) return new File(url.substring(5));
        if (url.startsWith("/")) return new File(url);
        return null;
    }

    public static String getName(String url) {
        Uri uri = Uri.parse(url);
        String path = path(uri);
        int slash = path.lastIndexOf("/");
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    public static String resolve(String baseUrl, String relativePath) {
        try {
            Uri base = Uri.parse(baseUrl);
            Uri resolved = UriUtil.resolve(base, relativePath);
            return resolved.toString();
        } catch (Throwable ignored) {
            return relativePath;
        }
    }

    public static String fix(String url) {
        if (url == null) return "";
        if (url.startsWith("//")) url = "https:" + url;
        if (url.startsWith(".")) url = url.replaceFirst("^\\.+/", "");
        if (url.startsWith("/")) return url;
        if (url.startsWith("http")) return url;
        return url;
    }

    public static String addQuery(String url, String key, String value) {
        if (url == null || url.isEmpty()) return url;
        if (value == null || value.isEmpty()) return url;
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + key + "=" + value;
    }

    public static String removeQuery(String url, String key) {
        if (url == null || url.isEmpty()) return url;
        int queryIndex = url.indexOf('?');
        if (queryIndex < 0) return url;
        String scheme = url.substring(0, queryIndex + 1);
        String query = url.substring(queryIndex + 1);
        String[] pairs = query.split("&");
        StringBuilder sb = new StringBuilder(scheme);
        boolean first = true;
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            if (k.equals(key)) continue;
            if (!first) sb.append('&');
            sb.append(pair);
            first = false;
        }
        return sb.toString();
    }

    public static String header(String url) {
        String host = host(url);
        if (host.isEmpty()) return "";
        String referer = url;
        if (url.contains("/")) {
            int slash = url.indexOf('/');
            if (slash >= 0) referer = url.substring(0, slash + 1);
        }
        return HttpHeaders.REFERER + ": " + referer + "\r\n" + HttpHeaders.HOST + ": " + host;
    }
}