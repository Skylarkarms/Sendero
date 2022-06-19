package sendero;

import sendero.atomics.AtomicUtils;
import sendero.switchers.Switchers;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static sendero.Appointers.ConcurrentProducerSwapper.bindStart;

public class Appointers {
    static class AppointerSwapCore<T> implements Switchers.Switch {
        /**Accessed via side effects, ignore warnings*/
        final AtomicReference<AtomicBinaryEvent> witnessAtomicReference;
        final Holders.StreamManager<T> holder;

        AppointerSwapCore(
                Holders.StreamManager<T> holder
        ) {
            this(holder, AtomicBinaryEvent.DEFAULT);
        }

        AppointerSwapCore(
                Holders.StreamManager<T> holder,
                AtomicBinaryEvent fixedAppointer) {
            this.witnessAtomicReference = new AtomicReference<>(fixedAppointer);
            this.holder = holder;
        }

        private AtomicBinaryEvent get() {
            return witnessAtomicReference.get();
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

    }

    static class ConcurrentProducerSwapper<T> implements Switchers.Switch, UnboundPathListener<T> {

        final AppointerSwapCore<T> appointerSwapCore;

        Holders.StreamManager<T> getStreamManager() {
            return appointerSwapCore.holder;
        }


        ConcurrentProducerSwapper(
                Holders.StreamManager<T> recipient
        ) {
            appointerSwapCore = new AppointerSwapCore<>(recipient);
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
            final AtomicUtils.Witness<AtomicBinaryEvent> witness = AtomicUtils.contentiousCAS(
                    appointerSwapCore.witnessAtomicReference,
                    prev -> prev == AtomicBinaryEvent.DEFAULT || !((Appointer<?>)prev).equalTo(basePath, inputMethod.type),
                    appointer -> Builders.BinaryEventConsumers.producerListener(
                            basePath,
                            appointerSwapCore.holder,
                            inputMethod.type)
            );
            final AtomicBinaryEvent next = witness.next;
            if (next != null) witness.prev.shutoff();
            return next;
        }

        public AtomicBinaryEvent getAndClear() {
            return appointerSwapCore.witnessAtomicReference.getAndSet(AtomicBinaryEvent.DEFAULT);
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

        static<T> PathListener<T> getInstance(BasePath<T> basePath) {
            return new PathListenerImpl<>(basePath.streamManager);
       }
    }

    static class PathListenerImpl<T> extends ConcurrentProducerSwapper<T> implements PathListener<T> {

        PathListenerImpl(Holders.StreamManager<T> manager) {
            super(manager);
        }

        @Override
        public void stopAndClearPath() {
            appointerSwapCore.witnessAtomicReference.getAndSet(Appointer.CLEARED_APPOINTER).shutoff();
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
