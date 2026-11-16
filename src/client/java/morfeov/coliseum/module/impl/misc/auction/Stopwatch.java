package morfeov.coliseum.module.impl.misc.auction;
public final class Stopwatch {
    private long started=System.nanoTime();
    public void reset(){started=System.nanoTime();}
    public boolean hasExceededMillis(long millis){return System.nanoTime()-started>=millis*1_000_000L;}
}
