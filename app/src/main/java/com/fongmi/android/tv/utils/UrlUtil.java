package com.fongmi.android.tv.utils;

import android.net.Uri;

import com.fongmi.android.tv.server.Server;
import com.github.catvod.utils.UriUtil;
import com.google.common.net.HttpHeaders;

import java.io.File;

public class UrlUtil {

    public static final String CLAN_ROOT = "/tvbox/";

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
        return host == null ? "" : host.toLowerCase().trim();
    }

    public static String path(String url) {
        return url == null ? "" : path(Uri.parse(url));
    }

    public static String path(Uri uri) {
        String path = uri.getLastPathSegment();
        return path == null ? "" : path.trim();
    }

    public static String resolve(String baseUri, String referenceUri) {
        return UriUtil.resolve(baseUri, referenceUri);
    }

    public static String convert(String url) {
        String scheme = scheme(url);
        String path = null;
        if ("assets".equals(scheme)) path = "/";
        else if ("file".equals(scheme)) path = "/file/";
        else if ("proxy".equals(scheme)) path = "/proxy?";
        else if ("clan".equals(scheme)) return clanToPath(url);
        return path != null ? url.replace(scheme + "://", Server.get().getAddress(path)) : url;
    }

    public static String clanToPath(String url) {
        if (url == null) return url;
        if (url.startsWith("clan://")) return CLAN_ROOT + url.substring(7);
        return url;
    }

    public static File toLocalFile(String url) {
        if (url == null) return null;
        if (url.startsWith("clan://")) return new File(CLAN_ROOT + url.substring(7));
        if (url.startsWith("file://")) return new File(url.replace("file://", ""));
        if (url.startsWith("file:/")) return new File(url.replace("file:/", ""));
        if (url.startsWith("/")) return new File(url);
        return null;
    }

    public static String getName(String url) {
        Uri uri = Uri.parse(url);
        String path = path(uri);
        String host = host(uri);
        return !path.isEmpty() ? path : !host.isEmpty() ? host : url;
    }

    public static String fixHeader(String key) {
        if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) return HttpHeaders.USER_AGENT;
        if (HttpHeaders.REFERER.equalsIgnoreCase(key)) return HttpHeaders.REFERER;
        if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) return HttpHeaders.COOKIE;
        return key;
    }
}
