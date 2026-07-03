package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.AdapterHomeKeepBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/** 午夜首映 · 首页继续观看适配器（进度条 + 续播标记） */
public class HomeKeepAdapter extends RecyclerView.Adapter<HomeKeepAdapter.ViewHolder> {

    private final List<History> items = new ArrayList<>();
    private final OnClickListener listener;

    public HomeKeepAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {
        void onKeepClick(History item);
    }

    public void setItems(List<History> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public int size() {
        return items.size();
    }

    private static int percent(History item) {
        long duration = item.getDuration();
        if (duration <= 0) return 0;
        long pct = item.getPosition() * 100 / duration;
        return (int) Math.max(2, Math.min(100, pct));
    }

    private static String resume(History item) {
        if (!TextUtils.isEmpty(item.getVodRemarks())) return item.getVodRemarks();
        return "继续观看";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterHomeKeepBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        History item = items.get(position);
        holder.binding.name.setText(item.getVodName());
        holder.binding.resume.setText(resume(item));
        holder.binding.sub.setText(item.getSiteName());
        holder.binding.progress.setProgress(percent(item));
        holder.binding.getRoot().setOnClickListener(v -> listener.onKeepClick(item));
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterHomeKeepBinding binding;

        ViewHolder(@NonNull AdapterHomeKeepBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
