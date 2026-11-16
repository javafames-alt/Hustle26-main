package morfeov.coliseum.module.impl.movement;

/** the original implementation's alternating timer and two-tick recovery after a server position correction. */
public final class GrimGlideFlightState {
    private int correctionTicks;
    private boolean skipNextMovementPacket;
    private int timerPhase;
    private float timerMultiplier = 1.0f;

    // Keep packet observation and tick updates atomic if another renderer/network integration queries this state.
    public synchronized void reset() {
        correctionTicks = 0;
        skipNextMovementPacket = false;
        timerPhase = 0;
        timerMultiplier = 1.0f;
    }

    public synchronized void beforeTick(boolean gliding, float boost, float slowdown) {
        if (correctionTicks > 0) correctionTicks--;
        if (gliding) {
            timerMultiplier = timerPhase % 2 == 0 ? boost : slowdown;
            timerPhase++;
        } else {
            timerMultiplier = 1.0f;
        }
    }

    public synchronized void positionCorrected() {
        correctionTicks = 2;
        skipNextMovementPacket = true;
        if (timerPhase % 2 == 1) timerPhase++;
    }

    /** The first outgoing movement after a correction must carry the actual position. */
    public synchronized boolean replaceMovementPacket(boolean gliding) {
        boolean replace = gliding && correctionTicks == 0 && !skipNextMovementPacket;
        skipNextMovementPacket = false;
        return replace;
    }

    public synchronized boolean recoveringFromCorrection() { return correctionTicks > 0; }
    public synchronized float timerMultiplier() { return timerMultiplier; }
}
