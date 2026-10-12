package com.papermarkdown.reader;

public class OutlineItem {
    public final String title;
    public final int level;
    public final int index;

    public OutlineItem(String title, int level, int index) {
        this.title = title;
        this.level = level;
        this.index = index;
    }
}