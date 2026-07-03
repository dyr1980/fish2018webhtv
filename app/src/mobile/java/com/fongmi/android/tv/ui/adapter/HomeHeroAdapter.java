package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterHomeHeroBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/** 午夜首映 · 首页 Hero 轮播适配器（竖排宋体片名 + 立即播放） */
public class HomeHeroAdapter extends RecyclerView.Adapter<HomeHeroAdapter.ViewHolder> {

    private final List<Vod> items = new ArrayList<>();
    private final OnClickListener listener;

    public HomeHeroAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {
        void onHeroPlay(Vod item);

        void onHeroKeep(Vod item);
    }

    public void setItems(List<Vod> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public int size() {
        return items.size();
    }

    static CharSequence vertical(String text) {
        if (TextUtils.isEmpty(text)) return "";
        String s = text.trim();
        if (s.length() > 6) s = s.substring(0, 6);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static String meta(Vod item) {
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(item.getYear())) parts.add(item.getYear());
        if (!TextUtils.isEmpty(item.getRemarks())) parts.add(item.getRemarks());
        if (!TextUtils.isEmpty(item.getTypeName())) parts.add(item.getTypeName());
        return TextUtils.join("  ·  ", parts);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterHomeHeroBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Vod item = items.get(position);
        holder.binding.name.setText(vertical(item.getName()));
        holder.binding.meta.setText(meta(item));
        holder.binding.play.setOnClickListener(v -> listener.onHeroPlay(item));
        holder.binding.keep.setOnClickListener(v -> listener.onHeroKeep(item));
        ImgUtil.load(item.getName(), item.getPic(), holder.binding.image);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterHomeHeroBinding binding;

        ViewHolder(@NonNull AdapterHomeHeroBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
