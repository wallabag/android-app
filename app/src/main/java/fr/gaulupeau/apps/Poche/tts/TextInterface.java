package fr.gaulupeau.apps.Poche.tts;

/**
 * Text To Speech (TTS) Text Interface.
 */
public interface TextInterface {
    void    restoreFromStart();
    void    storeCurrent();
    void    restoreCurrent();
    int getCurrentIndex();
    GenericItem getItem(int index);
    boolean next();
    boolean rewind(long desiredTimeToRewind, int currentIndex, long progressInCurrentItem);
    boolean fastForward(long desiredTimeToSkip, int currentIndex, long progressInCurrentItem);
    boolean skipToNext();
    boolean skipToPrevious();
    long   getTime();
    long   getTotalDuration();
}
