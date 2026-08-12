package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.VodApi;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.api.parser.VodParser;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.CspWarmup;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VodConfig extends BaseConfig {

    private static final String TAG = VodConfig.class.getSimpleName();

    private Site home;
    private String wall;
    private List<Site> sites;
    private List<Parse> parses;
    private List<Rule> rules;
    private List<String> ads;
    private List<String> flags;

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static Site getHome() {
        return get().getHome();
    }

    public static String getResp() {
        return get().getHome().getCore().getResp();
    }

    public static List<Site> getSites() {
        return get().getSites();
    }

    public static List<Parse> getParses() {
        return get().getParses();
    }

    public static Parse getParse() {
        return get().getParse();
    }

    public static List<Doh> getDoh() {
        return get().getDoh();
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public VodConfig init() {
        return clear().config(Config.vod());
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
        if (config.isEmpty()) {
            try {
                initSites(config, "", new JsonObject());
            } catch (Throwable ignored) {}
            return;
        }
        String globalSpider = "";
        try {
            String json = Decoder.getJson(UrlUtil.convert(config.getUrl()), TAG);
            JsonObject object = Json.parse(json).getAsJsonObject();
            globalSpider = Json.safeString(object, "spider");
            checkJson(config, object);
            return;
        } catch (Throwable ignored) {}
        try {
            initSites(config, globalSpider, new JsonObject());
        } catch (Throwable ignored) {}
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
        initSites(config, Json.safeString(object, "spider"), object);
        initParse(config, object);
    }

    private void parseDepot(Config config, JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, VOD));
        if (configs.isEmpty()) throw new Exception("Depot urls is empty");
        load(this.config = configs.get(0));
        Config.delete(config.getUrl());
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initSites(Config config, String globalSpider, JsonObject object) {
        String spider = TextUtils.isEmpty(globalSpider) ? Json.safeString(object, "spider") : globalSpider;
        BaseLoader.get().parseJar(spider, true);
        List<Site> sites = Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new));
        List<Site> fileSites = loadFileSites(spider);
        sites.addAll(0, fileSites);
        setSites(sites);
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

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
        RuleConfig.get().invalidate();
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public void setHome(Site home) {
        setHome(getConfig(), home, true);
    }

    private void setHome(Config config, Site home, boolean save) {
        this.home = home;
        config.setHome(home.getKey());
        if (save) config.save();
        RuleConfig.get().invalidate();
    }

    private List<Site> loadFileSites(String spider) {
        List<Site> result = new ArrayList<>();
        if (!Setting.isFileSites()) return result;
        List<Site> jsonSites = loadFileSitesJson(spider);
        List<Site> jsSites = loadFileSitesJs(spider);
        List<Site> pySites = loadFileSitesPy(spider);
        List<Site> classicSites = loadFileSitesClassic(spider);
        result.addAll(jsonSites);
        result.addAll(jsSites);
        result.addAll(pySites);
        result.addAll(classicSites);
        return result;
    }

    private static final String CLAN_ROOT = Path.root() + "/tvbox/";

    private List<Site> loadFileSitesJson(String spider) {
        List<Site> result = new ArrayList<>();
        File dir = new File(CLAN_ROOT + "sites-json");
        if (!dir.exists() || !dir.isDirectory()) return result;
        File[] files = dir.listFiles(f -> f.isFile() && (f.getName().endsWith(".json") || f.getName().endsWith(".txt")) && !f.getName().startsWith("."));
        if (files == null || files.length == 0) return result;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            try {
                String content = Path.read(file);
                if (TextUtils.isEmpty(content)) continue;
                JsonObject object = Json.parse(content).getAsJsonObject();
                String name = Json.safeString(object, "name");
                String key = Json.safeString(object, "key");
                if (TextUtils.isEmpty(key)) {
                    String fname = file.getName();
                    int dot = fname.lastIndexOf('.');
                    key = dot > 0 ? fname.substring(0, dot) : fname;
                }
                if (TextUtils.isEmpty(name)) name = key;
                Site site = Site.objectFrom(object, spider);
                site.setKey(key);
                site.setName(name);
                site.setApi(TextUtils.isEmpty(site.getApi()) ? UrlUtil.convert("clan://sites-json/" + file.getName()) : site.getApi());
                site.setExt(UrlUtil.convert("clan://sites-json/" + file.getName()));
                site.setType(site.getType() == 0 ? 3 : site.getType());
                if (!site.getKey().isEmpty()) result.add(site);
            } catch (Throwable ignored) {}
        }
        return result;
    }

    private List<Site> loadFileSitesJs(String spider) {
        List<Site> result = new ArrayList<>();
        File dir = new File(CLAN_ROOT + "sites-js", "api");
        if (!dir.exists() || !dir.isDirectory()) return result;
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".js") && !f.getName().startsWith("."));
        if (files == null || files.length == 0) return result;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            try {
                String fname = file.getName();
                int dot = fname.lastIndexOf('.');
                String key = dot > 0 ? fname.substring(0, dot) : fname;
                Site site = new Site(key, UrlUtil.convert("clan://sites-js/api/" + file.getName())).setType(1).setSpider(spider);
                result.add(site);
            } catch (Throwable ignored) {}
        }
        return result;
    }

    private List<Site> loadFileSitesPy(String spider) {
        List<Site> result = new ArrayList<>();
        File dir = new File(CLAN_ROOT + "sites-py", "api");
        if (!dir.exists() || !dir.isDirectory()) return result;
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".py") && !f.getName().startsWith("."));
        if (files == null || files.length == 0) return result;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            try {
                String fname = file.getName();
                int dot = fname.lastIndexOf('.');
                String key = dot > 0 ? fname.substring(0, dot) : fname;
                Site site = new Site(key, UrlUtil.convert("clan://sites-py/api/" + file.getName())).setType(2).setSpider(spider);
                result.add(site);
            } catch (Throwable ignored) {}
        }
        return result;
    }

    private List<Site> loadFileSitesClassic(String spider) {
        List<Site> result = new ArrayList<>();
        File dir = new File(CLAN_ROOT + "sites");
        if (!dir.exists() || !dir.isDirectory()) return result;
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".js") && !f.getName().startsWith("."));
        if (files == null || files.length == 0) return result;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            try {
                String fname = file.getName();
                int dot = fname.lastIndexOf('.');
                String key = dot > 0 ? fname.substring(0, dot) : fname;
                Site site = new Site(key, UrlUtil.convert("clan://sites/" + file.getName())).setType(0).setSpider(spider);
                result.add(site);
            } catch (Throwable ignored) {}
        }
        return result;
    }

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }
}