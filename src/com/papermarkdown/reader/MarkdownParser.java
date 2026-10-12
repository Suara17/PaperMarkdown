package com.papermarkdown.reader;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    public static List<OutlineItem> extractOutline(String markdown) {
        List<OutlineItem> outline = new ArrayList<>();
        if (markdown == null || markdown.trim().isEmpty()) {
            return outline;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(markdown))) {
            String line;
            boolean inCodeBlock = false;
            int headingIndex = 0;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("```")) {
                    inCodeBlock = !inCodeBlock;
                    continue;
                }
                if (inCodeBlock) {
                    continue;
                }

                Matcher matcher = HEADING_PATTERN.matcher(trimmed);
                if (matcher.matches()) {
                    String hashes = matcher.group(1);
                    String title = matcher.group(2).trim();
                    // 去除标题末尾多余的 # 符号（如 # Title #）
                    title = title.replaceAll("\\s+#+$", "");
                    int level = hashes != null ? hashes.length() : 1;
                    outline.add(new OutlineItem(title, level, headingIndex++));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return outline;
    }
}