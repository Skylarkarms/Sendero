package sendero;

import sendero.event_registers.ConsumerRegisters;
import sendero.interfaces.ConsumerUpdater;
import sendero.interfaces.Register;
import sendero.lists.SimpleLists;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.Holders.SwapBroadcast.HOT;
import static sendero.functions.Functions.myIdentity;

public final class Gate {
    public static final class IO<T> extends Outs.OutBasePath.Many<T> implements Holders.HolderIO<T> {
        private final ConsumerUpdater<T> consumerUpdater = Inputs.getConsumerUpdater(this);

        public IO() {
            super();
        }

        public IO(T value) {
            super(holderBuilder -> holderBuilder.withInitial(value));
        }

        @Override
        public T updateAndGet(UnaryOperator<T> update) {
            return consumerUpdater.updateAndGet(update);
        }

        @Override
        public void accept(T t) {
            consumerUpdater.accept(t);
        }

        @Override
        public void update(long delay, UnaryOperator<T> update) {
            consumerUpdater.update(delay, update);
        }
    }
    public static class Acceptor<T> extends Path<T> implements Consumer<T> {
        private final Consumer<T> scoped = Inputs.getConsumer(this);

        public Acceptor(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
            super(builderOperator);
        }

        public Acceptor(T initialValue) {
            super(initialValue);
        }

        @Override
        public void accept(T t) {
            scoped.accept(t);
        }
    }
    public static class Updater<T> extends Path<T> implements sendero.interfaces.Updater<T> {

        private final sendero.interfaces.Updater<T> scoped = Inputs.getUpdater(this);

        public Updater() {
            super();
        }

        public Updater(T initialValue) {
            super(initialValue);
        }

        @Override
        public T updateAndGet(UnaryOperator<T> update) {
            return scoped.updateAndGet(update);
        }

        @Override
        public void update(long delay, UnaryOperator<T> update) {
            scoped.update(delay, update);
        }
    }
    public static class In<T> extends Path<T> implements Holders.HolderIO<T> {

        private final ConsumerUpdater<T> consumerUpdater = Inputs.getConsumerUpdater(this);

        public In(UnaryOperator<Builders.HolderBuilder<T>> operator) {
            super(operator);
        }

        public In() {
            super();
        }

        public In(T initialValue) {
            super(initialValue);
        }

        @Override
        public In<T> store(String TAG) {
            return (In<T>) super.store(TAG);
        }

        @Override
        public T updateAndGet(UnaryOperator<T> update) {
            return consumerUpdater.updateAndGet(update);
        }

        @Override
        public void accept(T t) {
            consumerUpdater.accept(t);
        }

        @Override
        public void update(long delay, UnaryOperator<T> update) {
            consumerUpdater.update(delay, update);
        }
    }
    public interface Out<T> extends Register<T> {
        interface Many<T> extends Out<T> {
            void unregister(Consumer<? super T> consumer);
        }
        interface Single<T> extends Out<T> {
            void unregister();
        }
    }
    static final class Outs {

        static class OutBasePath<T> extends Path<T> {
            public OutBasePath() {
            }

            OutBasePath(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
                super(builderOperator);
            }

            protected static class Many<T> extends OutBasePath<T> implements Out.Many<T> {

                private final SimpleLists.LockFree.Snapshooter<Consumer<? super T>, Immutable.Values>
                        locale = SimpleLists.getSnapshotting(Consumer.class, this::localSerialValues);

                public Many() {
                    super();
                }

                Many(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
                    super(builderOperator);
                }

                private Runnable dispatchCommandFun(Immutable<T> pair) {
                    return () -> {
                        boolean emptyLocale = locale.isEmpty();
                        if (emptyLocale) pathDispatch(false, pair);
                        else {
                            pathDispatch(true, pair);
                            Consumer<? super T>[] consumers = locale.copy();
                            //If we are out of luck, lists may be empty at this point, but it won't matter.
                            for (Consumer<? super T> observer:consumers
                            ) {
//                                //consecutive "losing" threads that got pass this check might get a boost, so we should prevent the override of lesser versions on the other end.
//                                //And the safety measure will end with subscriber's own version of dispatch();
                                inferDispatch(pair, observer);
                            }
                        }
                    };
                }

