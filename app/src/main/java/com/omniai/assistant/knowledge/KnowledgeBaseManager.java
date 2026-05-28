package com.omniai.assistant.knowledge;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.omniai.assistant.common.Constants;
import com.omniai.assistant.model.KnowledgeBase;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KnowledgeBaseManager {

    private List<KnowledgeBase> knowledgeBases;
    private EmbeddingEngine embeddingEngine;
    private VectorIndex vectorIndex;
    private SharedPreferences prefs;
    private Gson gson;
    private Map<String, VectorIndex> kbIndexes;
    private DocumentParser documentParser;
    private ExecutorService indexExecutor;

    private static final String PREFS_NAME = "omniai_knowledge_bases";
    private static final String KEY_KNOWLEDGE_BASES = "knowledge_bases";

    private static volatile KnowledgeBaseManager instance;

    public KnowledgeBaseManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.knowledgeBases = loadKnowledgeBasesFromPrefs();
        this.embeddingEngine = new EmbeddingEngine(context);
        this.vectorIndex = new VectorIndex();
        this.kbIndexes = new HashMap<>();
        this.documentParser = new DocumentParser();
        this.indexExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "KBIndexThread");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    public static KnowledgeBaseManager getInstance() {
        if (instance == null) {
            synchronized (KnowledgeBaseManager.class) {
                if (instance == null) {
                    throw new IllegalStateException("KnowledgeBaseManager not initialized. Call constructor first.");
                }
            }
        }
        return instance;
    }

    public void initialize() {
        if (!embeddingEngine.isInitialized()) {
            embeddingEngine.initialize(null, new EmbeddingEngine.InitCallback() {
                @Override
                public void onInitialized(int dimension) {
                    for (KnowledgeBase kb : knowledgeBases) {
                        VectorIndex idx = kbIndexes.get(kb.getId());
                        if (idx != null && idx.getDimension() != dimension) {
                            idx = new VectorIndex(dimension);
                            kbIndexes.put(kb.getId(), idx);
                        }
                    }
                }

                @Override
                public void onError(String error) {
                }
            });
        }

        for (KnowledgeBase kb : knowledgeBases) {
            String indexPath = Constants.KNOWLEDGE_DIR + "/" + kb.getId() + "/index.bin";
            File indexFile = new File(indexPath);
            if (indexFile.exists()) {
                try {
                    VectorIndex idx = new VectorIndex();
                    idx.loadIndex(indexPath);
                    kbIndexes.put(kb.getId(), idx);
                } catch (Exception e) {
                    kbIndexes.put(kb.getId(), new VectorIndex());
                }
            } else {
                kbIndexes.put(kb.getId(), new VectorIndex());
            }
        }
    }

    public KnowledgeBase createKnowledgeBase(String name, String description) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(name);
        kb.setDescription(description);
        kb.setStatus("idle");
        File kbDir = new File(Constants.KNOWLEDGE_DIR, kb.getId());
        if (!kbDir.exists()) {
            kbDir.mkdirs();
        }
        knowledgeBases.add(kb);
        kbIndexes.put(kb.getId(), new VectorIndex());
        saveKnowledgeBasesToPrefs();
        return kb;
    }

    public boolean deleteKnowledgeBase(String id) {
        KnowledgeBase target = getKnowledgeBase(id);
        if (target == null) {
            return false;
        }
        File kbDir = new File(Constants.KNOWLEDGE_DIR, id);
        if (kbDir.exists()) {
            deleteRecursive(kbDir);
        }
        kbIndexes.remove(id);
        knowledgeBases.remove(target);
        saveKnowledgeBasesToPrefs();
        return true;
    }

    public void importDocument(String kbId, String filePath, String format) {
        KnowledgeBase kb = getKnowledgeBase(kbId);
        if (kb == null) {
            throw new RuntimeException("Knowledge base not found: " + kbId);
        }
        String content;
        switch (format.toLowerCase()) {
            case "txt":
                content = documentParser.parseTxt(filePath);
                break;
            case "md":
            case "markdown":
                content = documentParser.parseMarkdown(filePath);
                break;
            case "pdf":
                content = documentParser.parsePdf(filePath);
                break;
            case "docx":
                content = documentParser.parseDocx(filePath);
                break;
            default:
                throw new IllegalArgumentException("Unsupported document format: " + format);
        }
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("Document is empty or could not be parsed: " + filePath);
        }
        List<String> chunks = documentParser.chunkText(content, 512, 64);
        VectorIndex index = kbIndexes.get(kbId);
        if (index == null) {
            index = new VectorIndex();
            kbIndexes.put(kbId, index);
        }
        if (embeddingEngine.isInitialized()) {
            List<float[]> embeddings = embeddingEngine.embedBatch(chunks);
            File file = new File(filePath);
            String source = file.getName();
            for (int i = 0; i < chunks.size(); i++) {
                index.addVector(embeddings.get(i), chunks.get(i), source);
            }
        }
        kb.setDocumentCount(kb.getDocumentCount() + 1);
        kb.setTotalSize(kb.getTotalSize() + new File(filePath).length());
        saveKnowledgeBasesToPrefs();
        saveKbIndex(kbId);
    }

    public void importUrl(String kbId, String url) {
        KnowledgeBase kb = getKnowledgeBase(kbId);
        if (kb == null) {
            throw new RuntimeException("Knowledge base not found: " + kbId);
        }
        String content = documentParser.parseHtml(url);
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("Failed to extract content from URL: " + url);
        }
        List<String> chunks = documentParser.chunkText(content, 512, 64);
        VectorIndex index = kbIndexes.get(kbId);
        if (index == null) {
            index = new VectorIndex();
            kbIndexes.put(kbId, index);
        }
        if (embeddingEngine.isInitialized()) {
            List<float[]> embeddings = embeddingEngine.embedBatch(chunks);
            for (int i = 0; i < chunks.size(); i++) {
                index.addVector(embeddings.get(i), chunks.get(i), url);
            }
        }
        kb.setDocumentCount(kb.getDocumentCount() + 1);
        saveKnowledgeBasesToPrefs();
        saveKbIndex(kbId);
    }

    public void importOcrText(String kbId, String text) {
        KnowledgeBase kb = getKnowledgeBase(kbId);
        if (kb == null) {
            throw new RuntimeException("Knowledge base not found: " + kbId);
        }
        if (text == null || text.trim().isEmpty()) {
            throw new RuntimeException("OCR text is empty");
        }
        List<String> chunks = documentParser.chunkText(text, 512, 64);
        VectorIndex index = kbIndexes.get(kbId);
        if (index == null) {
            index = new VectorIndex();
            kbIndexes.put(kbId, index);
        }
        if (embeddingEngine.isInitialized()) {
            List<float[]> embeddings = embeddingEngine.embedBatch(chunks);
            for (int i = 0; i < chunks.size(); i++) {
                index.addVector(embeddings.get(i), chunks.get(i), "ocr_text");
            }
        }
        kb.setDocumentCount(kb.getDocumentCount() + 1);
        saveKnowledgeBasesToPrefs();
        saveKbIndex(kbId);
    }

    public void buildIndex(String kbId) {
        KnowledgeBase kb = getKnowledgeBase(kbId);
        if (kb == null) {
            throw new RuntimeException("Knowledge base not found: " + kbId);
        }
        kb.setStatus("indexing");
        saveKnowledgeBasesToPrefs();
        indexExecutor.submit(() -> {
            try {
                VectorIndex index = kbIndexes.get(kbId);
                if (index != null && index.size() > 0) {
                    saveKbIndex(kbId);
                }
                kb.setStatus("idle");
            } catch (Exception e) {
                kb.setStatus("error");
            }
            saveKnowledgeBasesToPrefs();
        });
    }

    public List<SearchResult> search(String kbId, String query, int topK) {
        VectorIndex index = kbIndexes.get(kbId);
        if (index == null || index.size() == 0) {
            return new ArrayList<>();
        }
        if (!embeddingEngine.isInitialized()) {
            throw new IllegalStateException("Embedding engine is not initialized");
        }
        float[] queryEmbedding = embeddingEngine.embed(query);
        List<VectorIndex.SearchResult> vectorResults = index.search(queryEmbedding, topK);
        List<SearchResult> results = new ArrayList<>();
        for (VectorIndex.SearchResult vr : vectorResults) {
            results.add(new SearchResult(vr.content, vr.source, vr.score, 0));
        }
        return results;
    }

    public List<KnowledgeBase> listKnowledgeBases() {
        return Collections.unmodifiableList(knowledgeBases);
    }

    public KnowledgeBase getKnowledgeBase(String id) {
        if (id == null) {
            return null;
        }
        for (KnowledgeBase kb : knowledgeBases) {
            if (id.equals(kb.getId())) {
                return kb;
            }
        }
        return null;
    }

    public EmbeddingEngine getEmbeddingEngine() {
        return embeddingEngine;
    }

    private void saveKbIndex(String kbId) {
        VectorIndex index = kbIndexes.get(kbId);
        if (index != null && index.size() > 0) {
            String indexPath = Constants.KNOWLEDGE_DIR + "/" + kbId + "/index.bin";
            index.saveIndex(indexPath);
        }
    }

    private void saveKnowledgeBasesToPrefs() {
        String json = gson.toJson(knowledgeBases);
        prefs.edit().putString(KEY_KNOWLEDGE_BASES, json).apply();
    }

    private List<KnowledgeBase> loadKnowledgeBasesFromPrefs() {
        String json = prefs.getString(KEY_KNOWLEDGE_BASES, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Type type = new TypeToken<ArrayList<KnowledgeBase>>() {}.getType();
            List<KnowledgeBase> loaded = gson.fromJson(json, type);
            return loaded != null ? loaded : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    public static class SearchResult {

        private String content;
        private String source;
        private float score;
        private int chunkIndex;

        public SearchResult(String content, String source, float score, int chunkIndex) {
            this.content = content;
            this.source = source;
            this.score = score;
            this.chunkIndex = chunkIndex;
        }

        public String getContent() {
            return content;
        }

        public String getSource() {
            return source;
        }

        public float getScore() {
            return score;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }
    }
}
