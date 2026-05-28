package com.omniai.assistant.knowledge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DocumentParser {

    private static final long MAX_TEXT_SIZE = 10 * 1024 * 1024;
    private static final int MAX_LINES = 100_000;
    private static final long MAX_PDF_SIZE = 50 * 1024 * 1024;
    private static final long MAX_PDF_TEXT_SIZE = 5 * 1024 * 1024;
    private static final long MAX_DOCX_SIZE = 50 * 1024 * 1024;

    public String parseTxt(String path) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                if (sb.length() + line.length() + 1 > MAX_TEXT_SIZE) {
                    break;
                }
                if (++lineCount > MAX_LINES) {
                    break;
                }
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse TXT file: " + path, e);
        }
        return sb.toString();
    }

    public String parseMarkdown(String path) {
        return parseTxt(path);
    }

    public String parsePdf(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                throw new RuntimeException("PDF file not found: " + path);
            }
            return extractPdfText(file);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse PDF file: " + path, e);
        }
    }

    private String extractPdfText(File file) {
        if (file.length() > MAX_PDF_SIZE) {
            throw new RuntimeException("PDF file too large: " + file.length() + " bytes (max " + MAX_PDF_SIZE + " bytes)");
        }
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[5];
            fis.read(header);
            String headerStr = new String(header, StandardCharsets.US_ASCII);
            if (!"%PDF-".equals(headerStr)) {
                throw new RuntimeException("Invalid PDF file format");
            }
            fis.getChannel().position(0);
            byte[] buffer = new byte[8192];
            int bytesRead;
            StringBuilder rawContent = new StringBuilder();
            while ((bytesRead = fis.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, bytesRead, StandardCharsets.US_ASCII);
                rawContent.append(chunk);
                if (rawContent.length() > MAX_PDF_TEXT_SIZE * 2) {
                    break;
                }
            }
            String content = rawContent.toString();
            int streamIndex = 0;
            while ((streamIndex = content.indexOf("stream", streamIndex)) != -1) {
                int endStream = content.indexOf("endstream", streamIndex);
                if (endStream == -1) break;
                String streamContent = content.substring(streamIndex + 6, endStream).trim();
                StringBuilder text = new StringBuilder();
                for (char c : streamContent.toCharArray()) {
                    if (c >= 32 && c < 127) {
                        text.append(c);
                    }
                }
                String extracted = text.toString().trim();
                if (extracted.length() > 10) {
                    if (sb.length() + extracted.length() + 1 > MAX_PDF_TEXT_SIZE) {
                        break;
                    }
                    sb.append(extracted).append("\n");
                }
                streamIndex = endStream + 9;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract PDF text", e);
        }
        return sb.toString();
    }

    public String parseDocx(String path) {
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(path);
            java.util.zip.ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) {
                zipFile.close();
                throw new RuntimeException("Invalid DOCX file: missing word/document.xml");
            }
            if (entry.getSize() > MAX_DOCX_SIZE) {
                zipFile.close();
                throw new RuntimeException("DOCX entry too large: " + entry.getSize() + " bytes (max " + MAX_DOCX_SIZE + " bytes)");
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8));
            StringBuilder xmlContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (xmlContent.length() + line.length() > MAX_TEXT_SIZE) {
                    break;
                }
                xmlContent.append(line);
            }
            reader.close();
            zipFile.close();
            return extractTextFromXml(xmlContent.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse DOCX file: " + path, e);
        }
    }

    private String extractTextFromXml(String xml) {
        StringBuilder text = new StringBuilder();
        boolean inTag = false;
        for (int i = 0; i < xml.length(); i++) {
            char c = xml.charAt(i);
            if (c == '<') {
                inTag = true;
                if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
                    if (xml.indexOf("</w:p>", i) > i && xml.indexOf("</w:p>", i) - i < 200) {
                        text.append('\n');
                    }
                }
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                text.append(c);
            }
        }
        return text.toString().replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .trim();
    }

    public String parseHtml(String url) {
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("HTTP request failed: " + response.code());
                }
                long contentLength = response.body().contentLength();
                if (contentLength > MAX_TEXT_SIZE) {
                    throw new RuntimeException("HTML response too large: " + contentLength + " bytes (max " + MAX_TEXT_SIZE + " bytes)");
                }
                String html = response.body().string();
                return extractTextFromHtml(html);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch HTML from: " + url, e);
        }
    }

    private String extractTextFromHtml(String html) {
        String text = html.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "")
                .replaceAll("<style[^>]*>[\\s\\S]*?</style>", "")
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("</p>", "\n")
                .replaceAll("</div>", "\n")
                .replaceAll("</li>", "\n")
                .replaceAll("</h[1-6]>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("[ \\t]+", " ")
                .trim();
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.append(trimmed).append("\n");
            }
        }
        return result.toString().trim();
    }

    public String extractOcrText(String imagePath) {
        return "";
    }

    public List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("Overlap must be non-negative");
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("Overlap must be less than chunk size");
        }
        int step = chunkSize - overlap;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start += step;
            if (end == text.length()) {
                break;
            }
        }
        return chunks;
    }
}
