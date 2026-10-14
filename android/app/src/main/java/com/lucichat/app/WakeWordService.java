package com.lucichat.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

public class WakeWordService extends Service {
    private static final String CHANNEL_ID = "aura_wake_word";
    private static final int NOTIFICATION_ID = 7001;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean active = false;
    private boolean launching = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        active = true;
        startForeground(NOTIFICATION_ID, buildNotification());
        startRecognition();
        return START_STICKY;
    }

    private void startRecognition() {
        if (!active || launching) return;
        try {
            if (recognizer != null) {
                recognizer.destroy();
                recognizer = null;
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { scheduleRestart(500); }
                @Override public void onError(int error) { scheduleRestart(900); }
                @Override public void onResults(Bundle results) { inspect(results); scheduleRestart(350); }
                @Override public void onPartialResults(Bundle results) { inspect(results); }
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            speech.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            speech.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            recognizer.startListening(speech);
        } catch (Exception e) {
            scheduleRestart(1500);
        }
    }

    private void inspect(Bundle bundle) {
        if (bundle == null || launching) return;
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null) return;
        for (String phrase : matches) {
            String normalized = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT);
            if (normalized.matches(".*\\b(hey\\s+)?aura\\b.*")) {
                launching = true;
                active = false;
                if (recognizer != null) {
                    recognizer.cancel();
                    recognizer.destroy();
                    recognizer = null;
                }
                Intent open = new Intent(this, MainActivity.class);
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                open.putExtra("aura_wake_word", true);
                android.app.KeyguardManager keyguard =
                    (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (keyguard != null && keyguard.isKeyguardLocked()) {
                    PendingIntent openPending = PendingIntent.getActivity(
                        this, 7003, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? new Notification.Builder(this, CHANNEL_ID)
                        : new Notification.Builder(this);
                    Notification heard = builder.setContentTitle("Aura heard you")
                        .setContentText("Tap to open Aura.")
                        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                        .setContentIntent(openPending)
                        .setAutoCancel(true)
                        .build();
                    NotificationManager manager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (manager != null) manager.notify(NOTIFICATION_ID + 1, heard);
                } else {
                    startActivity(open);
                }
                stopSelf();
                return;
            }
        }
    }

    private void scheduleRestart(long delay) {
        if (active && !launching) handler.postDelayed(this::startRecognition, delay);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Aura voice activation", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Visible notification while Aura's wake-word listener is active.");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, WakeWordService.class);
        stop.setAction("STOP");
        PendingIntent stopPending = PendingIntent.getService(
            this, 7002, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        return builder.setContentTitle("Aura is listening")
            .setContentText("Say “Hey Aura” to open Aura. Tap Stop to turn listening off.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening", stopPending)
            .build();
    }

    @Override public void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}