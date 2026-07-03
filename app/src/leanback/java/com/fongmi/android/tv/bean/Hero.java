package com.fongmi.android.tv.bean;

/**
 * 午夜首映 · 电视端首页 Hero 推荐位包装类。
 * 仅用于让 leanback CustomSelector 用独立的 HeroPresenter 渲染，与普通 Vod 卡区分。
 */
public class Hero {

    private final Vod vod;

    public Hero(Vod vod) {
        this.vod = vod;
    }

    public Vod getVod() {
        return vod;
    }
}
