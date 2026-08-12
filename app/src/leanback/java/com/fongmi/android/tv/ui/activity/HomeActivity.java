package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.receiver.ShortcutReceiver;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.fragment.TypeFragment;
import com.fongmi.android.tv.ui.holder.FuncPresenter;
import com.fongmi.android.tv.ui.holder.HistoryPresenter;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.FuncPresenter.OnClickListener;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.ui.presenter.TypeAdapter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.DLNARendererService;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SpiderDebug;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.web.HomeWebController;
import com.fongmi.android.tv.web.WebHomeChrome;
import com.fongmi.android.tv.web.WebHomeChromeStartup;
import com.fongmi.android.tv.web.WebHomeViewport;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HistoryPresenter.OnClickListener, TypeAdapter.OnClickListener, HomeWebController.Listener {

    public static final String EXTRA_KEY = "extra_key";
    public static final String EXTRA_WORD = "extra_word";

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mFuncAdapter;
    private ArrayObjectAdapter mHistoryAdapter;
    private CustomScroller mScroller;
    private VodPresenter mPresenter;
    private Presenter mFuncPresenter;
    private Presenter mHistoryPresenter;
    private HomeWebController mWeb;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        SpiderDebug.log("startup", "home initView start cost=%sms", System.currentTimeMillis() - App.time());
        mBinding.title.setListener(this);
        mBinding.title.setSelectable(false);
        mBinding.title.setSelected(true);
        mBinding.title.setSubTitle(false);
        mBinding.title.setMaintainFocus(true);
        mBinding.title.enableFadingWhenDisabled(false);
        mBinding.title.setTitleSpacing(0);
        mBinding.title.setBadgeColor(Color.TRANSPARENT);
        mBinding.title.setMaintainTitleFocus(true);
        mBinding.title.setMaintainSubtitleFocus(true);
        EventBus.getDefault().register(this);
        setRecyclerView();
        setViewModel();
        setAdapter();
        runAfterFirstFrame(this::initAfterFirstFrame);
        SpiderDebug.log("startup", "home initView end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void initAfterFirstFrame() {
        SpiderDebug.log("startup", "home first frame cost=%sms", System.currentTimeMillis() - App.time());
        App.post(this::initConfig, 80);
        App.post(() -> PermissionUtil.requestFile(this, allGranted -> {
            PermissionUtil.requestNotify(this);
            if (allGranted) initConfig();
        }), 1800);
        App.post(() -> DLNARendererService.start(this), 2500);
    }

    private void runAfterFirstFrame(Runnable runnable) {
        View root = mBinding.getRoot();
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
                root.post(runnable);
                return true;
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (mWeb != null) mWeb.destroy();
        EventBus.getDefault().unregister(this);
        AppDatabase.backup();
        OkHttp.get().clear();
        Server.get().stop();
        DLNARendererService.stop(this);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWeb != null) mWeb.onResume();
    }

    @Override
    protected void onPause() {
        if (mWeb != null) mWeb.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (mWeb != null && mWeb.handleBack()) return;
        if (mBinding.progressLayout.isShowingContent()) {
            super.onBackPressed();
            return;
        }
        if (mBinding.browse != null && mBinding.browse.hasFocus()) {
            if (mBinding.progressLayout.isShowingContent()) {
                super.onBackPressed();
            } else {
                getVideo(true);
            }
            return;
        }
        if (mBinding.func.isFocused()) {
            if (getHistoryIndex() != -1) mBinding.browse.setSelectedPosition(getHistoryIndex());
            else mBinding.browse.setSelectedPosition(getRecommendIndex());
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (mWeb != null && mWeb.handleKey(keyCode, event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onTitleClick() {
        SiteDialog.create().change().show(this);
    }

    @Override
    public boolean onTitleLongClick() {
        reloadConfig();
        return true;
    }

    @Override
    public void onTitleRight() {
        if (getHome().getKey() != null) SearchActivity.start(this, "", getHome().getKey());
        else SearchActivity.start(this);
    }

    @Override
    public void onTitleLeft() {
        HistoryDialog.create().vod().show(this);
    }

    @Override
    public void onItemClick(Vod item) {
        VideoActivity.start(this, item);
    }

    @Override
    public boolean onLongClick(Vod item) {
        Vod vod = VodConfig.get().getVod(item.getVodId());
        if (vod == null) return false;
        Collect.collect(vod, !Collect.has(vod.getVodId()));
        Notify.show(Collect.has(vod.getVodId()) ? R.string.collect_add : R.string.collect_remove);
        VodConfig.get().setVod(vod);
        if (Collect.get().isEmpty()) getVideo();
        else getCollect();
        return true;
    }

    @Override
    public void onItemClick(Func item) {
        if (item.getResId() == R.string.home_live) LiveActivity.start(this);
        else if (item.getResId() == R.string.home_keep) KeepActivity.start(this);
        else if (item.getResId() == R.string.home_push) PushActivity.start(this);
        else if (item.getResId() == R.string.home_search) SearchActivity.start(this);
        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);
    }

    @Override
    public boolean onLongClick(Func item) {
        if (item.getResId() != R.string.home_search) return false;
        SearchActivity.start(this, "", getHome().getKey());
        return true;
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getVodId());
    }

    @Override
    public boolean onLongClick(History item) {
        History.delete(item.getVodId());
        Notify.show(R.string.del_success);
        if (getHistoryIndex() != -1) getHistory(true);
        else getVideo();
        return true;
    }

    private Vod getHome() {
        return VodConfig.get().getHome() == null ? new Vod() : VodConfig.get().getHome();
    }

    private void setRecyclerView() {
        mScroller = new CustomScroller(this);
        mScroller.setWindowAlignment(CustomScroller.WINDOW_ALIGN_HIGH);
        mScroller.setVerticalScrollBarEnabled(false);
        mScroller.setHorizontalScrollBarEnabled(false);
        mBinding.browse.setVerticalScrollBarEnabled(false);
        mBinding.browse.setHorizontalScrollBarEnabled(false);
    }

    private WebView getHomeWeb() {
        if (mHomeWeb != null) return mHomeWeb;
        mHomeWeb = new WebView(this);
        mHomeWeb.setFocusable(true);
        mHomeWeb.setFocusableInTouchMode(true);
        mHomeWeb.setVisibility(View.GONE);
        mBinding.webOverlay.addView(mHomeWeb, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return mHomeWeb;
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            boolean categoryResult = isHomeCategoryResult(result);
            mAdapter.remove("progress");
            if (categoryResult) {
                setAdapter();
                getVideo();
            } else {
                setVideo(result);
            }
        });
    }

    private boolean isHomeCategoryResult(Result result) {
        if (result != null && result.getItem().size() == 0) return true;
        String homeKey = VodConfig.get().getHome() == null ? "" : VodConfig.get().getHome().getKey();
        if (!VodConfig.get().getSites().isEmpty() && VodConfig.get().getHome() != null) {
            if (homeKey != null && !homeKey.isEmpty()) {
                for (Site s : VodConfig.get().getSites()) {
                    if (s.getKey().equals(homeKey)) return false;
                }
            }
        }
        return false;
    }

    private void setAdapter() {
        mBinding.browse.setFocusable(true);
        mBinding.browse.setHorizontalScrollBarEnabled(false);
        mBinding.browse.setVerticalScrollBarEnabled(false);
        mBinding.browse.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (mWeb != null) mWeb.refreshLayout();
        });
        mBinding.browse.setLayoutManager(mScroller);
        mBinding.browse.setOnChildSelectedListener((parent, view, position) -> {
            if (mWeb != null) mWeb.refreshLayout();
        });
        mBinding.browse.setOnScrollListener(new androidx.leanback.widget.RowHeaderView.OnUnhandledRowHoverListener() {
            @Override
            public boolean onUnhandledRowHover(@Nullable Row row) {
                return false;
            }
        });
        mBinding.browse.setOuterFocusSearchEnabled(true);
        mBinding.browse.setFocusScrollStrategy(CustomScroller.FOCUS_SCROLL_ALIGNED);
        mBinding.browse.setWindowAlignment(CustomScroller.WINDOW_ALIGN_HIGH);
        mBinding.browse.addOnKeyInterceptListener(new androidx.leanback.widget.ArrayObjectAdapter() {
            @Override
            public boolean onInterceptKey(android.view.KeyEvent event) {
                return false;
            }
        });
        mBinding.browse.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (mWeb != null) mWeb.refreshLayout();
            }
        });
    }

    private void initConfig() {
        SpiderDebug.log("startup", "config load start cost=%sms", System.currentTimeMillis() - App.time());
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                SpiderDebug.log("startup", "config load success cost=%sms", System.currentTimeMillis() - App.time());
                showContent();
            }

            @Override
            public void error(String msg) {
                SpiderDebug.log("startup", "config load error msg=%s cost=%sms", msg, System.currentTimeMillis() - App.time());
                if (msg == null || msg.isEmpty()) {
                    showContent();
                } else {
                    Notify.show(msg);
                    showContent();
                }
            }
        };
    }

    private void showContent() {
        SpiderDebug.log("startup", "home showContent start cost=%sms", System.currentTimeMillis() - App.time());
        mBinding.progressLayout.showContent();
        setTitle();
        setLogo();
        setFunc();
        setAdapter();
        initWeb();
        getVideo();
        SpiderDebug.log("startup", "home showContent end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void initWeb() {
        mWeb = new HomeWebController(this, getHomeWeb(), this);
        syncWebHomeChrome();
    }

    private void setTitle() {
        List<String> items = Arrays.asList(getHome().getName(), VodConfig.get().getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setTitle(s));
    }

    private void setLogo() {
        ImgUtil.logo(mBinding.logo);
    }

    private void setFunc() {
        List<Func> items = new ArrayList<>();
        if (LiveConfig.hasLoadedLives()) items.add(Func.create(R.string.home_live));
        items.add(Func.create(R.string.home_search));
        items.add(Func.create(R.string.home_keep));
        items.add(Func.create(R.string.home_push));
        items.add(Func.create(R.string.home_setting));
        mFuncAdapter.setItems(items, new BaseDiffCallback<Func>());
    }

    private void getHistory() {
        getHistory(false);
    }

    private void getHistory(boolean renew) {
        List<History> items = History.get();
        int historyIndex = getHistoryIndex();
        int recommendIndex = getRecommendIndex();
        boolean exist = recommendIndex - historyIndex == 2;
        if (renew) mHistoryAdapter = new ArrayObjectAdapter(mHistoryPresenter = new HistoryPresenter(this));
        if ((items.isEmpty() && exist) || (renew && exist)) mAdapter.removeItems(historyIndex, 1);
        if ((!items.isEmpty() && !exist) || (renew && exist)) mAdapter.add(historyIndex, new ListRow(mHistoryAdapter));
        mHistoryAdapter.setItems(items, new BaseDiffCallback<History>());
    }

    private void setHistoryDelete(boolean delete) {
        HistoryPresenter presenter = (HistoryPresenter) mAdapter.getPresenter(mAdapter.indexOf(null));
        if (presenter != null) presenter.setDelete(delete);
        int historyIndex = getHistoryIndex();
        if (historyIndex != -1) mAdapter.notifyArrayItemRangeChanged(historyIndex, 1);
    }

    private void setCollectDelete(boolean delete) {
        CollectPresenter presenter = (CollectPresenter) mAdapter.getPresenter(mAdapter.indexOf(new Collect()));
        if (presenter != null) presenter.setDelete(delete);
        int collectIndex = getCollectIndex();
        if (collectIndex != -1) mAdapter.notifyArrayItemRangeChanged(collectIndex, 1);
    }

    private int getHistoryIndex() {
        for (int i = 0; i < mAdapter.size(); i++) if (mAdapter.get(i) instanceof ListRow && ((ListRow) mAdapter.get(i)).getAdapter() == mHistoryAdapter) return i;
        return -1;
    }

    private int getRecommendIndex() {
        int index = getHistoryIndex();
        return index == -1 ? mAdapter.size() : index + 1;
    }

    private int getCollectIndex() {
        for (int i = 0; i < mAdapter.size(); i++) {
            Object item = mAdapter.get(i);
            if (item instanceof ListRow && ((ListRow) item).getHeaderItem() != null
                    && ((ListRow) item).getHeaderItem().getName().equals(getString(R.string.collect_title))) {
                return i;
            }
        }
        return -1;
    }

    private void getVideo() {
        getVideo(false);
    }

    private void getVideo(boolean refresh) {
        Site home = VodConfig.get().getHome();
        if (home == null || TextUtils.isEmpty(home.getKey())) {
            getCollect();
            return;
        }
        SpiderDebug.log("site-dialog", "getVideo start key=%s hasHomePage=%s refresh=%b", home.getKey(), home.hasHomePage(), refresh);
        if (mWeb != null && mWeb.isVisible()) {
            if (!mWeb.load(home, refresh)) getVideoInternal(home, refresh);
        } else {
            getVideoInternal(home, refresh);
        }
    }

    private void getVideoInternal(Site home, boolean refresh) {
        mBinding.progressLayout.showProgress();
        mBinding.progressLayout.showContent();
        mBinding.progressLayout.showProgress();
        Result result = Result.empty();
        boolean webLoaded = false;
        if (home.hasHomePage() && !refresh) {
            mViewModel.homeContent();
        } else {
            mViewModel.categoryContent(home.getKey(), home.getHome() == null || home.getHome().isEmpty() ? home.getFirstCategory() : home.getHome(), false, new JsonObject());
        }
    }

    private void setVideo(Result result) {
        Site home = VodConfig.get().getHome();
        List<Vod> items = result.getItem();
        List<Vod> allItems = new ArrayList<>(items);
        if (result.getItem().isEmpty()) {
            mAdapter.remove("progress");
            mAdapter.clear();
            mAdapter.add("progress");
            getCollect();
            return;
        }
        if (home != null && home.hasHomePage()) {
            setAdapterItems(allItems, home);
            return;
        }
        if (result.getItem().isEmpty()) {
            mAdapter.remove("progress");
            getCollect();
            return;
        }
        setAdapterItems(allItems, home);
    }

    private void setAdapterItems(List<Vod> items, Site home) {
        mAdapter.remove("progress");
        mAdapter.clear();
        if (items.isEmpty()) {
            mBinding.progressLayout.showEmpty();
            return;
        }
        mBinding.progressLayout.showContent();
        int style = home != null ? home.getStyle() : 0;
        if (style == 0) style = VodConfig.get().getStyle();
        Style s = Style.get(style);
        mPresenter = new VodPresenter(this, s);
        List<Vod> allItems = new ArrayList<>(items);
        int historyIndex = getHistoryIndex();
        if (historyIndex != -1) mAdapter.removeItems(historyIndex, 1);
        int position = mAdapter.size();
        mAdapter.add(new ListRow(new HeaderItem(position, getString(R.string.home_recommend)), new ArrayObjectAdapter(mPresenter)));
        ((ListRow) mAdapter.get(position)).getAdapter().addAll(0, allItems);
        getHistory();
        if (mBinding.browse.getSelectedPosition() == -1) mBinding.browse.setSelectedPosition(0);
    }

    private void getCollect() {
        mAdapter.remove("progress");
        mAdapter.clear();
        mBinding.progressLayout.showContent();
        List<Collect> collects = Collect.get();
        if (collects.isEmpty()) {
            mBinding.progressLayout.showEmpty();
            return;
        }
        int style = VodConfig.get().getStyle();
        Style s = Style.get(style);
        VodPresenter presenter = new VodPresenter(this, s);
        HeaderItem header = new HeaderItem(0, getString(R.string.collect_title));
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
        for (Collect collect : collects) {
            Vod vod = new Vod();
            vod.setVodId(collect.getVodId());
            vod.setVodName(collect.getVodName());
            vod.setVodPic(collect.getVodPic());
            adapter.add(vod);
        }
        mAdapter.add(new ListRow(header, adapter));
        if (mBinding.browse.getSelectedPosition() == -1) mBinding.browse.setSelectedPosition(0);
    }

    private void loadSite(String key, String home) {
        Site site = VodConfig.get().getSite(key);
        if (site == null) return;
        VodConfig.get().setHome(site);
        SiteDialog.create().select(key, home).show(this);
    }

    private void syncWebHomeChrome() {
        if (mWeb == null) return;
        WebHomeChrome.restore(VodConfig.get().getConfig(), VodConfig.get().getHome());
    }

    public void refreshWebHomeChromeState() {
        if (mWeb != null) mWeb.refreshChromeState();
    }

    private void showEmpty(CharSequence text) {
        if (TextUtils.isEmpty(text)) return;
        mBinding.progressLayout.showEmpty();
        if (mBinding.empty.getText() == null || !mBinding.empty.getText().equals(text)) {
            mBinding.empty.setText(text);
        }
    }

    private void showLoading() {
        mBinding.progressLayout.showProgress();
    }

    private void showContent() {
        mBinding.progressLayout.showContent();
    }

    private void showWebEmpty() {
        if (mAdapter != null && mAdapter.size() > 0) return;
        getCollect();
    }

    private void hideContent() {
        mBinding.progressLayout.showProgress();
    }

    public void openHistory() {
        HistoryDialog.create().vod().show(this);
    }

    public void openKeep() {
        KeepActivity.start(this);
    }

    public void openSetting() {
        SettingActivity.start(this);
    }

    public void openSearch() {
        SearchActivity.start(this, "", getHome().getKey());
    }

    @Override
    public void onWebLoading() {
        showLoading();
    }

    @Override
    public void onWebReady() {
        showContent();
    }

    @Override
    public void onWebError() {
        getVideo();
    }

    @Override
    public void onWebEmpty() {
        showWebEmpty();
    }

    @Override
    public void applyWebHomeChrome(String mode) {
        if (mWeb != null) mWeb.applyChrome(mode);
    }

    @Override
    public void applyWebHomeViewport(WebHomeViewport viewport) {
        if (mWeb != null) mWeb.setViewport(viewport);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.history();
                RefreshEvent.home();
                setLogo();
                break;
            case COMMON:
                setFunc();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                setTitle();
                SpiderDebug.log("site-dialog", "home refresh start key=%s homePage=%s", getHome().getKey(), getHome().hasHomePage());
                if (mWeb != null && mWeb.isVisible()) {
                    if (!mWeb.load(getHome(), true)) getVideo(true);
                } else {
                    getVideo(true);
                }
                break;
            case HISTORY:
                getHistory();
                break;
            case SIZE:
                getVideo();
                getHistory(true);
                break;
            case THEME:
                recreate();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStateEvent(StateEvent event) {
        if (event.type() == StateEvent.Type.PROGRESS) showLoading();
        if (event.type() == StateEvent.Type.EMPTY) showContent();
        if (event.type() == StateEvent.Type.ERROR) showEmpty(event.getMsg());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() == ServerEvent.Type.PUSH) VideoActivity.push(this, event.text());
        if (event.type() == ServerEvent.Type.SEARCH) SearchActivity.start(this, event.text());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == 1001) {
            String path = UrlUtil.getPathFromUri(this, data.getData());
            if (path == null) return;
            String key = UrlUtil.getName(path);
            loadSite(key, "");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1000 && grantResults.length > 0) {
            boolean granted = grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (granted) {
                initConfig();
            }
        }
    }

    @Override
    public void onItemClick(com.fongmi.android.tv.bean.Func item) {
        if (item.getResId() == R.string.home_live) LiveActivity.start(this);
        else if (item.getResId() == R.string.home_keep) KeepActivity.start(this);
        else if (item.getResId() == R.string.home_push) PushActivity.start(this);
        else if (item.getResId() == R.string.home_search) SearchActivity.start(this);
        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);
    }

    @Override
    public boolean onLongClick(com.fongmi.android.tv.bean.Func item) {
        if (item.getResId() != R.string.home_search) return false;
        SearchActivity.start(this, "", getHome().getKey());
        return true;
    }

    private void reloadConfig() {
        onRefresh();
    }

    @Override
    public void onRefresh() {
        if (mWeb != null && mWeb.isVisible()) mWeb.reload();
        else getVideo();
    }

    @Override
    public void setSite(Site item) {
        SpiderDebug.log("site-dialog", "set site key=%s name=%s homePage=%s", item.getKey(), item.getName(), item.hasHomePage());
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    @Override
    public void setFilter(String key, com.fongmi.android.tv.bean.Value value) {
    }

    @Override
    public void onItemClick(int position, com.fongmi.android.tv.bean.Class item) {
        TypeFragment fragment = (TypeFragment) getSupportFragmentManager().getFragments().stream().filter(f -> f instanceof TypeFragment).findFirst().orElse(null);
        if (fragment != null) fragment.onItemClick(position, item);
    }

    @Override
    public View getHomeChromeContainer() {
        return mBinding.webOverlay;
    }
}