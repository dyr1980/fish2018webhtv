package com.fongmi.android.tv.ui.presenter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.bean.Hero;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterHeroBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class HeroPresenter extends Presenter {

    private final OnClickListener listener;

    public HeroPresenter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(Hero item);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterHeroBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Hero item = (Hero) object;
        Vod vod = item.getVod();
        ViewHolder holder = (ViewHolder) viewHolder;
        String desc = clean(vod.getContent());
        String meta = buildMeta(vod);
        holder.binding.name.setText(vod.getName());
        holder.binding.meta.setText(meta);
        holder.binding.meta.setVisibility(TextUtils.isEmpty(meta) ? View.GONE : View.VISIBLE);
        holder.binding.desc.setText(desc);
        holder.binding.desc.setVisibility(TextUtils.isEmpty(desc) ? View.GONE : View.VISIBLE);
        holder.view.setOnClickListener(view -> listener.onItemClick(item));
        ImgUtil.load(vod.getName(), vod.getPic(), holder.binding.image);
    }

    private String buildMeta(Vod vod) {
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(vod.getYear())) parts.add(vod.getYear().trim());
        if (!TextUtils.isEmpty(vod.getTypeName())) parts.add(vod.getTypeName().trim());
        if (!TextUtils.isEmpty(vod.getRemarks())) parts.add(vod.getRemarks().trim());
        return TextUtils.join("  ·  ", parts);
    }

    private String clean(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterHeroBinding binding;

        public ViewHolder(@NonNull AdapterHeroBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
