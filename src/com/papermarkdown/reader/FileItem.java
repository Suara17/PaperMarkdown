package com.papermarkdown.reader;

import android.net.Uri;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileItem {
    public final String name;
    public final String path;
    public final Uri uri; // SAF 模式下使用
    public final String docId; // SAF 模式下该目录/文件的 documentId
    public final long size;
    public final long lastModified;
    public final boolean isDirectory;

    // 本地目录模式构造
    public FileItem(File file) {
        this.name = file.getName();
        this.path = file.getAbsolutePath();
        this.uri = null;
        this.docId = null;
        this.size = file.length();
        this.lastModified = file.lastModified();
        this.isDirectory = file.isDirectory();
    }

    // 本地子目录构造（仅目录）
    public FileItem(File dir, boolean isDir) {
        this.name = dir.getName();
        this.path = dir.getAbsolutePath();
        this.uri = null;
        this.docId = null;
        this.size = 0;
        this.lastModified = dir.lastModified();
        this.isDirectory = isDir;
    }

    // SAF 模式构造
    public FileItem(String name, Uri uri, String docId, long size, long lastModified, boolean isDirectory) {
        this.name = name;
        this.path = null;
        this.uri = uri;
        this.docId = docId;
        this.size = size;
        this.lastModified = lastModified;
        this.isDirectory = isDirectory;
    }

    public String getFormattedSize() {
        if (isDirectory) return "文件夹";
        if (size <= 0) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
    }

    public String getFormattedDate() {
        if (lastModified <= 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(lastModified));
    }
}