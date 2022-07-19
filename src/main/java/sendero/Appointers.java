package sendero;

import sendero.atomics.AtomicUtils;
import sendero.switchers.Switchers;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static sendero.Appointers.ConcurrentProducerSwapper.bindStart;

public class Appointers {
    static class AppointerSwapCore<T> extends AtomicReference<AtomicBinaryEvent> implements Switchers.Switch {
        AppointerSwapCore(
        ) {
            super(AtomicBinaryEvent.DEFAULT);
        }

        @Override
        public boolean on() {
            return get().on();
        }

        @Override
        public boolean isActive() {
            return get().isActive();
        }

        @Override
        public boolean off() {
            return get().off();
        }

        @Override
        public String toString() {
            return "AppointerSwapCore{" +
                    "\n swappableProducer=" + get() +
                    '}';
        }
    }

    static class ConcurrentProducerSwapper<T> implements Switchers.Switch, UnboundPathListener<T> {

        final AppointerSwapCore<T> appointerSwapCore;
        final Holders.StreamManager<T> streamManager;


        Holders.StreamManager<T> getStreamManager() {
            return streamManager;
        }


        ConcurrentProducerSwapper(
                Holders.StreamManager<T> recipient
        ) {
            appointerSwapCore = new AppointerSwapCore<>();
            streamManager = recipient;
        }

        @Override
        public boolean on() {
            return appointerSwapCore.on();
        }

        @Override
        public boolean off() {
            return appointerSwapCore.off();
        }

        @Override
        public boolean isActive() {
            return appointerSwapCore.isActive();
        }

        static<T, S, P extends BasePath<S>> boolean bindStart(InputMethodSwapper<T, AtomicBinaryEvent> swapper, P basePath, Builders.InputMethods<T, S> inputMethod) {
            AtomicBinaryEvent event = swapper.bind(basePath, inputMethod);
            return event != null && event.start();
        }

        @Override
        public <S, P extends BasePath<S>> AtomicBinaryEvent bind(P basePath, Builders.InputMethods<T, S> inputMethod) {
            final AtomicUtils.Witness<AtomicBinaryEvent> witness = AtomicUtils.setIf(
                    appointerSwapCore,
                    prev -> prev == AtomicBinaryEvent.DEFAULT || !((Appointer<?>)prev).equalTo(basePath, inputMethod.type),
                    appointer -> Builders.BinaryEventConsumers.producerListener(
                            basePath,
                            streamManager,
                            inputMethod.type)
            );
            final AtomicBinaryEvent next = witness.next;
            if (next != null) witness.prev.shutoff();
            return next;
        }

        public AtomicBinaryEvent getAndClear() {
            return appointerSwapCore.getAndSet(AtomicBinaryEvent.DEFAULT);
        }

        @Override
        public String toString() {
            return "ConcurrentProducerSwapper{" +
                    "\n appointerSwapCore=" + appointerSwapCore +
                    ",\n streamManager= " + streamManager +
                    '}';
        }
    }

    /**We could exchange 2 methods for a single one with Builders.InputMethod*/
    interface UnboundPathListener<T> extends InputMethodSwapper<T, AtomicBinaryEvent> {
        default <P extends BasePath<T>> boolean setAndStart(P basePath) {
            return bindStart(this, basePath, Builders.InputMethods.identity());
        }
        default <S, P extends BasePath<S>> boolean setAndStart(P basePath, Function<S, T> map) {
            return bindStart(this, basePath, Builders.InputMethods.map(map));
        }
        default <S, P extends BasePath<S>> boolean setAndStart(P basePath, BiFunction<T, S, T> update) {
            return bindStart(this, basePath, Builders.InputMethods.update(update));
        }
    }

    /**Used by Path, this one is for client usage, and can emulate the behaviour of Unbound Link classes*/
    public interface PathListener<T> extends UnboundPathListener<T>, Switchers.Switch {
        void stopAndClearPath();
        boolean isCleared();

        static<T> PathListener<T> getInstance(BasePath<T> consumer) {
            return new PathListenerImpl<>(consumer.getManager());
       }
    }

    static class PathListenerImpl<T> extends ConcurrentProducerSwapper<T> implements PathListener<T> {

        PathListenerImpl(Holders.StreamManager<T> manager) {
            super(manager);
        }

        @Override
        public void stopAndClearPath() {
            appointerSwapCore./*swappableAppointer.*/getAndSet(AtomicBinaryEvent.DEFAULT).shutoff();
        }

        @Override
        public boolean isActive() {
            return appointerSwapCore.isActive();
        }

        @Override
        public boolean isCleared() {
            return appointerSwapCore.get().isDefault();
        }
    }
}
