package sendero;

import java.util.function.BiFunction;
import java.util.function.Function;

public class BaseUnbound<T> implements UnboundLink<T>, BaseLink {

    final ActivePathListener<T> activePathListener;

    BaseUnbound(BasePath<T> basePath) {
        final Appointers.UnboundPathListenerImpl<T> pathListener = new Appointers.UnboundPathListenerImpl<>(basePath.baseTestDispatcher);
        activePathListener = new ActivePathListener<>(basePath.manager, pathListener);

    }

    @Override
    public boolean isBound() {
        try {
            throw new IllegalAccessException("Should use basePath's isBound instead!");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean unbound() {
        return activePathListener.unbound();
    }

    @Override
    public <S, P extends BasePath<S>> void bindMap(P basePath, Function<S, T> map) {
        activePathListener.bindMap(basePath, map);
    }

    @Override
    public <S, P extends BasePath<S>> void bindUpdate(P basePath, BiFunction<T, S, T> update) {
        activePathListener.bindUpdate(basePath, update);
    }

    @Override
    public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
        activePathListener.forcedSet(
                AtomicBinaryEventConsumer.switchMapEventConsumer(
                        activePathListener.getColdHolder(),
//                        getColdHolder(),
                        path,
                        switchMap
                )
        );

    }
}
