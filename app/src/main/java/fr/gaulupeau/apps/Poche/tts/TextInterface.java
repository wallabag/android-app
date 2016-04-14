package fr.gaulupeau.apps.Poche.tts;

/**
 * Text To Speech (TTS) Text Interface.
 */
public interface TextInterface {
    void    restoreCurrent();
    String  getText(int relativeIndex);
    boolean next();
    boolean fastForward();
    boolean rewind();
    float   getTime();
    float   getTotalDuration();
}
