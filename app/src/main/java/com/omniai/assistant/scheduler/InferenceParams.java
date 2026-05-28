package com.omniai.assistant.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InferenceParams {

    private int nPredict;
    private float temperature;
    private float topP;
    private int topK;
    private float repeatPenalty;
    private int nCtx;
    private int nThreads;
    private boolean useMmap;
    private boolean useGpu;
    private String systemPrompt;
    private List<String> stopTokens;

    private InferenceParams() {
        this.nPredict = 256;
        this.temperature = 0.7f;
        this.topP = 0.9f;
        this.topK = 40;
        this.repeatPenalty = 1.1f;
        this.nCtx = 2048;
        this.nThreads = Runtime.getRuntime().availableProcessors();
        this.useMmap = true;
        this.useGpu = false;
        this.systemPrompt = "";
        this.stopTokens = new ArrayList<>(Arrays.asList("</s>", "<|end|>", "<|endoftext|>"));
    }

    public int getNPredict() {
        return nPredict;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getTopP() {
        return topP;
    }

    public int getTopK() {
        return topK;
    }

    public float getRepeatPenalty() {
        return repeatPenalty;
    }

    public int getNCtx() {
        return nCtx;
    }

    public int getNThreads() {
        return nThreads;
    }

    public boolean isUseMmap() {
        return useMmap;
    }

    public boolean isUseGpu() {
        return useGpu;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<String> getStopTokens() {
        return Collections.unmodifiableList(stopTokens);
    }

    public static class Builder {
        private final InferenceParams params;

        public Builder() {
            this.params = new InferenceParams();
        }

        public Builder nPredict(int nPredict) {
            this.params.nPredict = nPredict;
            return this;
        }

        public Builder temperature(float temperature) {
            this.params.temperature = temperature;
            return this;
        }

        public Builder topP(float topP) {
            this.params.topP = topP;
            return this;
        }

        public Builder topK(int topK) {
            this.params.topK = topK;
            return this;
        }

        public Builder repeatPenalty(float repeatPenalty) {
            this.params.repeatPenalty = repeatPenalty;
            return this;
        }

        public Builder nCtx(int nCtx) {
            this.params.nCtx = nCtx;
            return this;
        }

        public Builder nThreads(int nThreads) {
            this.params.nThreads = nThreads;
            return this;
        }

        public Builder useMmap(boolean useMmap) {
            this.params.useMmap = useMmap;
            return this;
        }

        public Builder useGpu(boolean useGpu) {
            this.params.useGpu = useGpu;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.params.systemPrompt = systemPrompt;
            return this;
        }

        public Builder stopTokens(List<String> stopTokens) {
            this.params.stopTokens = new ArrayList<>(stopTokens);
            return this;
        }

        public Builder addStopToken(String stopToken) {
            this.params.stopTokens.add(stopToken);
            return this;
        }

        public InferenceParams build() {
            return this.params;
        }
    }
}
