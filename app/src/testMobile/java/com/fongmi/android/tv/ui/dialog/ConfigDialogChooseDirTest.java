package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 猫源本地包是一整个文件夹（{@code index.js} + {@code index.config.js}），系统文件选择器
 * 选不到目录——两个 flavor 都必须另有「选目录」入口，否则这个功能从界面上根本用不了。
 *
 * <p>按本仓库既有做法断言源码文本：这两处是纯 UI 接线，起 Activity 才能验证的成本远高于收益。
 */
public class ConfigDialogChooseDirTest {

    private static final String MOBILE = "app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java";
    private static final String LEANBACK = "app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java";

    @Test
    public void bothFlavorsExposeDirectoryChooser() throws Exception {
        for (String file : new String[]{MOBILE, LEANBACK}) {
            String source = read(file);
            assertTrue(file + " 必须有选目录的处理方法", source.contains("private void onChooseDir(View view)"));
            assertTrue(file + " 选目录必须走 showDirectory()，show() 选不到文件夹",
                    source.contains("FileChooser.from(launcher).showDirectory();"));
        }
    }

    /** 入口只写方法不接线等于没有，所以单独钉住监听器注册。 */
    @Test
    public void directoryChooserIsWiredToAControl() throws Exception {
        assertTrue("mobile 要把选目录挂到输入框的 startIcon（endIcon 已被选文件占用）",
                read(MOBILE).contains("binding.choose.setStartIconOnClickListener(this::onChooseDir);"));
        assertTrue("leanback 要把选目录挂到独立按钮",
                read(LEANBACK).contains("binding.chooseDir.setOnClickListener(this::onChooseDir);"));
    }

    /** 选文件那条路不能被顶掉：本地包 zip 仍然靠它选。 */
    @Test
    public void fileChooserStaysAvailable() throws Exception {
        assertTrue(read(MOBILE).contains("binding.choose.setEndIconOnClickListener(this::onChoose);"));
        assertTrue(read(LEANBACK).contains("binding.choose.setOnClickListener(this::onChoose);"));
    }

    @Test
    public void layoutsProvideTheControlsThoseListenersBindTo() throws Exception {
        String mobile = read("app/src/mobile/res/layout/dialog_config.xml");
        assertTrue("mobile 布局要有 startIcon，否则 setStartIconOnClickListener 点不到",
                mobile.contains("app:startIconDrawable="));
        assertTrue("startIcon 要有无障碍描述", mobile.contains("app:startIconContentDescription="));
        assertTrue("leanback 布局要有 chooseDir 按钮，否则 binding.chooseDir 编译不过",
                read("app/src/leanback/res/layout/dialog_config.xml").contains("android:id=\"@+id/chooseDir\""));
    }

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
