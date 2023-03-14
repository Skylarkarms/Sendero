package sendero.event_registers;

import sendero.AtomicBinaryEvent;
import sendero.AtomicBinaryEventConsumer;
import sendero.BasePath;
import sendero.Builders;
import sendero.interfaces.Register;
import sendero.switchers.Switchers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class BinaryEventRegisters {
    public static BaseStateRegister getAtomicRegister() {
        return new BinaryStateRegister();
    }
    public static Switchers.Switch getAtomicWith(AtomicBinaryEvent fixed) {
        return new AtomicWithFixed(fixed);
    }
    public interface BinaryEventRegister {
        void register(AtomicBinaryEvent booleanConsumer);
        AtomicBinaryEvent unregister();
        boolean isRegistered();
        interface Atomic extends BinaryEventRegister {
            /**@return prev, which is the same as expect if successful*/
            AtomicBinaryEvent swapRegister(AtomicBinaryEvent expect, AtomicBinaryEvent set);
        }
    }

    /**Keeps the BasePath's state alive while swapping event listeners
     * Lighter and faster version than that of AtomicBinaryEventConsumer
     * */
    static abstract class BaseEvent implements Switchers.Switch {
        final Switchers.Switch mainState = Switchers.getAtomic();

        abstract void onStateChanged(boolean isActive);

        @Override
        public boolean on() {
            boolean on = mainState.on();
            if (on) onStateChanged(true);
            return on;
        }

        @Override
        public boolean off() {
            boolean off = mainState.off();
            if (off) onStateChanged(false);
            return off;
        }

        @Override
        public boolean isActive() {
            return mainState.isActive();
        }

        @Override
        public String toString() {
            return "BaseEvent{" +
                    "\n mainState=" + mainState +
                    "\n}";
        }
    }

    /**The link between Consumer and boolean state is loosely related.
     * Why aren't both states not strictly related and under the umbrella of a single Atomic value?
     * By having a loose relation we allow that:
     *      Boolean values can receive changes even without a Consumer attached.
     *      Boolean values can change while the Consumer is in a heavy contention process of change.
     *      Consumer can perform registrations freely without being tied to the on() or off() atomic pipeline. (the ifAccept() short-circuits if false)
     *
     * If both were heavily related, contention between boolean changes and consumer changes both with heavy traffic would be required to access a single atomic pipeline.
     * By using a loose relation between both, the only requirement is a volatile read of the current boolean state at the moment of new registration, with minor drawbacks explained
     *
     * This class can handle ONE register at a time.
     * For multiple registers see Appointer.ConcurrentToMany.
     * For multiple NON-concurrent see bellow class*/
    public static abstract class BaseStateRegister extends BaseEvent implements BinaryEventRegister.Atomic {
        abstract Switchers.Switch getSwitch();

        @Override
        void onStateChanged(boolean isActive) {
            if (isActive) getSwitch().on();
            else getSwitch().off();
        }
    }
    private static class BinaryStateRegister
            extends BaseStateRegister {
        private final ConsumerRegisters.StateAwareBinaryConsumerRegister register = ConsumerRegisters.getStateAware(this::isActive);

        @Override
        Switchers.Switch getSwitch() {
            return register;
        }

        /**There are two caches for state, BaseEvent, which handles the Path's state (owner of this register), another within the AtomicBinaryEvent created on the fly.
         * AtomicBinaryEvent's state controls its own register and unregister functions, while This one retains state for the next incoming register (if any)*/
        @Override
        public void register(AtomicBinaryEvent booleanConsumer) {
            register.registerDispatch(booleanConsumer);
        }

        @Override
        public AtomicBinaryEvent unregister() {
            return register.unregisterDispatch();
        }

        @Override
        public boolean isRegistered() {
            return register.isRegistered();
        }

        @Override
        public AtomicBinaryEvent swapRegister(AtomicBinaryEvent expect, AtomicBinaryEvent set) {
            return register.swapRegister(expect, set);
        }
    }

    private static class AtomicWithFixed
            extends BaseStateRegister
    {

        private final AtomicBinaryEvent fixed;

        private AtomicWithFixed(AtomicBinaryEvent fixed) {
            this.fixed = fixed;
        }

        @Override
        Switchers.Switch getSwitch() {
            return fixed;
        }

        @Override
        public void register(AtomicBinaryEvent booleanConsumer) {
            throw new IllegalStateException("Fixed");
        }

        /**Unregister will shutOff the fixed AtomicBinaryEvent, rendering it unusable*/
        @Override
        public AtomicBinaryEvent unregister() {
            fixed.shutoff();
            return null;
        }

        @Override
        public boolean isRegistered() {
            return true;
        }

        @Override
        public String toString() {
            return "AtomicWithFixed{" +
                    "\n mainState=" + super.toString() +
                    ",\n fixed=" + fixed +
                    "}@" + hashCode();
        }

        @Override
        public AtomicBinaryEvent swapRegister(AtomicBinaryEvent expect, AtomicBinaryEvent set) {
            return null;
        }
    }

    public interface SwitchSynchronizer<K> {
        <S extends Switchers.Switch> S putIfAbsent(K key, S aSwitch) throws IllegalStateException;
        <S extends Switchers.Switch> boolean trueIfAbsent(K key, Supplier<S> aSwitchSupplier);
        <S extends SwitchSynchronizer<K>> void syncWith(K key, S parent);
        <S extends SwitchSynchronizer<K>> void syncWith(S parent);
        boolean remove(K key);
        boolean contains(K key);
        boolean isActive();

        @SuppressWarnings("unchecked")
        static <K, Inheritor extends SwitchSynchronizer<K>> Inheritor syncFactory(
                Inheritor parent,
                Supplier<Inheritor> childSupplier
        ) {
            Inheritor child = childSupplier.get();
            try {
                child.syncWith((K)child, parent);
                return child;
            } catch (Exception e) {
                throw new IllegalStateException("parent must be instance of K (key), use the Key factory instead", e);
            }
        }
        static <K, Inheritor extends SwitchSynchronizer<K>> Inheritor syncFactory(
                Inheritor parent,
                Function<Inheritor, K> key,
                Supplier<Inheritor> childSupplier
        ) {
            Inheritor child = childSupplier.get();
            child.syncWith(key.apply(child), parent);
            return child;
        }
        static <K, Inheritor extends SwitchSynchronizerImpl<K>, Event> Inheritor factory(
                Supplier<Inheritor> inheritorSupplier,
                Register<Event> lifecycleRegister,
                final Event ON,
                final Event OFF,
                final Event DESTROY
        ) {
            assert inheritorSupplier != null;
            Inheritor core = inheritorSupplier.get();
            SwitchSynchronizerImpl.syncWithEventRegister(lifecycleRegister, ON, OFF, DESTROY, core);
            return core;
        }
    }

    private static <S, T> AtomicBinaryEvent getBackgroundSwitch(BasePath<S> source, Builders.InputMethods<T, S> inputMethod, Consumer<? super T> consumer) {
        return Builders.BinaryEventConsumers.producerListenerDefaulter(source, inputMethod, consumer);
    }

    private static <S, T> AtomicBinaryEvent getSwitch(
            Executor executor,
            BasePath<S> source, Builders.InputMethods<T, S> inputMethod, Consumer<? super T> consumer) {
        return Builders.BinaryEventConsumers.producerListenerDefaulter(source, executor, inputMethod, consumer);
    }

    /**Convenience interface implementation for {@link SynchronizedModel}*/
    public interface ISynchronizedModel {
        SynchronizedModel get();
        default <S extends Switchers.Switch> S sync(S aSwitch) {
            return get().sync(aSwitch);
        }
        default <S extends Switchers.Switch> boolean sync(S aSwitch, Supplier<S> supplier) {
            return get().sync(aSwitch, supplier);
        }
        default <S extends Switchers.Switch> boolean remove(S aSwitch) {
            return get().remove(aSwitch);
        }
        default boolean isActive() {
            return get().isActive();
        }
        default <S, T, BP extends BasePath<S>> BP syncSource(
                BP basePath,
                Builders.InputMethods<T, S> inputMethod,
                Consumer<? super T> consumer
        ) {
            return get().syncSource(basePath, inputMethod, consumer);
        }

        default <T, BP extends BasePath<T>> BP syncSource(
                BP basePath,
                Consumer<? super T> consumer
        ) {
            return get().syncSource(basePath, consumer);
        }
        default <S, T, BP extends BasePath<S>, Cons extends Consumer<? super T>> Cons syncCons(
                BP basePath,
                Builders.InputMethods<T, S> inputMethod,
                Cons consumer
        ) {
            return get().syncCons(basePath, inputMethod, consumer);
        }
        default <T, BP extends BasePath<T>, Cons extends Consumer<? super T>> Cons syncCons(
                BP basePath,
                Cons consumer
        ) {
            return get().syncCons(basePath, consumer);
        }
        default <S, T, BP extends BasePath<S>>boolean threadSync(
                Executor executor,
                BP basePath,
                Builders.InputMethods<T, S> inputMethod,
                Consumer<? super T> consumer
        ){
            return get().threadSync(executor, basePath, inputMethod, consumer);
        }
        default <T, BP extends BasePath<T>>boolean threadSync(
                Executor executor,
                BP basePath,
                Consumer<? super T> consumer
        ){
            return get().threadSync(executor, basePath, consumer);
        }
        default <S, T, BP extends BasePath<S>, Cons extends Consumer<? super T>> boolean sync(
                BP basePath,
                Builders.InputMethods<T, S> inputMethod,
                Cons consumer
        ) {
            return get().sync(basePath, inputMethod, consumer);
        }
        default <T, BP extends BasePath<T>, Cons extends Consumer<? super T>> boolean sync(
                BP basePath,
                Cons consumer
        ) {
            return get().sync(basePath, consumer);
        }
    }

    /**A self-contained model that inherits from {@link AtomicBinaryEventConsumer} */
    public static class LiveModel extends SwitchSynchronizerImpl<Object> implements ISynchronizedModel {
        private final SynchronizedModel model = SynchronizedModel.getInstance(
                this
        );

        @Override
        public SynchronizedModel get() {
            return model;
        }

        @Override
        public boolean isActive() {
            return ISynchronizedModel.super.isActive();
        }
    }

    public static class SynchronizedModel {
        private final SwitchSynchronizer<Object> synchronizer;

        public static SynchronizedModel getInstance(SwitchSynchronizer<Object> synchronizer) {
            return new SynchronizedModel(synchronizer);
        }

        protected SynchronizedModel(SwitchSynchronizer<Object> synchronizer) {
            this.synchronizer = synchronizer;
        }

        protected SynchronizedModel(SynchronizedModel model) {
            this(model.synchronizer);
        }

        protected SynchronizedModel(ISynchronizedModel model) {
            this(model.get().synchronizer);
        }

        protected <S extends Switchers.Switch> S sync(S aSwitch) {
            return synchronizer.putIfAbsent(aSwitch, aSwitch);
        }

        protected <S, T, BP extends BasePath<S>, Cons extends Consumer<? super T>>BP syncSource(
                BP basePath,
                Builders.InputMethods<T, S> inputMethod,
                Cons consumer
        ) {
            synchronizer.putIfAbsent(
                    basePath,
                    getBackgroundSwitch(basePath, inputMethod, consumer)
            );
            return basePath;
        }

        protected <S, T, BP extends BasePath<S>, Cons extends Consumer<? super T>> boolean sync(
                BP basePath,
                Builders.InputMethods<T, S> inputMethod,
                Cons consumer
        ) {
            return synchronizer.trueIfAbsent(
                    basePath,
                    () -> getBackgroundSwitch(basePath, inputMethod, consumer)
            );
        }

        protected <T, BP extends BasePath<T>, Cons extends Consumer<? super T>> boolean sync(
                BP basePath,
                Cons consumer
        ) {
            return sync(
                    basePath,
                    Builders.InputMethods.identity(),
                    consumer
            );
        }

        protected <S, T, BP extends BasePath<S>, Cons extends Consumer<? super T>> Cons syncCons(
                BP basePath,
                Builders.InputMethods<T, S> inputMethod,
                Cons consumer
        ) {
            synchronizer.putIfAbsent(
                    basePath,
                    getBackgroundSwitch(basePath, inputMethod, consumer)
            );
            return consumer;
        }

        protected <T, BP extends BasePath<T>> BP syncSource(
                BP basePath,
                Consumer<? super T> consumer
        ) {
            return syncSource(
                    basePath,
                    Builders.InputMethods.identity(),
                    consumer
            );
        }

        protected <T, BP extends BasePath<T>, Cons extends Consumer<? super T>> Cons syncCons(
                BP basePath,
                Cons consumer
        ) {
            return syncCons(
                    basePath,
                    Builders.InputMethods.identity(),
                    consumer
            );
        }

        protected <S, T, BP extends BasePath<S>>boolean threadSync(
                Executor executor,
                BP basePath,
                Builders.InputMethods<T, S> inputMethod,
                Consumer<? super T> consumer
        ) {
            return synchronizer.trueIfAbsent(
                    basePath,
                    () -> getSwitch(executor, basePath, inputMethod, consumer)
            );
        }

        protected <T, BP extends BasePath<T>>boolean threadSync(
                Executor executor,
                BP basePath,
                Consumer<? super T> consumer
        ) {
            return threadSync(executor, basePath, Builders.InputMethods.identity(), consumer);
        }

        protected <S extends Switchers.Switch> boolean sync(S aSwitch, Supplier<S> supplier) {
            return synchronizer.trueIfAbsent(aSwitch, supplier);
        }

        protected <S extends Switchers.Switch> boolean remove(S aSwitch) {
            return synchronizer.remove(aSwitch);
        }

        protected boolean isActive() {
            return synchronizer.isActive();
        }

        @Override
        public String toString() {
            return "SynchronizedModel{" +
                    "synchronizer=" + synchronizer +
                    '}';
        }
    }

    /**AtomicBinaryEventConsumer is used instead because it's "shutOff()" method allows "Destroy" concatenation*/
    public static class SwitchSynchronizerImpl<K>
            extends AtomicBinaryEventConsumer
            implements SwitchSynchronizer<K>
    {
        private final Map<K, Switchers.Switch> suppliersSet = new HashMap<>();
        private final AtomicBoolean synced = new AtomicBoolean();
        /**Parent will drop listeners on destruction*/
        @Override
        public <S extends SwitchSynchronizer<K>> void syncWith(K key, S parent) {
            SYNC_CHECK_EXCEPT(
                    () -> parent.putIfAbsent(key, this)
            );
        }
        @Override
        @SuppressWarnings("unchecked")
        public <S extends SwitchSynchronizer<K>> void syncWith(S parent) {
            SYNC_CHECK_EXCEPT(
                    () -> {
                        try {
                            parent.putIfAbsent((K) this, this);
                        } catch (Exception e) {
                            throw new IllegalStateException("parent must be instance of K (key), use the Key factory instead", e);
                        }
                    }
            );
        }
        void SYNC_CHECK_EXCEPT(Runnable passed) {
            if (!synced.getAndSet(true)) {
                passed.run();
            } else
                throw new IllegalStateException("Manager already synced.");
        }

        public static <K, Inheritor extends SwitchSynchronizerImpl<K>, Event> void syncWithEventRegister(Register<Event> lifecycleRegister, Event ON, Event OFF, Event DESTROY, Inheritor core) {
            Predicate<Event> isOff, isOn, destroy;
            isOn = event -> event == ON;
            isOff = event -> event == OFF;
            destroy = event -> event == DESTROY;
            core.SYNC_CHECK_EXCEPT(
                    () -> lifecycleRegister.register(
                            event -> {
                                if (isOn.test(event)) core.on();
                                else if (isOff.test(event)) core.off();
                                else if (destroy.test(event)) core.shutoff();
                            }
                    )
            );
        }

        @Override
        public <S extends Switchers.Switch> S putIfAbsent(K key, S aSwitch) throws IllegalStateException {
            assert aSwitch != null && !isOff();
            if (!suppliersSet.containsKey(key)) {
                suppliersSet.put(key, aSwitch);
                if (isActive()) aSwitch.on();
                return aSwitch;
            } throw new IllegalStateException("Key: " + key + " already present in Map.");
        }
        @Override
        public <S extends Switchers.Switch> boolean trueIfAbsent(K key, Supplier<S> aSwitchSupplier) {
            assert aSwitchSupplier != null;
            if (!suppliersSet.containsKey(key)) {
                S aSwitch = aSwitchSupplier.get();
                assert aSwitch != null;
                suppliersSet.put(key, aSwitch);
                if (isActive()) aSwitch.on();
                return true;
            } return false;
        }
        @Override
        public boolean contains(K key) {
            return suppliersSet.containsKey(key);
        }
        @Override
        public boolean remove(K key) {
            Switchers.Switch removed = suppliersSet.remove(key);
            boolean removedB = removed != null;
            if (removedB && removed.isActive()) removed.off();
            return removedB;
        }

        @Override
        protected void onDestroyed() {
            synced.set(false);
            forEachSet(
                    aSwitch -> {
                        if (aSwitch instanceof AtomicBinaryEvent) ((AtomicBinaryEvent) aSwitch).shutoff();
                    }
            );
            suppliersSet.clear();
        }

        private void forEachSet(Consumer<Switchers.Switch> consumer) {
            for (Switchers.Switch s:suppliersSet.values()) consumer.accept(s);
        }

        @Override
        protected void onStateChange(boolean isActive) {
            if (isActive) forEachSet(Switchers.Switch::on);
            else forEachSet(Switchers.Switch::off);
        }

        @Override
        public String toString() {
            return "SwitchSynchronizerImpl{" +
                    "\n, synced=" + isActive() +
                    "\n }";
        }
    }

}
