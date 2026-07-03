package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterHomeRankBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/** 午夜首映 · 首页本周热播适配器（宋体大序号 + 海报） */
public class HomeRankAdapter extends RecyclerView.Adapter<HomeRankAdapter.ViewHolder> {

    private final List<Vod> items = new ArrayList<>();
    private final OnClickListener listener;

    public HomeRankAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {
        void onRankClick(Vod item);
    }

    public void setItems(List<Vod> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public int size() {
        return items.size();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterHomeRankBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Vod item = items.get(position);
        boolean top = position == 0;
        holder.binding.num.setText(String.valueOf(position + 1));
        holder.binding.num.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), top ? R.color.md_amber : R.color.md_mist_dim));
        holder.binding.tag.setVisibility(top ? View.VISIBLE : View.GONE);
        holder.binding.getRoot().setOnClickListener(v -> listener.onRankClick(item));
        ImgUtil.load(item.getName(), item.getPic(), holder.binding.image);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterHomeRankBinding binding;

        ViewHolder(@NonNull AdapterHomeRankBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
