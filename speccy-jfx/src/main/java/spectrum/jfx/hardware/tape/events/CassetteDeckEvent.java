package spectrum.jfx.hardware.tape.events;

import spectrum.jfx.model.TapeFile;

/**
 * Listener interface for cassette deck events.
 */
public interface CassetteDeckEvent {

    /**
     * Called when tape is changed.
     */
    void onTapeChanged(TapeFile tape);

    /**
     * Called when current section changes during playback.
     */
    void onTapeSectionChanged(int sectionIndex, TapeFile file);

    /**
     * Called when tape position changes.
     */
    void onTapePositionChanged(long position);

    /**
     * Called when motor state changes.
     */
    void onTapeMotorChanged(boolean on);

    /**
     * Called when tape playback finishes.
     */
    void onTapeFinished(boolean success);

    /**
     * Called when tape error occurs.
     */
    void onTapeError(String message);
}
