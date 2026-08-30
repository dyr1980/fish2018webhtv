package com.fongmi.android.tv.node;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Response;

/**
 * 猫源 bundle（CatPawOpen 的 {@code index.js}）的下载与缓存。
 *
 * <p>用户填的是 {@code .../index.js.md5}——那个地址返回 32 位校验值，真正的 bundle 在去掉
 * {@code .md5} 后缀的地址上。每次启动只拉几十字节的 md5 比对，命中就用本地缓存，
 * 避免重复下载 1.2MB 的 bundle。
 *
 * <p>本地包（用户自己解压出来的 {@code index.js} + {@code index.config.js} 目录）走同一套
 * 缓存判定，只是把「下载」换成「复制」、把远端 md5 换成本地文件的实际 md5。
 */
public final class NodeBundle {

    private static final String SUFFIX = ".md5";

    /** 本地包的判定文件，与 CatPawOpen 的发布约定一致。 */
    private static final String MARKER = "index.js.md5";

    /** 包内文件名。指到单个文件时只认这几个，否则任何与包同目录的文件都会被误判成本地包。 */
    private static final java.util.Set<String> MEMBERS = new java.util.HashSet<>(java.util.Arrays.asList(
            "index.js", MARKER, "index.config.js", "index.config.js.md5"));

    private NodeBundle() {
    }

