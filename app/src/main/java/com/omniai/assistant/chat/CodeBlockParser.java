package com.omniai.assistant.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeBlockParser {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\s*\\n(.*?)```", Pattern.DOTALL);

    public static class CodeBlock {
        private final String language;
        private final String code;

        public CodeBlock(String language, String code) {
            this.language = language != null ? language : "";
            this.code = code != null ? code : "";
        }

        public String getLanguage() {
            return language;
        }

        public String getCode() {
            return code;
        }
    }

    public static class ContentSegment {
        public static final String TYPE_TEXT = "TEXT";
        public static final String TYPE_CODE = "CODE";

        private final String type;
        private final String content;
        private final String language;

        public ContentSegment(String type, String content, String language) {
            this.type = type;
            this.content = content != null ? content : "";
            this.language = language;
        }

        public String getType() {
            return type;
        }

        public String getContent() {
            return content;
        }

        public String getLanguage() {
            return language;
        }

        public boolean isCode() {
            return TYPE_CODE.equals(type);
        }

        public boolean isText() {
            return TYPE_TEXT.equals(type);
        }
    }

    public static List<CodeBlock> parseCodeBlocks(String markdown) {
        List<CodeBlock> blocks = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return blocks;
        }

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String language = matcher.group(1);
            String code = matcher.group(2);
            blocks.add(new CodeBlock(language, code));
        }
        return blocks;
    }

    public static List<ContentSegment> parseContentSegments(String markdown) {
        List<ContentSegment> segments = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return segments;
        }

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(markdown);
        int lastEnd = 0;

        while (matcher.find()) {
            int start = matcher.start();
            if (start > lastEnd) {
                String text = markdown.substring(lastEnd, start);
                if (!text.isEmpty()) {
                    segments.add(new ContentSegment(ContentSegment.TYPE_TEXT, text, null));
                }
            }

            String language = matcher.group(1);
            String code = matcher.group(2);
            segments.add(new ContentSegment(ContentSegment.TYPE_CODE, code, language));

            lastEnd = matcher.end();
        }

        if (lastEnd < markdown.length()) {
            String remaining = markdown.substring(lastEnd);
            if (!remaining.isEmpty()) {
                segments.add(new ContentSegment(ContentSegment.TYPE_TEXT, remaining, null));
            }
        }

        return segments;
    }
}
