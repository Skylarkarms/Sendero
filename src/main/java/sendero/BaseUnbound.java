package sendero;

import java.util.function.Function;

public class BaseUnbound<T> implements BaseLink, InputMethodBinder<T> {

    final ActivePathListener<T> activePathListener;

    BaseUnbound(Holders.ActivationHolder<T> activationHolder) {
        this(activationHolder.activationManager, activationHolder.getManager());
    }

    BaseUnbound(ActivationManager activationManager, Holders.StreamManager<T> streamManager) {
        final Appointers.ConcurrentProducerSwapper<T> pathListener = new Appointers.ConcurrentProducerSwapper<>(streamManager);
        activePathListener = new ActivePathListener<>(activationManager, pathListener);
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

    public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
        activePathListener.forcedSet(
                AtomicBinaryEventConsumer.switchMapEventConsumer(
                        activePathListener.getStreamManager(),
                        path,
                        switchMap
                )
        );
    }

    @Override
    public <S, P extends BasePath<S>> Void bind(P basePath, Builders.InputMethods<T, S> inputMethod) {
        return activePathListener.bind(basePath, inputMethod);
    }
}
