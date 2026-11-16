package morfeov.coliseum.movement;

/** Contact from the previous move, reset before each native movement/collision pass. */
public interface WebContact {
    boolean coliseum$isInWeb();
    void coliseum$setInWeb(boolean value);
}
