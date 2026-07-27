package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogUpdateApkSourceCustomBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.UpdateApkSource;

public final class UpdateApkSourceDialog {

    private static final String[] SOURCES = {
            UpdateApkSource.GH_PROXY,
            UpdateApkSource.GHFAST,
            UpdateApkSource.DIRECT,
            UpdateApkSource.CUSTOM
    };

    private UpdateApkSourceDialog() {
    }

    public static void show(FragmentActivity activity, Runnable onChanged) {
        CharSequence[] labels = {
                activity.getString(R.string.update_apk_source_gh_proxy),
                activity.getString(R.string.update_apk_source_ghfast),
                activity.getString(R.string.update_apk_source_direct),
                activity.getString(R.string.update_apk_source_custom)
        };
        ChoiceDialog.showSingle(activity, R.string.update_apk_source_title, labels, sourceIndex(Setting.getUpdateApkSource()), which -> {
            if (which < 0 || which >= SOURCES.length) return;
            if (UpdateApkSource.CUSTOM.equals(SOURCES[which])) {
                showCustom(activity, onChanged);
                return;
            }
            Setting.putUpdateApkSource(SOURCES[which]);
            if (onChanged != null) onChanged.run();
        });
    }

    public static String summary(Context context) {
        int label = switch (Setting.getUpdateApkSource()) {
            case UpdateApkSource.GHFAST -> R.string.update_apk_source_ghfast;
            case UpdateApkSource.DIRECT -> R.string.update_apk_source_direct;
            case UpdateApkSource.CUSTOM -> R.string.update_apk_source_custom;
            default -> R.string.update_apk_source_gh_proxy;
        };
        return context.getString(R.string.about_update_apk_source_value, context.getString(label));
    }

    private static int sourceIndex(String source) {
        for (int i = 0; i < SOURCES.length; i++) if (SOURCES[i].equals(source)) return i;
        return 0;
    }

    private static void showCustom(FragmentActivity activity, Runnable onChanged) {
        DialogUpdateApkSourceCustomBinding binding = DialogUpdateApkSourceCustomBinding.inflate(LayoutInflater.from(activity));
        binding.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        binding.input.setText(Setting.getUpdateApkCustomPrefix());
        binding.input.setSelection(binding.input.length());

        Dialog dialog = LightDialog.create(activity, activity.getString(R.string.update_apk_source_custom_title), binding.getRoot());
        binding.cancel.setOnClickListener(view -> dialog.dismiss());
        binding.save.setOnClickListener(view -> {
            String value = binding.input.getText() == null ? "" : binding.input.getText().toString();
            String prefix = UpdateApkSource.normalizeCustomPrefix(value);
            if (prefix.isEmpty()) {
                binding.inputLayout.setError(activity.getString(R.string.update_apk_source_custom_invalid));
                binding.input.requestFocus();
                return;
            }
            binding.inputLayout.setError(null);
            Setting.putUpdateApkCustomPrefix(prefix);
            Setting.putUpdateApkSource(UpdateApkSource.CUSTOM);
            dialog.dismiss();
            if (onChanged != null) onChanged.run();
        });
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        binding.save.requestFocus();
    }
}
