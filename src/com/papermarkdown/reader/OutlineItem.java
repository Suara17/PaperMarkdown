package com.papermarkdown.reader;

public class OutlineItem {
    public final String title;
    public final int level;
    public final int index;
    public boolean isCollapsed = false; // 是否折叠了子标题（仅针对有子项的节点有效）
    public boolean hasChildren = false;  // 是否包含子标题

    public OutlineItem(String title, int level, int index) {
        this.title = title;
        this.level = level;
        this.index = index;
    }
}