                @Override
                void dispatch(long delay, Immutable<T> t) {
                    //It may be the case that the JNI hangs the thread for whatever reason it may deem proper.
                    //One example would arise in the case that the processes between holder CAS and THIS dispatch() take too long to resolve.
                    //And this, could very well be the case if some heavy transformation is being performed (at the subscriber level (If extended by Subscriber))
                    // between subscriber.accept(t) AND subscriber's own version of dispatch().
                    // But not so much at the Holder level (since there is almost no computation between CAS and this dispatch), but one should never assume...
                    //In that case, a version of T that should have arrived earlier, could arrive as being the last one, overriding the potential true last response.
                    //If this happens the result would be < 0.
                    //To prevent this, Subscriber MUST TAKE NOTES OF ITS OWN VERSION, and grant access to it by overriding getVersion(), for this class to be able to use.

                    Runnable dispatchCommand = dispatchCommandFun(t);
                    scheduleExecution(delay, dispatchCommand);
                }

                @Override
                void coldDispatch(Immutable<T> t) {
                    Consumer<? super T>[] locals = locale.copy();
                    final int length = locals.length;
                    if (length != 0) parallelDispatch(0, locals, t,
                            Immutable::get
                    ); // first locals, then keep with domain
                    pathDispatch(false, t);//to appointees
                }

                @Override
                public boolean isActive() {
                    return super.isActive();
                }

                @Override
                public void unregister(Consumer<? super T> consumer) {
                    if (locale.remove(consumer)) deactivate();
                }

                @Override
                public void register(Consumer<? super T> valueConsumer) {
                    onAdd(
                            res -> valueConsumer.accept(res.get()),
                            () ->  locale.snapshotAdd(valueConsumer)
                    );
                }

                @Override
                boolean deactivationRequirements() {
                    return locale.isEmpty() && super.deactivationRequirements();
                }
            }

            protected static class Single<T> extends OutBasePath<T> implements Out.Single<T> {

                private final ConsumerRegisters.IConsumerRegister.SnapshottingConsumerRegister<Immutable.Values, T>
                        locale = ConsumerRegisters.IConsumerRegister.getInstance(this::localSerialValues);

                private Runnable dispatchCommandFunction(Immutable<T> t) {
                    return () -> {
                        boolean registered = locale.isRegistered();
                        if (!registered) pathDispatch(false, t);
                        else {
                            pathDispatch(true, t);
                            inferDispatch(t, locale);
                        }
                    };
                }

                @Override
                void dispatch(long delay, Immutable<T> t) {
                    //It may be the case that the JNI hangs the thread for whatever reason it may deem proper.
                    //One example would arise in the case that the processes between holder CAS and THIS dispatch() takes too long to resolve.
                    //And this, could very well be the case if some heavy transformation is being performed (at the subscriber level (If extended by Subscriber))
                    // between subscriber.accept(t) AND subscriber's own version of dispatch().
                    // But not so much at the Holder level (since there is almost no computation between CAS and this dispatch), but one should never assume...
                    //In that case, a version of T that should have arrived earlier, could arrive as being the last one, overriding the potential true last response.
                    //If this happens the result would be < 0.
                    //To prevent this, Subscriber MUST TAKE NOTES OF ITS OWN VERSION, and grant access to it by overriding getVersion(), for this class to be able to use.

                    Runnable dispatchCommand = dispatchCommandFunction(t);
                    scheduleExecution(delay, dispatchCommand);
                }

                @Override
                void coldDispatch(Immutable<T> t) {
                    fastExecute(
                            () -> inferDispatch(t, locale)
                    );
                    pathDispatch(false, t);
                }

                @Override
                boolean deactivationRequirements() {
                    return !locale.isRegistered() && super.deactivationRequirements();
                }

                @SuppressWarnings("unchecked")
                @Override
                public void register(Consumer<? super T> valueConsumer) {
                    onAdd(
                            res -> valueConsumer.accept(res.get()),
                            () -> locale.snapshotRegister((Consumer<T>) valueConsumer)
                    );
                }

                @Override
                public void unregister() {
                    if (locale.unregister() != null) deactivate();

                }

            }

        }

        // Outs that do not belong to the basePath family do not need to perform any dispatching on new threads.
        // Hence, should only extend ActivationHolder.class
        static class ManyImpl<T> extends Holders.ActivationHolder<T> implements Out.Many<T> {

            private final SimpleLists.LockFree.Snapshooter<Consumer<? super T>, Immutable.Values>
                    locale = SimpleLists.getSnapshotting(Consumer.class, this::localSerialValues);

