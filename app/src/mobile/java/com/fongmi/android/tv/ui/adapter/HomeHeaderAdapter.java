package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ViewMobileHomeHeaderBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

/** 午夜首映 · 首页头部（ConcatAdapter header）：Hero 轮播 + 继续观看 + 本周热播 + 新片标题 */
public class HomeHeaderAdapter extends RecyclerView.Adapter<HomeHeaderAdapter.ViewHolder> {

    private final List<Vod> heroList = new ArrayList<>();
    private final List<History> keepList = new ArrayList<>();
    private final List<Vod> rankList = new ArrayList<>();
    private final Callback callback;

    public HomeHeaderAdapter(Callback callback) {
        this.callback = callback;
    }

    public interface Callback extends HomeHeroAdapter.OnClickListener, HomeKeepAdapter.OnClickListener, HomeRankAdapter.OnClickListener {
        void onMoreHistory();
    }

    public void setData(List<Vod> hero, List<History> keep, List<Vod> rank) {
        heroList.clear();
        keepList.clear();
        rankList.clear();
        if (hero != null) heroList.addAll(hero);
        if (keep != null) keepList.addAll(keep);
        if (rank != null) rankList.addAll(rank);
        notifyDataSetChanged();
    }

    public boolean hasContent() {
        return !heroList.isEmpty();
    }

    @Override
    public int getItemCount() {
        return heroList.isEmpty() ? 0 : 1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ViewMobileHomeHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), callback);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(heroList, keepList, rankList);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ViewMobileHomeHeaderBinding binding;
        private final HomeHeroAdapter heroAdapter;
        private final HomeKeepAdapter keepAdapter;
        private final HomeRankAdapter rankAdapter;
        private final Callback callback;

        ViewHolder(@NonNull ViewMobileHomeHeaderBinding binding, Callback callback) {
            super(binding.getRoot());
            this.binding = binding;
            this.callback = callback;
            this.heroAdapter = new HomeHeroAdapter(callback);
            this.keepAdapter = new HomeKeepAdapter(callback);
            this.rankAdapter = new HomeRankAdapter(callback);
            binding.heroPager.setAdapter(heroAdapter);
            binding.heroPager.setOffscreenPageLimit(1);
            setHeroHeight();
            binding.keepRecycler.setLayoutManager(new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
            binding.keepRecycler.setAdapter(keepAdapter);
            binding.rankRecycler.setLayoutManager(new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
            binding.rankRecycler.setAdapter(rankAdapter);
            binding.keepMore.setOnClickListener(v -> callback.onMoreHistory());
            binding.heroPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    buildDots(heroAdapter.size(), position);
                }
            });
        }

        void bind(List<Vod> hero, List<History> keep, List<Vod> rank) {
            heroAdapter.setItems(hero);
            keepAdapter.setItems(keep);
            rankAdapter.setItems(rank);
            binding.keepGroup.setVisibility(keep.isEmpty() ? View.GONE : View.VISIBLE);
            binding.rankGroup.setVisibility(rank.isEmpty() ? View.GONE : View.VISIBLE);
            binding.gridGroup.setVisibility(hero.isEmpty() ? View.GONE : View.VISIBLE);
            buildDots(hero.size(), binding.heroPager.getCurrentItem());
        }

        private void setHeroHeight() {
            // 响应式：Hero 高度按屏宽比例（0.78），限制在 240~360dp，适配小屏/大屏/平板
            int available = ResUtil.getScreenWidth() - ResUtil.dp2px(40);
            int height = Math.max(ResUtil.dp2px(240), Math.min(ResUtil.dp2px(360), (int) (available * 0.78f)));
            ViewGroup.LayoutParams params = binding.heroPager.getLayoutParams();
            if (params.height == height) return;
            params.height = height;
            binding.heroPager.setLayoutParams(params);
        }

        private void buildDots(int count, int selected) {
            binding.heroDots.removeAllViews();
            if (count <= 1) return;
            for (int i = 0; i < count; i++) {
                boolean on = i == selected;
                View dot = new View(itemView.getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ResUtil.dp2px(on ? 18 : 5), ResUtil.dp2px(5));
                if (i < count - 1) lp.rightMargin = ResUtil.dp2px(6);
                dot.setLayoutParams(lp);
                dot.setBackgroundResource(on ? R.drawable.mdm_dot_on : R.drawable.mdm_dot);
                binding.heroDots.addView(dot);
            }
        }
    }
}
