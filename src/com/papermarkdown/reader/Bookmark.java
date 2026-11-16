package com.papermarkdown.reader;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 书签数据模型：记录某个文档中某一处阅读位置。
 * 采用「文字锚点 + 像素偏移」双保险定位，原文改动后仍能尽量跳回原处。
 */
public class Bookmark {
    public String title;      // 书签名称（默认取所在章节标题或首段文字）
    public String snippet;    // 位置处的文字片段，用于定位与列表预览
    public int offsetPx;      // 阅读时的滚动像素偏移，文字定位失败时兜底
    public long createdAt;    // 创建时间戳
    public String docKey;     // 所属文档标识（本地为绝对路径，SAF 为 URI）

    public Bookmark(String docKey, String title, String snippet, int offsetPx, long createdAt) {
        this.docKey = docKey;
        this.title = title;
        this.snippet = snippet;
        this.offsetPx = offsetPx;
        this.createdAt = createdAt;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("t", title == null ? "" : title);
            o.put("s", snippet == null ? "" : snippet);
            o.put("o", offsetPx);
            o.put("c", createdAt);
            o.put("d", docKey == null ? "" : docKey);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o;
    }

    public static Bookmark fromJson(JSONObject o) {
        if (o == null) return null;
        return new Bookmark(
                o.optString("d", ""),
                o.optString("t", "未命名书签"),
                o.optString("s", ""),
                o.optInt("o", 0),
                o.optLong("c", 0L)
        );
    }
}
