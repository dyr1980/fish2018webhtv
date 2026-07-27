package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.List;

public final class UpdateDownloadQueue {

    private final List<String> candidates;
    private int index = -1;
    private boolean canceled;

    public UpdateDownloadQueue(List<String> candidates) {
        this.candidates = candidates == null ? List.of() : new ArrayList<>(candidates);
    }

    public String next() {
        if (canceled || index + 1 >= candidates.size()) return null;
        return candidates.get(++index);
    }

    public void cancel() {
        canceled = true;
    }

    public int size() {
        return candidates.size();
    }
}
