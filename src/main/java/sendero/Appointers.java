package sendero;

import sendero.atomics.AtomicUtils;
import sendero.switchers.Switchers;

import java.util.concurrent.atomic.AtomicReference;

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

    static class BasePathListenerImpl<T> implements Switchers.Switch, UnboundPathListener<T> {

        final AppointerSwapCore<T> appointerSwapCore;

        Holders.StreamManager<T> getStreamManager() {
            return appointerSwapCore.holder;
        }


        BasePathListenerImpl(
                Holders.StreamManager<T> manager
        ) {
            appointerSwapCore = new AppointerSwapCore<>(manager);
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

        @Override
        public <S, P extends BasePath<S>> AtomicBinaryEvent bind(P basePath, Builders.InputMethods<T, S> inputMethod) {
            final AtomicUtils.Witness<AtomicBinaryEvent> witness = AtomicUtils.contentiousCAS(
                    appointerSwapCore.witnessAtomicReference,
                    prev -> !prev.equalTo(basePath, inputMethod),
                    appointer -> Builders.BinaryEventConsumers.producerConnector(
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
        default <S, P extends BasePath<S>> void setAndStart(P basePath, Builders.InputMethods<T, S> inputMethod) {
            AtomicBinaryEvent next = bind(basePath, inputMethod);
            if (next != null) next.start();
        }
        default <P extends BasePath<T>> void setAndStart(P basePath) {
            AtomicBinaryEvent next = bind(basePath, Builders.InputMethods.identity());
            if (next != null) next.start();
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

    static class PathListenerImpl<T> extends BasePathListenerImpl<T> implements PathListener<T> {

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
