package im.yaHustler.render.coliseimvisual;

public interface VisualEventListener<T> {
    void onEvent(T event);

    default int priority() {
        return 0;
    }
}
