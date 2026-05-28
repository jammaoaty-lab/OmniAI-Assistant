package com.omniai.assistant.multimodal;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;

import java.util.ArrayList;
import java.util.Locale;

public class SpeechRecognizer {

    private android.speech.SpeechRecognizer recognizer;
    private boolean isListening;
    private VoiceCallback callback;
    private Handler handler;
    private Context context;
    private String language;

    public SpeechRecognizer(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        this.isListening = false;
        this.language = Locale.getDefault().toString();
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
            this.recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context);
            this.recognizer.setRecognitionListener(new SpeechRecognitionListener());
        }
    }

    public void startListening() {
        if (recognizer == null) {
            if (callback != null) {
                callback.onError("Speech recognition not available");
            }
            return;
        }
        if (isListening) {
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        isListening = true;
        recognizer.startListening(intent);
    }

    public void stopListening() {
        if (recognizer != null && isListening) {
            isListening = false;
            recognizer.stopListening();
        }
    }

    public boolean isListening() {
        return isListening;
    }

    public void setLanguage(String locale) {
        this.language = locale;
    }

    public void setCallback(VoiceCallback callback) {
        this.callback = callback;
    }

    public void destroy() {
        if (recognizer != null) {
            isListening = false;
            recognizer.destroy();
            recognizer = null;
        }
    }

    private class SpeechRecognitionListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            isListening = false;
        }

        @Override
        public void onError(int error) {
            isListening = false;
            if (callback != null) {
                String errorMessage = getErrorMessage(error);
                handler.post(() -> callback.onError(errorMessage));
            }
        }

        @Override
        public void onResults(Bundle results) {
            isListening = false;
            ArrayList<String> matches = results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty() && callback != null) {
                String text = matches.get(0);
                handler.post(() -> callback.onResult(text));
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty() && callback != null) {
                String text = matches.get(0);
                handler.post(() -> callback.onPartial(text));
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }

        private String getErrorMessage(int error) {
            switch (error) {
                case android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    return "Network timeout";
                case android.speech.SpeechRecognizer.ERROR_NETWORK:
                    return "Network error";
                case android.speech.SpeechRecognizer.ERROR_AUDIO:
                    return "Audio error";
                case android.speech.SpeechRecognizer.ERROR_SERVER:
                    return "Server error";
                case android.speech.SpeechRecognizer.ERROR_CLIENT:
                    return "Client error";
                case android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    return "No speech detected";
                case android.speech.SpeechRecognizer.ERROR_NO_MATCH:
                    return "No match found";
                case android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    return "Recognizer busy";
                case android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    return "Insufficient permissions";
                default:
                    return "Unknown error";
            }
        }
    }

    public interface VoiceCallback {
        void onResult(String text);
        void onPartial(String text);
        void onError(String error);
    }
}
