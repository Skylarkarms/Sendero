package sendero;

import sendero.atomics.AtomicUtils;
import sendero.switchers.Switchers;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.isIdentity;
import static sendero.functions.Functions.myIdentity;

public class Appointers {
    static class AppointerSwapCore<T> implements Switchers.Switch {
        /**Accessed via side effects, ignore warnings*/
        final AtomicReference<AtomicBinaryEventConsumer> witnessAtomicReference;
        final Holders.StreamManager<T> holder;

        AppointerSwapCore(
                Holders.StreamManager<T> holder
        ) {
            this(holder, Appointer.CLEARED_APPOINTER);
        }

        AppointerSwapCore(
                Holders.StreamManager<T> holder,
                Appointer<?> fixedAppointer) {
            this.witnessAtomicReference = new AtomicReference<>(fixedAppointer);
            this.holder = holder;
        }

        private AtomicBinaryEventConsumer get() {
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

    interface BasePathListener<T> extends Switchers.Switch {
        /**@return last holder value, shutsDown previous*/
        default <P extends BasePath<T>> void setAndStart(P basePath) {
            setAndStart(basePath, myIdentity());
        }
        /** */
        <S, P extends BasePath<S>> void setAndStart(P basePath, Function<S, T> map);
    }

    static class BasePathListenerImpl<T> implements BasePathListener<T> {

        final AppointerSwapCore<T> appointerSwapCore;

        BasePathListenerImpl(
                Holders.StreamManager<T> manager
        ) {
            appointerSwapCore = new AppointerSwapCore<>(manager);
        }

        @Override
        public <S, P extends BasePath<S>> void setAndStart(P basePath, Function<S, T> map) {
            assert basePath != null;
            boolean isIdentity = isIdentity(map);
            final AtomicReference<AtomicBinaryEventConsumer> witnessAtomicReference = appointerSwapCore.witnessAtomicReference;
            final AtomicUtils.Witness<AtomicBinaryEventConsumer> witness = AtomicUtils.contentiousCAS(
                    witnessAtomicReference,
                    prev -> prev == Appointer.CLEARED_APPOINTER || !prev.equalTo(basePath) || !isIdentity, // always update if map is NOT identity
                    prev -> Appointer.producerConnector(basePath, appointerSwapCore.holder, map)
            );
            final AtomicBinaryEventConsumer prev = witness.prev, next = witness.next;
            if (next != null && prev != next) {
                prev.shutDown();
                //contention check
                if (witnessAtomicReference.get().equalTo(basePath)) {
                    next.start();
                }
            }
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
    }

    interface UnboundPathListener<T> {
        /**@return previous Path OR null under contention*/
        <S, P extends BasePath<S>> AtomicBinaryEventConsumer setPathAndGet(P basePath, Function<S, T> map);
        default AtomicBinaryEventConsumer setPathAndGet(BasePath<T> basePath) {
            return setPathAndGet(basePath, myIdentity());
        }
        <S, P extends BasePath<S>> Appointer<S> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update);
        AtomicBinaryEventConsumer getAndClear();
    }

    static class UnboundPathListenerImpl<T> implements UnboundPathListener<T> {

        final AppointerSwapCore<T> appointerSwapCore;

        UnboundPathListenerImpl(AppointerSwapCore<T> appointerSwapCore) {
            this.appointerSwapCore = appointerSwapCore;
        }

        UnboundPathListenerImpl(
                Holders.StreamManager<T> coldHolder
        ) {
            this.appointerSwapCore = new AppointerSwapCore<>(coldHolder);
        }

        @Override
        public <S, P extends BasePath<S>> AtomicBinaryEventConsumer setPathAndGet(P basePath, Function<S, T> map) {
            return AtomicUtils.contentiousCAS(
                    appointerSwapCore.witnessAtomicReference,
                    prev -> !prev.equalTo(basePath),
                    new UnaryOperator<AtomicBinaryEventConsumer>() {
                        final AtomicBinaryEventConsumer next = Appointer.producerConnector(basePath,
                                appointerSwapCore.holder,
                                map);
                        @Override
                        public AtomicBinaryEventConsumer apply(AtomicBinaryEventConsumer appointer) {
                            return next;
                        }
                    }
            ).next;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <S, P extends BasePath<S>> Appointer<S> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update) {
            return (Appointer<S>) AtomicUtils.contentiousCAS(
                    appointerSwapCore.witnessAtomicReference,
                    prev -> !prev.equalTo(basePath),
                    prev -> Appointer.producerHolderConnector(basePath,
                            getStreamManager(),
                            update
                    )
            ).next;
        }

        @Override
        public AtomicBinaryEventConsumer getAndClear() {
            return appointerSwapCore.witnessAtomicReference.getAndSet(Appointer.CLEARED_APPOINTER);
        }

        Holders.StreamManager<T> getStreamManager() {
            return appointerSwapCore.holder;
        }
    }

    /**Used by Path, this one is for client usage, and can emulate the behaviour of Unbound Link classes*/
    public interface PathListener<T> extends BasePathListener<T>, UnboundPathListener<T> {
        void stopAndClearPath();

        boolean isActive();
        boolean isCleared();

        static<T> PathListener<T> getInstance(BasePath<T> basePath) {
            return new PathListenerImpl<>(basePath.streamManager);
       }
    }

    static class PathListenerImpl<T> extends BasePathListenerImpl<T> implements PathListener<T> {

        final UnboundPathListenerImpl<T> unboundPathListener;

        PathListenerImpl(Holders.StreamManager<T> manager) {
            super(manager);
            unboundPathListener = new UnboundPathListenerImpl<>(appointerSwapCore);
        }

        @Override
        public <S, P extends BasePath<S>> AtomicBinaryEventConsumer setPathAndGet(P basePath, Function<S, T> map) {
            return unboundPathListener.setPathAndGet(basePath, map);
        }

        @Override
        public <S, P extends BasePath<S>> Appointer<S> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update) {
            return unboundPathListener.setPathUpdateAndGet(basePath, update);
        }

        @Override
        public void stopAndClearPath() {
            appointerSwapCore.witnessAtomicReference.getAndSet(Appointer.CLEARED_APPOINTER).shutDown();
        }

        @Override
        public boolean isActive() {
            return appointerSwapCore.isActive();
        }

        @Override
        public boolean isCleared() {
            return appointerSwapCore.get().isCleared();
        }

        /** If next == null, prev was already cleared
         * @return next appointer*/
        @Override
        public AtomicBinaryEventConsumer getAndClear() {
            return unboundPathListener.getAndClear();
        }
    }
}
