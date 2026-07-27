package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateApkSourceTest {

    private static final String GITHUB = "https://github.com/fish2018/webhtv/releases/download/v1/mobile.apk";
    private static final String CNB = "https://cnb.cool/fish2035/webhtv-release/-/releases/download/v1/mobile.apk";

    @Test
    public void defaultsUnknownSourceToGhProxy() {
        assertEquals(UpdateApkSource.GH_PROXY, UpdateApkSource.normalizeSource(null));
        assertEquals(UpdateApkSource.GH_PROXY, UpdateApkSource.normalizeSource("unknown"));
    }

    @Test
    public void ordersBuiltInSourcesAndDirectFallback() {
        assertEquals(List.of(
                "https://gh-proxy.com/" + GITHUB,
                "https://ghfast.top/" + GITHUB,
                GITHUB
        ), UpdateApkSource.buildCandidates(GITHUB, null, UpdateApkSource.GH_PROXY, ""));

        assertEquals(List.of(
                "https://ghfast.top/" + GITHUB,
                "https://gh-proxy.com/" + GITHUB,
                GITHUB
        ), UpdateApkSource.buildCandidates(GITHUB, null, UpdateApkSource.GHFAST, ""));
    }

    @Test
    public void directModeDoesNotUseThirdPartySources() {
        assertEquals(List.of(GITHUB), UpdateApkSource.buildCandidates(GITHUB, null, UpdateApkSource.DIRECT, ""));
    }

    @Test
    public void customSourcePrecedesBuiltInsAndNormalizesSlash() {
        assertEquals("https://proxy.example.com/path/", UpdateApkSource.normalizeCustomPrefix("  https://proxy.example.com/path///  "));
        assertEquals(List.of(
                "https://proxy.example.com/path/" + GITHUB,
                "https://gh-proxy.com/" + GITHUB,
                "https://ghfast.top/" + GITHUB,
                GITHUB
        ), UpdateApkSource.buildCandidates(GITHUB, null, UpdateApkSource.CUSTOM, "https://proxy.example.com/path///"));
    }

    @Test
    public void rejectsInvalidCustomPrefixes() {
        assertFalse(UpdateApkSource.isValidCustomPrefix("proxy.example.com"));
        assertFalse(UpdateApkSource.isValidCustomPrefix("https://proxy.example.com/?token=x"));
        assertFalse(UpdateApkSource.isValidCustomPrefix("https://proxy.example.com/#part"));
        assertTrue(UpdateApkSource.isValidCustomPrefix("http://192.168.1.2:8080/proxy"));
    }

    @Test
    public void prioritizesGithubCandidatesAndKeepsNonGithubFallback() {
        assertEquals(List.of(
                "https://gh-proxy.com/" + GITHUB,
                "https://ghfast.top/" + GITHUB,
                GITHUB,
                CNB
        ), UpdateApkSource.buildCandidates(CNB, GITHUB, UpdateApkSource.GH_PROXY, ""));
    }

    @Test
    public void leavesNonGithubUrlsUntouchedAndDeduplicatesCandidates() {
        assertFalse(UpdateApkSource.isGithubUrl("https://api.github.com/repos/fish2018/webhtv"));
        assertEquals(List.of(CNB), UpdateApkSource.buildCandidates(CNB, CNB, UpdateApkSource.GH_PROXY, ""));
    }
}
