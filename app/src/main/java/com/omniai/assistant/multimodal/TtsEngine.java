package com.omniai.assistant.multimodal;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TtsEngine {

    private TextToSpeech tts;
    private boolean isReady;
    private String currentVoice;
    private float speechRate;
    private float pitch;
    private Context context;

    public TtsEngine() {
        this.isReady = false;
        this.speechRate = 1.0f;
        this.pitch = 1.0f;
        this.currentVoice = null;
    }

    public void initialize(Context context) {
        this.context = context.getApplicationContext();
        this.tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                isReady = true;
            } else {
                isReady = false;
            }
        });
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        isReady = false;
    }

    public void speak(String text, String voiceId, UtteranceCallback callback) {
        if (tts == null || !isReady) {
            if (callback != null) {
                callback.onError("TTS engine not ready");
            }
            return;
        }

        if (voiceId != null) {
            Set<android.speech.tts.Voice> voices = tts.getVoices();
            if (voices != null) {
                for (android.speech.tts.Voice voice : voices) {
                    if (voice.getName().equals(voiceId)) {
                        tts.setVoice(voice);
                        currentVoice = voiceId;
                        break;
                    }
                }
            }
        }

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                if (callback != null) {
                    callback.onStart();
                }
            }

            @Override
            public void onDone(String utteranceId) {
                if (callback != null) {
                    callback.onComplete();
                }
            }

            @Override
            public void onError(String utteranceId) {
                if (callback != null) {
                    callback.onError("TTS playback error");
                }
            }
        });

        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "omni_tts_utterance");
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void setSpeechRate(float rate) {
        this.speechRate = Math.max(0.5f, Math.min(2.0f, rate));
        if (tts != null) {
            tts.setSpeechRate(this.speechRate);
        }
    }

    public void setPitch(float pitch) {
        this.pitch = Math.max(0.5f, Math.min(2.0f, pitch));
        if (tts != null) {
            tts.setPitch(this.pitch);
        }
    }

    public List<String> getAvailableVoices() {
        List<String> voiceNames = new ArrayList<>();
        if (tts == null || !isReady) {
            return voiceNames;
        }
        Set<android.speech.tts.Voice> voices = tts.getVoices();
        if (voices != null) {
            for (android.speech.tts.Voice voice : voices) {
                voiceNames.add(voice.getName());
            }
        }
        return voiceNames;
    }

    public boolean isReady() {
        return isReady;
    }

    public interface UtteranceCallback {
        void onStart();
        void onComplete();
        void onError(String error);
    }
}
