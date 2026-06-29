package net.fabricmc.fabric.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class EventFactory {
    
    private EventFactory() {}

    public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> invokerFactory) {
        return new Event<T>() {
            private final List<T> listeners = new ArrayList<>();
            private T invoker;

            @Override
            public void register(T listener) {
                listeners.add(listener);
                update();
            }

            @Override
            public T invoker() {
                if (invoker == null) update();
                return invoker;
            }

            @SuppressWarnings("unchecked")
            private void update() {
                T[] array = (T[]) java.lang.reflect.Array.newInstance(type, listeners.size());
                invoker = invokerFactory.apply(listeners.toArray(array));
            }
        };
    }
}
