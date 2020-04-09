package fr.gaulupeau.apps.Poche.tts;

/**
 * Text To Speech (TTS) Text Interface.
 */
public interface TextInterface {
    void    restoreFromStart();
    void    restoreCurrent();
    GenericItem getItem(int relativeIndex);
    boolean next();
    boolean fastForward();
    boolean rewind();
    boolean skipToNext();
    boolean skipToPrevious();
    long   getTime();
    long   getTotalDuration();
}
