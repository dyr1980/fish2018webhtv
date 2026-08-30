package com.fongmi.android.tv.node;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 猫源本地包的识别。
 *
 * <p>本地包是用户自己解压出来的 {@code index.js} + {@code index.config.js} 目录，判定文件是
 * {@code index.js.md5}（CatPawOpen 的发布约定）。没有这个标记就无法与「随便一个目录」区分，
 * 所以不能只看目录里有没有 index.js。
 */
public class NodeLocalBundleTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void recognizesDirectoryWithMarker() throws IOException {
        File dir = pack("pkg");
        assertEquals("目录里有 index.js.md5 就是本地包", dir, NodeBundle.localDir(dir.getAbsolutePath(), null));
    }

    @Test
    public void recognizesFileInsidePackageAndReturnsItsDirectory() throws IOException {
        File dir = pack("pkg");
        assertEquals("选中包里任意文件都该定位到包目录",
                dir, NodeBundle.localDir(new File(dir, "index.js.md5").getAbsolutePath(), null));
        assertEquals(dir, NodeBundle.localDir(new File(dir, "index.js").getAbsolutePath(), null));
        assertEquals(dir, NodeBundle.localDir(new File(dir, "index.config.js").getAbsolutePath(), null));
    }

    /**
     * 用户常把普通订阅 json 和本地包丢在同一个文件夹（比如都在 Download 里）。若只看「父目录有没有
     * index.js.md5」，那个 json 会被判成本地包，配置内容被整个忽略，表现为选了 A 源却加载出 B 源。
     */
    @Test
    public void rejectsUnrelatedFileSittingNextToPackage() throws IOException {
        File dir = pack("mixed");
        File other = new File(dir, "my-subscription.json");
        Files.write(other.toPath(), "{\"sites\":[]}".getBytes(StandardCharsets.UTF_8));
        assertNull("包外文件不能顺推到包目录", NodeBundle.localDir(other.getAbsolutePath(), null));
    }

    @Test
    public void resolvesPathRelativeToExternalRoot() throws IOException {
        File root = folder.newFolder("sdcard");
        File dir = new File(root, "Download/pkg");
        write(dir);
        // 文件选择器生成的形态：file:/ 加上相对外部存储根的路径
        assertEquals("选择器给的相对路径要能还原",
                dir, NodeBundle.localDir("file://Download/pkg/index.js.md5", root));
    }

    @Test
    public void rejectsDirectoryWithoutMarker() throws IOException {
        File dir = folder.newFolder("plain");
        Files.write(new File(dir, "index.js").toPath(), "x".getBytes(StandardCharsets.UTF_8));
        assertNull("没有 index.js.md5 的目录不能当本地包，否则任何目录都会被误认", NodeBundle.localDir(dir.getAbsolutePath(), null));
    }

    /**
     * 本地包在主进程判定、在 {@code :node} 子进程加载。中间用户挪走了包，ensure 会落到远端下载分支，
     * 而那套逻辑拿不到 md5 时一律复用已有缓存——会把上一个源的 bundle 当就绪跑起来。所以要能识别
     * 出「这地址根本下载不了」并如实报错。
     */
    @Test
    public void distinguishesRemoteFromLocalAddresses() {
        assertTrue(NodeBundle.isRemote("https://host/index.js.md5"));
        assertTrue(NodeBundle.isRemote("HTTP://host/index.js.md5"));
        assertTrue("前后空白不该影响判定", NodeBundle.isRemote("  https://host/index.js.md5 "));
        assertFalse("本地路径不是可下载地址", NodeBundle.isRemote("file://catpkg"));
        assertFalse(NodeBundle.isRemote("/sdcard/catpkg"));
        assertFalse(NodeBundle.isRemote(null));
        assertFalse(NodeBundle.isRemote(""));
    }

    @Test
    public void rejectsRemoteAndMissingPaths() throws IOException {
        assertNull(NodeBundle.localDir(null, null));
        assertNull(NodeBundle.localDir("", null));
        assertNull("远端地址必须继续走下载分支", NodeBundle.localDir("https://host/index.js.md5", null));
        assertNull(NodeBundle.localDir("HTTP://host/index.js.md5", null));
        assertNull("不存在的路径不是本地包", NodeBundle.localDir(new File(folder.getRoot(), "absent").getAbsolutePath(), null));
    }

    @Test
    public void recognizesZipCarryingMarker() throws IOException {
        File zip = zip("pkg.zip", true);
        assertEquals("发布形态就是 zip，选中它要能识别", zip, NodeBundle.localZip(zip.getAbsolutePath(), null));
        assertNull("zip 不是解压目录，localDir 不该认它", NodeBundle.localDir(zip.getAbsolutePath(), null));
    }

    @Test
    public void rejectsUnrelatedZipAndNonZipFile() throws IOException {
        assertNull("不含 index.js.md5 的 zip 只是普通压缩包", NodeBundle.localZip(zip("other.zip", false).getAbsolutePath(), null));
        File plain = folder.newFile("notzip.bin");
        Files.write(plain.toPath(), "PK-not-really".getBytes(StandardCharsets.UTF_8));
        assertNull(NodeBundle.localZip(plain.getAbsolutePath(), null));
        assertNull(NodeBundle.localZip("https://host/pkg.zip", null));
    }

    private File zip(String name, boolean marker) throws IOException {
        File file = new File(folder.getRoot(), name);
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(file))) {
            if (marker) entry(out, "index.js.md5", "b24cea4ad00908b04d0fbe8d0a01999e");
            entry(out, "index.js", "module.exports={};");
            entry(out, "index.config.js", "module.exports={};");
        }
        return file;
    }

    private static void entry(java.util.zip.ZipOutputStream out, String name, String body) throws IOException {
        out.putNextEntry(new java.util.zip.ZipEntry(name));
        out.write(body.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private File pack(String name) throws IOException {
        File dir = folder.newFolder(name);
        write(dir);
        return dir;
    }

    private static void write(File dir) throws IOException {
        dir.mkdirs();
        Files.write(new File(dir, "index.js.md5").toPath(), "b24cea4ad00908b04d0fbe8d0a01999e".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "index.js").toPath(), "module.exports={};".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "index.config.js").toPath(), "module.exports={};".getBytes(StandardCharsets.UTF_8));
    }
}
