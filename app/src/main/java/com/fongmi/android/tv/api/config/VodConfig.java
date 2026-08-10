package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.CspWarmup;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VodConfig extends BaseConfig {

    private static final String TAG = VodConfig.class.getSimpleName();

    private Site home;
    private String wall;
    private Parse parse;
    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<String> ads;
    private List<String> flags;
    private List<Parse> parses;

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public VodConfig init() {
        return config(Config.vod());
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        ads = null;
        doh = null;
        home = null;
        wall = null;
        parse = null;
        sites = null;
        flags = null;
        rules = null;
        parses = null;
        WebHomeExtensionRegistry.get().setGlobalSources(null, "");
        BaseLoader.get().clear();
        RuleConfig.get().invalidate();
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.vod();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.vod();
    }

    @Override
    protected void load(Config config) throws Throwable {
        String json = Decoder.getJson(UrlUtil.convert(config.getUrl()), TAG);
        checkJson(config, Json.parse(json).getAsJsonObject());
    }

    @Override
    protected boolean isLoaded() {
        return !getSites().isEmpty();
    }

    @Override
    protected void beforeLoad() {
        CspWarmup.reset();
    }

    @Override
    protected void onLoadSuccess() {
        CspWarmup.schedule("vod-config-loaded");
    }

    private void checkJson(Config config, JsonObject object) throws Throwable {
        if (object.has("msg")) {
            throw new Exception(object.get("msg").getAsString());
        } else if (object.has("urls")) {
            parseDepot(config, object);
        } else {
            parseConfig(config, object);
        }
    }

    private void parseDepot(Config config, JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, VOD));
        if (configs.isEmpty()) throw new Exception("Depot urls is empty");
        load(this.config = configs.get(0));
        Config.delete(config.getUrl());
    }

    private void parseConfig(Config config, JsonObject object) {
        CustomCspSetting.inject(object);
        initList(object);
        initLive(config, object);
        initWall(config, object);
        initSite(config, object);
        initParse(config, object);
        WebHomeExtensionRegistry.get().setGlobalSources(object.get("webHomeExtensions"), config.getUrl());
        config.setLogo(Json.safeString(object, "logo"));
        config.setNotice(Json.safeString(object, "notice"));
        config.setDanmaku(Json.safeString(object, "danmaku"));
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setDoh(Doh.arrayFrom(fetchArray(object, "doh")));
        setFlags(Json.safeListString(object, "flags"));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initLive(Config config, JsonObject object) {
        if (Json.isEmpty(object, "lives")) return;
        Config temp = Config.find(config, LIVE).save();
        boolean sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) LiveConfig.get().config(temp.update()).parse(object);
    }

    private void initWall(Config config, JsonObject object) {
        if (Json.isEmpty(object, "wallpaper")) return;
        this.wall = Json.safeString(object, "wallpaper");
        Config temp = Config.find(wall, config.getName(), WALL).save();
        boolean sync = WallConfig.get().needSync(wall);
        if (sync) WallConfig.get().config(temp.update());
    }

    private void initSite(Config config, JsonObject object) {
        String spider = Json.safeString(object, "spider");
        BaseLoader.get().parseJar(spider, true);
        List<Site> sites = Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new));
        List<Site> fileSites = loadFileSites(spider);
        fileSites.addAll(sites);
        setSites(fileSites);
        Map<String, Site> items = Site.findAll().stream().collect(Collectors.toMap(Site::getKey, Function.identity()));
        getSites().forEach(site -> site.sync(items.get(site.getKey())));
        CustomCspSetting.Result custom = CustomCspSetting.inject(getSites());
        Site home = !custom.home().isEmpty() ? custom.home() : getSites().stream().filter(item -> item.getKey().equals(config.getHome())).findFirst().orElse(getSites().isEmpty() ? new Site() : getSites().get(0));
        setHome(config, home, false);
    }

    private void initParse(Config config, JsonObject object) {
        setParses(Json.safeListElement(object, "parses").stream().map(Parse::objectFrom).distinct().collect(Collectors.toCollection(ArrayList::new)));
        setParse(config, getParses().isEmpty() ? new Parse() : getParses().stream().filter(item -> item.getName().equals(config.getParse())).findFirst().orElse(getParses().get(0)), false);
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    private void setSites(List<Site> sites) {
        this.sites = sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    private void setParses(List<Parse> parses) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        this.parses = parses;
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    private void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
        RuleConfig.get().invalidate();
    }

    public List<Parse> getParses(int type) {
        return getParses().stream().filter(item -> item.getType() == type).toList();
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = getParses(type);
        List<Parse> filter = items.stream().filter(item -> item.getExt().getFlag().contains(flag)).toList();
        return filter.isEmpty() ? items : filter;
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
        RuleConfig.get().invalidate();
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public void setParse(Parse parse) {
        setParse(getConfig(), parse, true);
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public void setHome(Site site) {
        setHome(getConfig(), site, true);
        RefreshEvent.home();
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        return getParses().stream().filter(item -> item.getName().equals(name)).findFirst().orElse(new Parse());
    }

    public Site getSite(String key) {
        return getSites().stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(new Site());
    }

    private void setParse(Config config, Parse parse, boolean save) {
        this.parse = parse;
        this.parse.setSelected(true);
        config.setParse(parse.getName());
        getParses().forEach(item -> item.setSelected(parse));
        if (save) config.save();
    }

    // ==================== 文件站点加载器 ====================

    private static final String CLAN_ROOT = "/tvbox/";
    private static final String XBPQ_JAR = CLAN_ROOT + "jars/XBPQ.jar";

    private List<Site> loadFileSites(String globalSpider) {
        List<Site> result = new ArrayList<>();
        if (!Setting.isFileSites()) return result;
        try {
            result.addAll(loadXbpqSites(globalSpider));
        } catch (Throwable e) {
            SpiderDebug.log("vod-config", "XBPQ file sites load failed: %s", e.getMessage());
        }
        try {
            result.addAll(loadJsSites());
        } catch (Throwable e) {
            SpiderDebug.log("vod-config", "JS file sites load failed: %s", e.getMessage());
        }
        try {
            result.addAll(loadPySites());
        } catch (Throwable e) {
            SpiderDebug.log("vod-config", "PY file sites load failed: %s", e.getMessage());
        }
        try {
            result.addAll(loadRawSites(globalSpider));
        } catch (Throwable e) {
            SpiderDebug.log("vod-config", "Raw file sites load failed: %s", e.getMessage());
        }
        if (!result.isEmpty()) {
            SpiderDebug.log("vod-config", "file sites loaded count=%d", result.size());
        }
        return result;
    }

    /**
     * /tvbox/sites-json/ 目录: 每个文件内容作为 XBPQ 站点的 ext。
     * key = "XBPQ_" + 文件名, api = "csp_XBPQ", jar = XBPQ_JAR
     */
    private List<Site> loadXbpqSites(String globalSpider) {
        File dir = new File(CLAN_ROOT + "sites-json");
        List<Site> result = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return result;
        List<File> files = listSorted(dir);
        if (files.isEmpty()) return result;
        boolean globalIsXbpq = globalSpider.toLowerCase().contains("xbpq") && globalSpider.toLowerCase().endsWith(".jar");
        if (!globalIsXbpq) {
            BaseLoader.get().parseJar(XBPQ_JAR, true);
            SpiderDebug.log("vod-config", "XBPQ jar preloaded from %s", XBPQ_JAR);
        }
        for (File file : files) {
            FileMeta meta = parseFileMeta(file.getName());
            if (meta.name.isEmpty()) continue;
            String content = Path.read(file);
            if (content.isEmpty()) continue;
            Site site = Site.get("XBPQ_" + meta.name, meta.name);
            site.setApi("csp_XBPQ");
            site.setExt(content);
            site.setJar(XBPQ_JAR);
            meta.apply(site);
            result.add(site);
        }
        return result;
    }

    /**
     * /tvbox/sites-js/api/ 目录: 每个文件内容作为 JS Spider 的 api (脚本源码)。
     * key = "JS_" + 文件名, api = 文件内容, ext 不设置 (保持默认)
     */
    private List<Site> loadJsSites() {
        File dir = new File(CLAN_ROOT + "sites-js", "api");
        List<Site> result = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return result;
        for (File file : listSorted(dir)) {
            FileMeta meta = parseFileMeta(file.getName());
            if (meta.name.isEmpty()) continue;
            String content = Path.read(file);
            if (content.isEmpty()) continue;
            Site site = Site.get("JS_" + meta.name, meta.name);
            site.setApi(content);
            meta.apply(site);
            result.add(site);
        }
        return result;
    }

    /**
     * /tvbox/sites-py/ 目录: 每个文件内容作为 Python Spider 的 api (脚本源码)。
     * key = "PY_" + 文件名, api = 文件内容, ext 不设置 (保持默认)
     */
    private List<Site> loadPySites() {
        File dir = new File(CLAN_ROOT + "sites-py");
        List<Site> result = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return result;
        for (File file : listSorted(dir)) {
            FileMeta meta = parseFileMeta(file.getName());
            if (meta.name.isEmpty()) continue;
            String content = Path.read(file);
            if (content.isEmpty()) continue;
            Site site = Site.get("PY_" + meta.name, meta.name);
            site.setApi(content);
            meta.apply(site);
            result.add(site);
        }
        return result;
    }

    /**
     * /tvbox/sites/ 目录: 每个文件是完整的站点 JSON, 包含 key/name/api/ext/jar 等所有字段。
     * 未指定的字段使用默认值 (由 Site.objectFrom 处理)。
     * 文件名解析的标记也会覆盖到已解析的站点上 (searchable/hide/style)。
     */
    private List<Site> loadRawSites(String globalSpider) {
        File dir = new File(CLAN_ROOT + "sites");
        List<Site> result = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return result;
        for (File file : listSorted(dir)) {
            FileMeta meta = parseFileMeta(file.getName());
            String content = Path.read(file);
            if (content.isEmpty()) continue;
            try {
                JsonElement element = Json.parse(content);
                if (element.isJsonObject()) {
                    Site site = Site.objectFrom(element, globalSpider);
                    if (site.getName().isEmpty()) site.setName(meta.name);
                    if (!site.getKey().isEmpty()) {
                        meta.apply(site);
                        result.add(site);
                    }
                }
            } catch (Throwable e) {
                SpiderDebug.log("vod-config", "skip malformed raw site file: %s err=%s", file.getName(), e.getMessage());
            }
        }
        return result;
    }

    private List<File> listSorted(File dir) {
        File[] files = dir.listFiles(f -> f.isFile() && !f.getName().startsWith("."));
        if (files == null) return Collections.emptyList();
        Arrays.sort(files, (a, b) -> {
            FileMeta ma = parseFileMeta(a.getName());
            FileMeta mb = parseFileMeta(b.getName());
            int c = Integer.compare(ma.order, mb.order);
            if (c != 0) return c;
            return ma.name.compareToIgnoreCase(mb.name);
        });
        return Arrays.asList(files);
    }

    /**
     * 文件名解析器 — 严格结构: [order_]name [sourceId] [.N | .S] [-H | -S | -ratio] .ext
     *
     *   排序前缀: ^\d+_ — 参与排序, 不进入 name
     *   源序号:   \d+-\d+ — 自动清理, 不影响任何标记
     *   点号标记: .N 或 .S — 互斥, 只认最后出现的那一个, 其余全部清除
     *              .N → searchable=0, quickSearch=0 (不参与搜索)
     *              .S → hide=1 (只用于搜索)
     *   横线标记: -H / -S / -<非0数字> — 互斥, 只认尾部那一个 (紧接扩展名前)
     *              -H → style rect 1.33 (横图)
     *              -S → style rect 1.0 (正方形)
     *              -<数字> → style rect ratio=数字 (如 -0.8 -1 -1.78)
     *
     * 解析顺序 (从右向左剥):
     *   1. 去掉扩展名
     *   2. 从尾部提取 style 横线标记 (严格锚定 $, 只取一个)
     *   3. 遍历所有点号标记 .N/.S, 只保留最后一个生效, 其余从 stem 中删除
     *   4. 从头部提取排序前缀
     *   5. cleanName 清理源序号、孤立数字等
     *
     * 示例:
     *   0_酷6网.N0-0-H.js     → order=0,   name=酷6网,  searchable=0, quickSearch=0, ratio=1.33
     *   10_优酷.S-1.78.js     → order=10,  name=优酷,   hide=1, ratio=1.78
     *   5_搜狐.N.js           → order=5,   name=搜狐,   searchable=0, quickSearch=0
     *   0_老源-S.json         → order=0,   name=老源,   ratio=1.0
     *   优酷.js                → order=MAX, name=优酷,   默认
     *   酷6网.N.S.js           → 只认最后 .S, hide=1 (.N 被清除)
     *   酷6网.S.N.js           → 只认最后 .N, searchable=0 (.S 被清除)
     */
    private FileMeta parseFileMeta(String fileName) {
        FileMeta meta = new FileMeta();
        if (fileName == null) return meta;

        String stem = fileName;
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) stem = fileName.substring(0, lastDot);

        // 1. 尾部 style 标记: -H / -S / -<非0数字> (严格从 stem 尾部取, 互斥只一个)
        Matcher tailStyle = Pattern.compile("(?i)(-H|-S|-\\d+(?:\\.\\d+)?)\\s*$").matcher(stem);
        if (tailStyle.find()) {
            String mark = tailStyle.group(1);
            if (mark.equalsIgnoreCase("-H")) meta.ratio = 1.33f;
            else if (mark.equalsIgnoreCase("-S")) meta.ratio = 1.0f;
            else {
                try {
                    float v = Float.parseFloat(mark.substring(1));
                    if (v != 0) meta.ratio = v;
                } catch (NumberFormatException ignored) {}
            }
            stem = stem.substring(0, tailStyle.start());
        }

        // 2. 点号标记: .N 与 .S 互斥 — 遍历所有出现位置, 只保留最后一个生效, 其余全部清除
        Pattern dotPattern = Pattern.compile("\\.(N|S)", Pattern.CASE_INSENSITIVE);
        Matcher dotMatcher = dotPattern.matcher(stem);
        String lastDotMarkType = null;
        while (dotMatcher.find()) {
            lastDotMarkType = dotMatcher.group(1).toUpperCase();
        }
        if (lastDotMarkType != null) {
            stem = stem.replaceAll("(?i)\\.[NS]", "");
            if ("N".equals(lastDotMarkType)) { meta.searchable = 0; meta.quickSearch = 0; }
            else if ("S".equals(lastDotMarkType)) { meta.hide = 1; }
        }

        // 3. 排序前缀 ^\d+_
        if (stem.matches("^\\d+_.*")) {
            int sep = stem.indexOf('_');
            meta.order = Integer.parseInt(stem.substring(0, sep));
            stem = stem.substring(sep + 1);
        }

        meta.name = cleanName(stem);
        return meta;
    }

    /**
     * 清洗站点名: 清理剩余的源序号、孤立数字、多余连字符。
     */
    private String cleanName(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = s.replaceAll("\\d+-\\d+", "");              // 源序号整段 0-0 / 1-2
        s = s.replaceAll("(?:^|[.\\s-])\\d+(?![\\p{L}])", "-"); // 分隔符后的纯数字串
        s = s.replaceAll("[-\\s.]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return s.trim();
    }

    private static class FileMeta {
        int order = Integer.MAX_VALUE;
        String name = "";
        Integer searchable;   // null=不覆盖, 0=禁搜索
        Integer quickSearch;  // null=不覆盖, 0=禁快搜
        Integer hide;         // null=不覆盖, 1=隐藏
        float ratio = 0;      // 0=不设置 style

        void apply(Site site) {
            if (searchable != null) site.setSearchable(searchable);
            if (quickSearch != null) site.setQuickSearch(quickSearch);
            if (hide != null) site.setHide(hide);
            if (ratio > 0) site.setStyle(new Style("rect", ratio));
        }
    }

    private void setHome(Config config, Site site, boolean save) {
        home = site;
        home.setSelected(true);
        config.setHome(home.getKey());
        if (save) config.save();
        getSites().forEach(item -> item.setSelected(home));
    }

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }
}