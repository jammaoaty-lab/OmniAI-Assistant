package com.omniai.assistant.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class VectorIndex {

    private List<float[]> vectors;
    private List<String> contents;
    private List<String> sources;
    private int dimension;

    private static final int HNSW_M = 16;
    private static final int HNSW_EF_CONSTRUCTION = 200;
    private static final int HNSW_EF_SEARCH = 50;
    private static final int HNSW_MAX_LEVEL = 5;

    private HNSWGraph hnswGraph;
    private boolean useHNSW;
    private int vectorCount;

    public VectorIndex() {
        this(768);
    }

    public VectorIndex(int dimension) {
        this.dimension = dimension;
        this.vectors = new ArrayList<>();
        this.contents = new ArrayList<>();
        this.sources = new ArrayList<>();
        this.useHNSW = false;
        this.vectorCount = 0;
    }

    public void enableHNSW() {
        this.useHNSW = true;
        this.hnswGraph = new HNSWGraph(dimension, HNSW_M, HNSW_EF_CONSTRUCTION);
    }

    public void disableHNSW() {
        this.useHNSW = false;
        this.hnswGraph = null;
    }

    public void addVector(float[] vector, String content, String source) {
        if (vector == null || vector.length != dimension) {
            throw new IllegalArgumentException("Vector dimension mismatch. Expected: " + dimension + ", Got: " + (vector != null ? vector.length : 0));
        }
        int index = vectors.size();
        vectors.add(vector.clone());
        contents.add(content);
        sources.add(source);
        vectorCount++;

        if (useHNSW && hnswGraph != null) {
            hnswGraph.insert(vector, index);
        }
    }

    public void removeVector(int index) {
        if (index < 0 || index >= vectors.size()) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        }
        vectors.remove(index);
        contents.remove(index);
        sources.remove(index);
        vectorCount = vectors.size();
        if (useHNSW) {
            hnswGraph = new HNSWGraph(dimension, HNSW_M, HNSW_EF_CONSTRUCTION);
            for (int i = 0; i < vectors.size(); i++) {
                hnswGraph.insert(vectors.get(i), i);
            }
        }
    }

    public List<SearchResult> search(float[] query, int topK) {
        if (query == null || query.length != dimension) {
            throw new IllegalArgumentException("Query vector dimension mismatch");
        }
        if (vectors.isEmpty()) {
            return new ArrayList<>();
        }
        float queryNorm = norm(query);
        if (queryNorm == 0) {
            return new ArrayList<>();
        }

        if (useHNSW && hnswGraph != null && vectorCount > HNSW_M * 2) {
            return searchHNSW(query, topK, queryNorm);
        }

        return searchBruteForce(query, topK, queryNorm);
    }

    private List<SearchResult> searchBruteForce(float[] query, int topK, float queryNorm) {
        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            float similarity = cosineSimilarity(query, vectors.get(i), queryNorm);
            results.add(new SearchResult(contents.get(i), sources.get(i), similarity));
        }
        results.sort((a, b) -> Float.compare(b.score, a.score));
        if (topK > 0 && results.size() > topK) {
            return new ArrayList<>(results.subList(0, topK));
        }
        return results;
    }

    private List<SearchResult> searchHNSW(float[] query, int topK, float queryNorm) {
        List<Integer> candidateIndices = hnswGraph.search(query, Math.max(topK, HNSW_EF_SEARCH));

        List<SearchResult> results = new ArrayList<>();
        for (int idx : candidateIndices) {
            if (idx >= 0 && idx < vectors.size()) {
                float similarity = cosineSimilarity(query, vectors.get(idx), queryNorm);
                results.add(new SearchResult(contents.get(idx), sources.get(idx), similarity));
            }
        }

        results.sort((a, b) -> Float.compare(b.score, a.score));
        if (topK > 0 && results.size() > topK) {
            return new ArrayList<>(results.subList(0, topK));
        }
        return results;
    }

    public void buildHNSWIndex() {
        if (vectors.isEmpty()) return;
        this.hnswGraph = new HNSWGraph(dimension, HNSW_M, HNSW_EF_CONSTRUCTION);
        for (int i = 0; i < vectors.size(); i++) {
            hnswGraph.insert(vectors.get(i), i);
        }
        this.useHNSW = true;
    }

    private float cosineSimilarity(float[] a, float[] b, float normA) {
        float normB = norm(b);
        if (normB == 0) {
            return 0f;
        }
        float dot = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot / (normA * normB);
    }

    private float norm(float[] v) {
        float sum = 0f;
        for (float f : v) {
            sum += f * f;
        }
        return (float) Math.sqrt(sum);
    }

    public int size() {
        return vectorCount;
    }

    public void clear() {
        vectors.clear();
        contents.clear();
        sources.clear();
        vectorCount = 0;
        if (hnswGraph != null) {
            hnswGraph = new HNSWGraph(dimension, HNSW_M, HNSW_EF_CONSTRUCTION);
        }
    }

    public void saveIndex(String path) {
        try {
            java.io.File file = new java.io.File(path);
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            java.io.DataOutputStream dos = new java.io.DataOutputStream(
                    new java.io.BufferedOutputStream(new java.io.FileOutputStream(file)));
            dos.writeInt(dimension);
            dos.writeInt(vectors.size());
            dos.writeBoolean(useHNSW);
            for (int i = 0; i < vectors.size(); i++) {
                float[] vec = vectors.get(i);
                dos.writeInt(vec.length);
                for (float v : vec) {
                    dos.writeFloat(v);
                }
                dos.writeUTF(contents.get(i));
                dos.writeUTF(sources.get(i));
            }
            dos.flush();
            dos.close();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to save index: " + e.getMessage(), e);
        }
    }

    public void loadIndex(String path) {
        try {
            java.io.DataInputStream dis = new java.io.DataInputStream(
                    new java.io.BufferedInputStream(new java.io.FileInputStream(path)));
            int dim = dis.readInt();
            int count = dis.readInt();
            boolean hasHNSW = dis.readBoolean();
            List<float[]> loadedVectors = new ArrayList<>();
            List<String> loadedContents = new ArrayList<>();
            List<String> loadedSources = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int vecLen = dis.readInt();
                float[] vec = new float[vecLen];
                for (int j = 0; j < vecLen; j++) {
                    vec[j] = dis.readFloat();
                }
                loadedVectors.add(vec);
                loadedContents.add(dis.readUTF());
                loadedSources.add(dis.readUTF());
            }
            dis.close();
            this.dimension = dim;
            this.vectors = loadedVectors;
            this.contents = loadedContents;
            this.sources = loadedSources;
            this.vectorCount = count;

            if (hasHNSW && count > HNSW_M * 2) {
                buildHNSWIndex();
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load index: " + e.getMessage(), e);
        }
    }

    public int getDimension() {
        return dimension;
    }

    public boolean isHNSWEnabled() {
        return useHNSW;
    }

    private static class HNSWGraph {
        private final int dimension;
        private final int m;
        private final int efConstruction;
        private final List<Node> nodes;
        private int entryPoint;
        private int maxLevel;
        private final java.util.Random random;

        private static class Node {
            int id;
            float[] vector;
            List<List<Integer>> neighbors;

            Node(int id, float[] vector, int maxLevel) {
                this.id = id;
                this.vector = vector;
                this.neighbors = new ArrayList<>();
                for (int i = 0; i <= maxLevel; i++) {
                    this.neighbors.add(new ArrayList<>());
                }
            }
        }

        HNSWGraph(int dimension, int m, int efConstruction) {
            this.dimension = dimension;
            this.m = m;
            this.efConstruction = efConstruction;
            this.nodes = new ArrayList<>();
            this.entryPoint = -1;
            this.maxLevel = 0;
            this.random = new java.util.Random();
        }

        void insert(float[] vector, int externalId) {
            int level = randomLevel();
            Node node = new Node(externalId, vector, level);
            int nodeId = nodes.size();
            nodes.add(node);

            if (entryPoint == -1) {
                entryPoint = nodeId;
                maxLevel = level;
                return;
            }

            int current = entryPoint;
            float[] query = vector;
            float currentDist = cosineDist(query, nodes.get(current).vector);

            for (int l = maxLevel; l > level; l--) {
                boolean changed = true;
                while (changed) {
                    changed = false;
                    List<Integer> neighbors = nodes.get(current).neighbors.get(l);
                    for (int neighbor : neighbors) {
                        if (neighbor >= nodes.size()) continue;
                        float dist = cosineDist(query, nodes.get(neighbor).vector);
                        if (dist < currentDist) {
                            current = neighbor;
                            currentDist = dist;
                            changed = true;
                        }
                    }
                }
            }

            for (int l = Math.min(level, maxLevel); l >= 0; l--) {
                List<Integer> candidates = searchLayer(query, current, efConstruction, l);
                List<Integer> neighbors = selectNeighbors(query, candidates, m);
                node.neighbors.get(l).addAll(neighbors);
                for (int neighbor : neighbors) {
                    if (neighbor < nodes.size()) {
                        Node neighborNode = nodes.get(neighbor);
                        List<Integer> neighborList = neighborNode.neighbors.get(l);
                        neighborList.add(nodeId);
                        if (neighborList.size() > m * 2) {
                            neighborList.sort((a, b) -> {
                                float da = cosineDist(neighborNode.vector, nodes.get(a).vector);
                                float db = cosineDist(neighborNode.vector, nodes.get(b).vector);
                                return Float.compare(da, db);
                            });
                            while (neighborList.size() > m * 2) {
                                neighborList.remove(neighborList.size() - 1);
                            }
                        }
                    }
                }
                if (!candidates.isEmpty()) {
                    current = candidates.get(0);
                }
            }

            if (level > maxLevel) {
                maxLevel = level;
                entryPoint = nodeId;
            }
        }

        List<Integer> search(float[] query, int topK) {
            if (entryPoint == -1 || nodes.isEmpty()) {
                return Collections.emptyList();
            }

            int current = entryPoint;
            float currentDist = cosineDist(query, nodes.get(current).vector);

            for (int l = maxLevel; l > 0; l--) {
                boolean changed = true;
                while (changed) {
                    changed = false;
                    List<Integer> neighbors = nodes.get(current).neighbors.get(l);
                    for (int neighbor : neighbors) {
                        if (neighbor >= nodes.size()) continue;
                        float dist = cosineDist(query, nodes.get(neighbor).vector);
                        if (dist < currentDist) {
                            current = neighbor;
                            currentDist = dist;
                            changed = true;
                        }
                    }
                }
            }

            List<Integer> candidates = searchLayer(query, current, Math.max(topK, HNSW_EF_SEARCH), 0);

            PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Float.compare(b[1], a[1]));
            for (int candidate : candidates) {
                if (candidate < nodes.size()) {
                    float dist = cosineDist(query, nodes.get(candidate).vector);
                    pq.add(new int[]{candidate, Float.floatToIntBits(dist)});
                    if (pq.size() > topK) {
                        pq.poll();
                    }
                }
            }

            List<Integer> results = new ArrayList<>();
            while (!pq.isEmpty()) {
                results.add(0, pq.poll()[0]);
            }
            return results;
        }

        private List<Integer> searchLayer(float[] query, int entry, int ef, int level) {
            Map<Integer, Float> distances = new HashMap<>();
            PriorityQueue<int[]> candidates = new PriorityQueue<>((a, b) -> Float.compare(a[1], b[1]));
            Map<Integer, Boolean> visited = new HashMap<>();

            float entryDist = cosineDist(query, nodes.get(entry).vector);
            candidates.add(new int[]{entry, Float.floatToIntBits(entryDist)});
            visited.put(entry, true);
            distances.put(entry, entryDist);

            PriorityQueue<int[]> results = new PriorityQueue<>((a, b) -> Float.compare(b[1], a[1]));
            results.add(new int[]{entry, Float.floatToIntBits(entryDist)});

            while (!candidates.isEmpty()) {
                int[] current = candidates.poll();
                int currentId = current[0];
                float currentDist = Float.intBitsToFloat(current[1]);

                float worstDist = Float.intBitsToFloat(results.peek()[1]);
                if (currentDist > worstDist && results.size() >= ef) {
                    break;
                }

                if (level < nodes.get(currentId).neighbors.size()) {
                    List<Integer> neighbors = nodes.get(currentId).neighbors.get(level);
                    for (int neighbor : neighbors) {
                        if (neighbor >= nodes.size() || visited.containsKey(neighbor)) continue;
                        visited.put(neighbor, true);

                        float dist = cosineDist(query, nodes.get(neighbor).vector);
                        float worstResultDist = results.size() < ef ? Float.MAX_VALUE :
                                Float.intBitsToFloat(results.peek()[1]);

                        if (dist < worstResultDist || results.size() < ef) {
                            candidates.add(new int[]{neighbor, Float.floatToIntBits(dist)});
                            results.add(new int[]{neighbor, Float.floatToIntBits(dist)});
                            if (results.size() > ef) {
                                results.poll();
                            }
                        }
                    }
                }
            }

            List<Integer> resultIds = new ArrayList<>();
            while (!results.isEmpty()) {
                resultIds.add(0, results.poll()[0]);
            }
            resultIds.sort((a, b) -> {
                float da = distances.getOrDefault(a, Float.MAX_VALUE);
                float db = distances.getOrDefault(b, Float.MAX_VALUE);
                return Float.compare(da, db);
            });
            return resultIds;
        }

        private List<Integer> selectNeighbors(float[] query, List<Integer> candidates, int m) {
            List<int[]> scored = new ArrayList<>();
            for (int c : candidates) {
                if (c < nodes.size()) {
                    float dist = cosineDist(query, nodes.get(c).vector);
                    scored.add(new int[]{c, Float.floatToIntBits(dist)});
                }
            }
            scored.sort((a, b) -> Float.compare(Float.intBitsToFloat(a[1]), Float.intBitsToFloat(b[1])));
            List<Integer> selected = new ArrayList<>();
            for (int i = 0; i < Math.min(m, scored.size()); i++) {
                selected.add(scored.get(i)[0]);
            }
            return selected;
        }

        private int randomLevel() {
            int level = 0;
            while (random.nextDouble() < 1.0 / m && level < HNSW_MAX_LEVEL) {
                level++;
            }
            return level;
        }

        private float cosineDist(float[] a, float[] b) {
            float normA = 0f, normB = 0f, dot = 0f;
            for (int i = 0; i < a.length; i++) {
                normA += a[i] * a[i];
                normB += b[i] * b[i];
                dot += a[i] * b[i];
            }
            normA = (float) Math.sqrt(normA);
            normB = (float) Math.sqrt(normB);
            if (normA == 0 || normB == 0) return 1f;
            return 1f - dot / (normA * normB);
        }
    }

    public static class SearchResult {
        public String content;
        public String source;
        public float score;

        public SearchResult(String content, String source, float score) {
            this.content = content;
            this.source = source;
            this.score = score;
        }
    }
}
