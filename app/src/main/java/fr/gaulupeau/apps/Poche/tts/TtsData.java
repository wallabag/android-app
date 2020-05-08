package fr.gaulupeau.apps.Poche.tts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TtsData {

    // some arbitrary number (not more than 16 bits for
    // {@link androidx.fragment.app.Fragment#startActivityForResult(Intent, int)} compatibility)
    private static final int REQUEST_CODE_MARKER = 0xff00;
    private static final int REQUEST_CODE_MARKER_MASK = 0xff00;

    public interface ActivityForResultStarter {
        void startActivityForResult(Intent intent, int requestCode);
    }

    public static class DisplayName implements Comparable<DisplayName> {
        String name;
        String displayName;

        DisplayName() {}

        DisplayName(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }

        @Override
        public int compareTo(@NonNull DisplayName another) {
            return this.displayName.compareTo(another.displayName);
        }
    }

    public static class VoiceInfo extends DisplayName {
        String language;
        String countryDetails;
        DisplayName engineInfo;
    }

    private static final String TAG = TtsData.class.getSimpleName();

    private int numberOfEngines;
    private List<DisplayName> engines;

    private final Map<String, List<VoiceInfo>> voicesByLanguage = new HashMap<>();
    private List<DisplayName> languages = new ArrayList<>();

    private int numberOfInitializedEngines;

    private boolean initialized;

    public boolean isInitialized() {
        return initialized;
    }

    public List<DisplayName> getLanguages() {
        return languages;
    }

    public List<VoiceInfo> getVoicesByLanguage(String language) {
        return voicesByLanguage.get(language);
    }

    public void init(Context context, ActivityForResultStarter activityForResultStarter) {
        if (engines != null) return;
        Log.d(TAG, "init()");

        PackageManager pm = context.getPackageManager();

        Intent ttsIntent = new Intent();
        ttsIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);

        List<ResolveInfo> resolveInfoList = pm
                .queryIntentActivities(ttsIntent, PackageManager.GET_META_DATA);

        numberOfEngines = resolveInfoList.size();

        engines = new ArrayList<>(numberOfEngines);

        for (ResolveInfo resolveInfo : resolveInfoList) {
            DisplayName engineInfo = new DisplayName(
                    resolveInfo.activityInfo.applicationInfo.packageName,
                    resolveInfo.loadLabel(pm).toString());

            Log.d(TAG, "init() " + engineInfo.name + ": " + engineInfo.displayName);

            engines.add(engineInfo);

            Intent getVoicesIntent = new Intent();
            getVoicesIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            getVoicesIntent.setPackage(engineInfo.name);
            getVoicesIntent.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);

            activityForResultStarter.startActivityForResult(
                    getVoicesIntent, maskRequestCode(engines.size() - 1));
        }
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (engines == null) return false;
        requestCode = unmaskRequestCode(requestCode);
        Log.d(TAG, "onActivityResult() requestCode: "
                + requestCode + ", resultCode: " + resultCode);

        if (requestCode >= 0 && requestCode < engines.size()) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS
                    && data != null && data.hasExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES)) {
                List<String> availableVoices = data.getStringArrayListExtra(
                        TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);

                DisplayName engineInfo = engines.get(requestCode);

                Log.d(TAG, "onActivityResult() " + engineInfo.displayName
                        + " voices = " + availableVoices.toString());

                for (String voiceName : availableVoices) {
                    VoiceInfo voiceInfo = new VoiceInfo();
                    voiceInfo.name = voiceName;
                    voiceInfo.engineInfo = engineInfo;
                    int separatorIndex = voiceName.indexOf("-");
                    if (separatorIndex >= 0) {
                        voiceInfo.language = voiceName.substring(0, separatorIndex);
                        voiceInfo.countryDetails = voiceName.substring(separatorIndex + 1);
                        voiceInfo.displayName = engineInfo.displayName
                                + ":" + voiceInfo.countryDetails;
                    } else {
                        voiceInfo.language = voiceName;
                        voiceInfo.countryDetails = "";
                        voiceInfo.displayName = engineInfo.displayName;
                    }

                    List<VoiceInfo> voices = voicesByLanguage.get(voiceInfo.language);
                    if (voices == null) {
                        voicesByLanguage.put(voiceInfo.language, voices = new ArrayList<>());
                    }
                    voices.add(voiceInfo);
                }
            }

            numberOfInitializedEngines++;
            if (numberOfInitializedEngines == numberOfEngines) {
                // Sort languages by displayName:
                languages = new ArrayList<>(voicesByLanguage.size());
                for (String language : voicesByLanguage.keySet()) {
                    languages.add(new DisplayName(language,
                            new Locale(language).getDisplayLanguage()));
                }
                Collections.sort(languages);

                // sort voice lists by displayName:
                for (List<VoiceInfo> voices : voicesByLanguage.values()) {
                    Collections.sort(voices);
                }

                initialized = true;
                Log.d(TAG, "onActivityResult() initialization finished");
            }
        }

        return initialized;
    }

    private int maskRequestCode(int code) {
        if (code < 0 || (code & REQUEST_CODE_MARKER_MASK) != 0) {
            throw new IllegalArgumentException("code is out of range");
        }

        return code | REQUEST_CODE_MARKER;
    }

    private int unmaskRequestCode(int code) {
        if ((code & REQUEST_CODE_MARKER_MASK) != REQUEST_CODE_MARKER) {
            return -1;
        }

        return code ^ REQUEST_CODE_MARKER;
    }

}