            protected ManyImpl(
                    Function<Holders.StreamManager<T>, AtomicBinaryEvent> selfMap
            ) {
                super(
                        myIdentity(),
                        Builders.withFixed(selfMap)
                );
            }

            @Override
            void dispatch(long delay, Immutable<T> t) {
                //It may be the case that the JNI hangs the thread for whatever reason it may deem proper.
                //One example would arise in the case that the processes between holder CAS and THIS dispatch() take too long to resolve.
                //And this, could very well be the case if some heavy transformation is being performed (at the subscriber level (If extended by Subscriber))
                // between subscriber.accept(t) AND subscriber's own version of dispatch().
                // But not so much at the Holder level (since there is almost no computation between CAS and this dispatch), but one should never assume...
                //In that case, a version of T that should have arrived earlier, could arrive as being the last one, overriding the potential true last response.
                //If this happens the result would be < 0.
                //To prevent this, Subscriber MUST TAKE NOTES OF ITS OWN VERSION, and grant access to it by overriding getVersion(), for this class to be able to use.

                if (!locale.isEmpty()) {

                    //If we are out of luck, lists may be empty at this point, but it won't matter.
                    for (Consumer<? super T> observer:locale) inferDispatch(t, observer);
                }

                if (delay > HOT) throw new IllegalArgumentException("This dispatcher cannot be delayed.");
            }

            @Override
            void coldDispatch(Immutable<T> t) {
                Consumer<? super T>[] locals = locale.copy();
                final int length = locals.length;
                if (length == 0) return;
                for (int i = 0; i < length; i++) {
                    inferDispatch(t, locals[i]);
                }
            }

            @Override
            public boolean isActive() {
                return super.isActive();
            }

            @Override
            public void unregister(Consumer<? super T> consumer) {
                if (locale.remove(consumer)) tryDeactivate();
            }

            @Override
            public void register(Consumer<? super T> valueConsumer) {
                onRegistered(
                        res -> valueConsumer.accept(res.get()),
                        () -> locale.snapshotAdd(valueConsumer),
                        Runnable::run
                );

            }
        }

        static class SingleImpl<T> extends Holders.ActivationHolder<T> implements Out.Single<T> {

            private final ConsumerRegisters.IConsumerRegister.SnapshottingConsumerRegister<Immutable.Values, T>
                    locale = ConsumerRegisters.IConsumerRegister.getInstance(this::localSerialValues);

            protected SingleImpl(
                    Function<Holders.StreamManager<T>, AtomicBinaryEvent> selfMap
            ) {
                super(
                        myIdentity(),
                        Builders.withFixed(selfMap)
                );
            }

            @Override
            void dispatch(long delay, Immutable<T> t) {
                //It may be the case that the JNI hangs the thread for whatever reason it may deem proper.
                //One example would arise in the case that the processes between holder CAS and THIS dispatch() takes too long to resolve.
                //And this, could very well be the case if some heavy transformation is being performed (at the subscriber level (If extended by Subscriber))
                // between subscriber.accept(t) AND subscriber's own version of dispatch().
                // But not so much at the Holder level (since there is almost no computation between CAS and this dispatch), but one should never assume...
                //In that case, a version of T that should have arrived earlier, could arrive as being the last one, overriding the potential true last response.
                //If this happens the result would be < 0.
                //To prevent this, Subscriber MUST TAKE NOTES OF ITS OWN VERSION, and grant access to it by overriding getVersion(), for this class to be able to use.
                if (locale.isRegistered()) {
                    if (!inferDispatch(t, locale)) return;

                }

                if (delay > HOT) throw new IllegalArgumentException("This dispatcher cannot be delayed.");
            }

            @Override
            void coldDispatch(Immutable<T> t) {
                inferDispatch(t, locale);
            }

            @Override
            boolean deactivationRequirements() {
                return !locale.isRegistered() && super.deactivationRequirements();
            }

            @SuppressWarnings("unchecked")
            @Override
            public void register(Consumer<? super T> valueConsumer) {
                onRegistered(
                        res -> valueConsumer.accept(res.get()),
                        () -> locale.snapshotRegister((Consumer<T>) valueConsumer),
                        Runnable::run
                );
            }

            @Override
            public void unregister() {
                if (locale.unregister() != null) tryDeactivate();
            }

        }
    }
}
