package com.fongmi.android.tv.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class UpdateApkSource {

    public static final String GH_PROXY = "gh_proxy";
    public static final String GHFAST = "ghfast";
    public static final String DIRECT = "direct";
    public static final String CUSTOM = "custom";

    public static final String GH_PROXY_PREFIX = "https://gh-proxy.com/";
    public static final String GHFAST_PREFIX = "https://ghfast.top/";

    private UpdateApkSource() {
    }

    public static String normalizeSource(String source) {
        if (GHFAST.equals(source) || DIRECT.equals(source) || CUSTOM.equals(source)) return source;
        return GH_PROXY;
    }

    public static String normalizeCustomPrefix(String value) {
        if (value == null) return "";
        String prefix = value.trim();
        if (!isValidCustomPrefix(prefix)) return "";
        return prefix.replaceAll("/+$", "") + "/";
    }

    public static boolean isValidCustomPrefix(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isGithubUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value.trim());
            return "github.com".equalsIgnoreCase(uri.getHost())
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (Exception e) {
            return false;
        }
    }

    public static String applyPrefix(String prefix, String githubUrl) {
        String normalized = normalizeCustomPrefix(prefix);
        if (normalized.isEmpty() || !isGithubUrl(githubUrl)) return githubUrl;
        return normalized + githubUrl.trim();
    }

    public static List<String> buildCandidates(String primaryUrl, String fallbackUrl, String source, String customPrefix) {
        Set<String> github = new LinkedHashSet<>();
        Set<String> other = new LinkedHashSet<>();
        collect(primaryUrl, github, other);
        collect(fallbackUrl, github, other);

        Set<String> candidates = new LinkedHashSet<>();
        for (String url : github) addGithubCandidates(candidates, url, normalizeSource(source), customPrefix);
        candidates.addAll(other);
        return new ArrayList<>(candidates);
    }

    private static void collect(String value, Set<String> github, Set<String> other) {
        if (value == null || value.isBlank()) return;
        String url = value.trim();
        (isGithubUrl(url) ? github : other).add(url);
    }

    private static void addGithubCandidates(Set<String> candidates, String url, String source, String customPrefix) {
        switch (source) {
            case DIRECT -> candidates.add(url);
            case GHFAST -> {
                candidates.add(applyPrefix(GHFAST_PREFIX, url));
                candidates.add(applyPrefix(GH_PROXY_PREFIX, url));
                candidates.add(url);
            }
            case CUSTOM -> {
                String custom = applyPrefix(customPrefix, url);
                if (!url.equals(custom)) candidates.add(custom);
                candidates.add(applyPrefix(GH_PROXY_PREFIX, url));
                candidates.add(applyPrefix(GHFAST_PREFIX, url));
                candidates.add(url);
            }
            default -> {
                candidates.add(applyPrefix(GH_PROXY_PREFIX, url));
                candidates.add(applyPrefix(GHFAST_PREFIX, url));
                candidates.add(url);
            }
        }
    }
}