    /** 是不是本地包（解压好的目录，或还没解压的 zip）。做 IO，异常一律当成不是。 */
    public static boolean isLocal(String url) {
        try {
            File root = Path.root();
            return localDir(url, root) != null || localZip(url, root) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 解析本地包所在目录。
     *
     * <p>接受三种写法：{@code file:/} 开头的相对外部存储路径（选择器生成的那种）、
     * 裸绝对路径、以及直接指向解压出来的目录。指到目录时要求里面有 {@code index.js.md5}，
     * 否则无法与「随便一个目录」区分。
     *
     * @param root 外部存储根目录，用于还原选择器生成的相对路径；可为 null
     * @return 本地包目录，不是本地包则返回 null
     */
    static File localDir(String url, File root) {
        File target = target(url, root);
        if (target == null) return null;
        if (target.isDirectory()) return new File(target, MARKER).exists() ? target : null;
        // 只有包内那几个文件才顺推到父目录。否则用户把普通订阅 json 跟包放在同一个文件夹里，
        // 那个 json 会被当成本地包，配置内容被整个忽略。
        if (!MEMBERS.contains(lower(target.getName()))) return null;
        File parent = target.getParentFile();
        return parent != null && new File(parent, MARKER).exists() ? parent : null;
    }

    /**
     * 解析还没解压的本地包 zip。发布出来的包就是这个形态，选一个文件比让用户在目录树里
     * 定位到解压后的文件夹省事。
     *
     * @return 含 {@code index.js.md5} 的 zip，不是则返回 null
     */
    static File localZip(String url, File root) {
        File target = target(url, root);
        if (target == null || !target.isFile()) return null;
        if (!lower(target.getName()).endsWith(".zip")) return null;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(target)) {
            return zip.getEntry(MARKER) != null ? target : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 固定用 ROOT 折叠大小写。土耳其语等 locale 下 {@code "INDEX.JS"} 的默认小写会把 I 变成 ı，
     * 那样文件名和 scheme 的比对都会落空。
     */
    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    /** 能直接下载的地址。非此即彼：不是远端就只能当本地路径处理。 */
    static boolean isRemote(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String value = lower(url.trim());
        return value.startsWith("http://") || value.startsWith("https://");
    }

    /** 把配置里存的地址还原成本地文件；远端地址返回 null。 */
    private static File target(String url, File root) {
        if (TextUtils.isEmpty(url) || isRemote(url)) return null;
        String path = url.trim();
        String lower = lower(path);
        if (lower.startsWith("file://")) path = path.substring(7);
        else if (lower.startsWith("file:/")) path = path.substring(6);
        return resolve(path, root);
    }

    /**
     * 选择器给的是相对外部存储的路径（{@code file://Download/x} 这种），用户手输的是绝对路径，
     * 而从 content URI 落地的缓存文件去掉 {@code file:/} 后会变成丢了根斜杠的绝对路径——三种都试。
     */
    private static File resolve(String path, File root) {
        if (TextUtils.isEmpty(path)) return null;
        if (root != null) {
            File relative = new File(root, path);
            if (relative.exists()) return relative;
        }
        File absolute = new File(path);
        if (absolute.exists()) return absolute;
        File rooted = new File("/" + path);
        return rooted.exists() ? rooted : null;
    }

    /** 用户填 .md5 地址（约定如此），也容忍直接填 bundle 地址。 */
    public static String bundleUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String trimmed = url.trim();
        return trimmed.endsWith(SUFFIX) ? trimmed.substring(0, trimmed.length() - SUFFIX.length()) : trimmed;
    }

    public static String md5Url(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String trimmed = url.trim();
        return trimmed.endsWith(SUFFIX) ? trimmed : trimmed + SUFFIX;
    }

    public static File dir(Context context) {
        File dir = new File(context.getFilesDir(), "node/bundle");
        dir.mkdirs();
        return dir;
    }

    public static File file(Context context) {
        return new File(dir(context), "index.js");
    }

    /**
     * bundle 的配置文件。服务端会在里面自动写好 {@code server.url} 和 {@code authorization}，
     * 且各站点的配置项（如 {@code ffm3u8.url}）都得由它提供——传空对象会让 bundle 在
     * 注册/首个请求阶段抛 undefined。
     */
    public static File config(Context context) {
        return new File(dir(context), "index.config.js");
    }

    private static File stamp(Context context) {
        return new File(dir(context), "index.js.md5");
    }

    private static File configStamp(Context context) {
        return new File(dir(context), "index.config.js.md5");
    }

    private static String configUrl(String url) {
        String bundle = bundleUrl(url);
        int slash = bundle.lastIndexOf('/');
        return slash < 0 ? bundle : bundle.substring(0, slash + 1) + "index.config.js";
    }

    /**
     * 确保本地 bundle 与远端一致。
     *
     * @return null 表示就绪，否则是失败原因
     */
    public static synchronized String ensure(Context context, String url) {
        File root = Path.root();
        File source = localDir(url, root);
        if (source != null) return ensureLocal(context, source);
        File zip = localZip(url, root);
        if (zip != null) return ensureZip(context, zip);
        // 走到这里必须是能下载的地址。本地包在主进程判定、在 :node 子进程加载，中间用户挪走了包
        // 就会落到这儿；下面那套逻辑对拿不到 md5 一律「复用已有缓存」，会把上一个源的 bundle
        // 当成就绪跑起来——用户选了 A 却加载出 B。所以这里如实报错。
        if (!isRemote(url)) return "猫源地址无法访问，本地包可能已被移动：" + url;
        try {
            File bundle = file(context);
            String remote = remoteMd5(url);
            if (bundle.exists() && bundle.length() > 0) {
                String local = read(stamp(context));
                // 远端拿不到 md5（离线等）时不该阻断，已有缓存就先用着。
                // 但配置仍要单独校验——服务端改夸克 Cookie 这类操作只会变 index.config.js，
                // bundle 的 md5 不变，早退会导致新配置永远拉不到。
                if (TextUtils.isEmpty(remote) || remote.equalsIgnoreCase(local)) return ensureConfig(context, url);
            } else if (TextUtils.isEmpty(remote)) {
                remote = "";
            }
            String error = download(bundleUrl(url), bundle);
            if (error != null) return error;
            String actual = TextUtils.isEmpty(remote) ? Util.md5(bundle) : remote;
            write(stamp(context), actual);
            return ensureConfig(context, url);
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    /** 配置文件同样带 .md5，按同一套缓存判定。 */
    private static String ensureConfig(Context context, String url) {
        File target = config(context);
        String remote = remoteMd5(configUrl(url));
        if (target.exists() && target.length() > 0) {
            String local = read(configStamp(context));
            if (TextUtils.isEmpty(remote) || remote.equalsIgnoreCase(local)) return null;
        }
        String error = download(configUrl(url), target);
        if (error != null) return error;
        try {
            write(configStamp(context), TextUtils.isEmpty(remote) ? Util.md5(target) : remote);
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * 本地包：把用户目录里的 {@code index.js} / {@code index.config.js} 同步到运行目录。
     *
     * <p>不能直接把运行目录指到用户选的位置——Node 会在 bundle 同级写 {@code data/}、
     * {@code port}、{@code boot.js}，外部存储上这些写入未必被允许，且用户可能随时移走。
     *
     * <p>用文件实际 md5 做缓存判定（而不是包里附带的 {@code .md5}，那个只是发布者给的，
     * 用户换了 index.js 却忘了改 md5 的话会同步不到）。
     */
    private static String ensureLocal(Context context, File dir) {
        File bundle = new File(dir, "index.js");
        // 单独挑 index.js.md5 时系统选择器只会把那一个文件拷进来，同级找不到 index.js。
        // 提示要指向整个包，否则用户只看到「缺少 index.js」而不知道该改选什么。
        if (!bundle.exists() || bundle.length() == 0) return "本地包缺少 index.js，请选择整个包（zip 或解压后的文件夹）";
        File config = new File(dir, "index.config.js");
        if (!config.exists() || config.length() == 0) return "本地包缺少 index.config.js";
        try {
            String error = sync(bundle, file(context), stamp(context));
            if (error != null) return error;
            return sync(config, config(context), configStamp(context));
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    /** 源文件 md5 与落地戳一致就跳过复制，避免每次启动都搬 9MB。 */
    private static String sync(File source, File target, File stamp) throws IOException {
        // 运行目录本身就满足本地包的判定（index.js + index.config.js + 那个 stamp 恰好叫
        // index.js.md5），用户真指到它时源和目标是同一个文件，开写就把 bundle 截断了。
        if (same(source, target)) return null;
        String md5 = Util.md5(source);
        if (target.exists() && target.length() > 0 && !TextUtils.isEmpty(md5) && md5.equalsIgnoreCase(read(stamp))) return null;
        String error = copy(source, target);
        if (error != null) return error;
        write(stamp, TextUtils.isEmpty(md5) ? Util.md5(target) : md5);
        return null;
    }

    /** 同一个文件（含符号链接/`..` 等写法差异）。取不到规范路径时退回绝对路径比较。 */
    private static boolean same(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    private static String copy(File source, File target) {
        try (InputStream in = new FileInputStream(source)) {
            pipe(in, target);
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            return "本地包复制失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return target.length() > 0 ? null : "本地包 " + source.getName() + " 为空";
    }

    private static void pipe(InputStream in, File target) throws IOException {
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[65536];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
        }
    }

    /**
     * 本地包 zip：直接把里面的 {@code index.js} / {@code index.config.js} 解到运行目录。
     *
     * <p>缓存判定用 zip 里附带的 {@code .md5}（换包必然换这个值），拿不到就退回解压后的实际 md5。
     * 不做「先整体解压到临时目录再搬」——包里就这两个文件，逐条目写更省一次 9MB 的落盘。
     */
    private static String ensureZip(Context context, File zip) {
        try (java.util.zip.ZipFile file = new java.util.zip.ZipFile(zip)) {
            String error = unzip(file, "index.js", file(context), stamp(context), MARKER);
            if (error != null) return error;
            return unzip(file, "index.config.js", config(context), configStamp(context), "index.config.js.md5");
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            return "本地包解压失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static String unzip(java.util.zip.ZipFile zip, String name, File target, File stamp, String digestName) throws IOException {
        java.util.zip.ZipEntry entry = zip.getEntry(name);
        if (entry == null) return "本地包缺少 " + name;
        String md5 = digest(zip, digestName);
        if (target.exists() && target.length() > 0 && !TextUtils.isEmpty(md5) && md5.equalsIgnoreCase(read(stamp))) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            pipe(in, target);
        }
        if (target.length() == 0) return "本地包 " + name + " 为空";
        write(stamp, TextUtils.isEmpty(md5) ? Util.md5(target) : md5);
        return null;
    }

    /** zip 里附带的校验值；缺失或格式不对时返回空串，由调用方退回实际 md5。 */
    private static String digest(java.util.zip.ZipFile zip, String name) {
        java.util.zip.ZipEntry entry = zip.getEntry(name);
        if (entry == null) return "";
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] buf = new byte[64];
            int len = in.read(buf);
            if (len <= 0) return "";
            String value = new String(buf, 0, len).trim();
            return value.length() == 32 ? value : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String remoteMd5(String url) {
        try {
            String text = OkHttp.string(md5Url(url));
            if (text == null) return "";
            String value = text.trim();
            return value.length() == 32 ? value : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String download(String url, File target) {
        try (Response res = OkHttp.newCall(url, "node-bundle").execute()) {
            if (!res.isSuccessful() || res.body() == null) return "bundle 下载失败 HTTP " + res.code();
            try (InputStream in = res.body().byteStream()) {
                pipe(in, target);
            }
            return target.length() > 0 ? null : "bundle 为空";
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private static String read(File file) {
        if (!file.exists()) return "";
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[64];
            int len = in.read(buf);
            return len <= 0 ? "" : new String(buf, 0, len).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void write(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes());
        }
    }
}
