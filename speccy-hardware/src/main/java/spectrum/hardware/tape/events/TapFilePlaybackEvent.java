package spectrum.hardware.tape.events;

import spectrum.hardware.tape.model.TapeFile;

/**
 * Listener interface for TAP file playback events.
 */
public interface TapFilePlaybackEvent {

    /**
     * Called when current section changes during playback.
     *
     * @param index Section index
     * @param tape  Tape file being played
     */
    void onSectionChanged(int index, TapeFile tape);

    /**
     * Called when tape playback finishes.
     *
     * @param success true if playback completed successfully
     */
    void onTapeFinished(boolean success);
}
