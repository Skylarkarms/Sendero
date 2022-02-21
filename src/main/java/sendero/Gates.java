package sendero;

import sendero.event_registers.ConsumerRegister;
import sendero.interfaces.BooleanConsumer;
import sendero.interfaces.Register;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class Gates {
    public static class IO<T> extends Outs.OutBasePath.Many<T> implements Holders.StatefulHolder<T> {

        public IO() {
            super();
        }

        public IO(T value) {
            super();
            accept(value);
        }

        @Override
        public IO<T> setMap(UnaryOperator<T> map) {
            superSetMap(map);
            return this;
        }

        @Override
        public IO<T> expectIn(Predicate<T> expect) {
            superSetExpectInput(expect);
            return this;
        }

        @Override
        public IO<T> expectOut(Predicate<T> expect) {
            superSetExpectOutput(expect);
            return this;
        }

        @Override
        public void update(UnaryOperator<T> update) {
            super.update(update);
        }

        @Override
        public void accept(T t) {
            super.accept(t);
        }

        @Override
        public T get() {
            return super.get();
        }
    }
    public static class In<T> extends Path<T> implements Holders.StatefulHolder<T> {

        public In() {
            super();
        }

        public In(T value) {
            super();
            accept(value);
        }

        @Override
        public In<T> setMap(UnaryOperator<T> map) {
            superSetMap(map);
            return this;
        }

        @Override
        public In<T> expectIn(Predicate<T> expect) {
            superSetExpectInput(expect);
            return this;
        }

        @Override
        public In<T> expectOut(Predicate<T> expect) {
            superSetExpectOutput(expect);
            return this;
        }

        @Override
        public void update(UnaryOperator<T> update) {
            super.update(update);
        }

        @Override
        public void accept(T t) {
            super.accept(t);
        }

        @Override
        public T get() {
            return super.get();
        }
    }
    public interface Out<T> extends Register<T> {
        interface Many<T> extends Out<T> {
            void unregister(Consumer<T> consumer);
        }
        interface Single<T> extends Out<T> {
            void unregister();
        }
    }
    static final class Outs {
        static class OutBasePath<T> extends Path<T> {
            public OutBasePath() {
            }

            public OutBasePath(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
                super(selfMap);
            }

            protected static class Many<T> extends OutBasePath<T> implements Out.Many<T> {

                private final SimpleLists.SimpleList.LockFree.Snapshotting<Consumer<? super T>, Integer>
                        locale = SimpleLists.getSnapshotting(Consumer.class, this::getVersion);

                public Many() {
                    super();
                }

                @Override
                protected void dispatch(Pair.Immutables.Int<T> t) {
                    //It may be the case that the JNI hangs the thread for whatever reason it may deem proper.
                    //One example would arise in the case that the processes between holder CAS and THIS dispatch() take too long to resolve.
                    //And this, could very well be the case if some heavy transformation is being performed (at the subscriber level (If extended by Subscriber))
                    // between subscriber.accept(t) AND subscriber's own version of dispatch().
                    // But not so much at the Holder level (since there is almost no computation between CAS and this dispatch), but one should never assume...
                    //In that case, a version of T that should have arrived earlier, could arrive as being the last one, overriding the potential true last response.
                    //If this happens the result would be < 0.
                    //To prevent this, Subscriber MUST TAKE NOTES OF ITS OWN VERSION, and grant access to it by overriding getVersion(), for this class to be able to use.
                    super.dispatch(t); // Dispatch 2ManyDomain first (from Many2Many Obs)
                    if (!locale.isEmpty()) {
                        //If we are out of luck, lists may be empty at this point but it won't matter.
                        for (Consumer<? super T> observer:locale
                        ) {
                            if (t.compareTo(getVersion()) != 0) return;
                            //consecutive "losing" threads that got pass this check might get a boost so we should prevent the override of lesser versions on the other end.
                            //And the safety measure will end with subscriber's own version of dispatch();
                            observer.accept(t.getValue());
                        }
                    }
                }

                @Override
                protected void coldDispatch(Pair.Immutables.Int<T> t) {
                    Consumer<? super T>[] locals = locale.copy();
                    final int length = locals.length;
                    if (length != 0) parallelDispatch(0, locals, t, Pair.Immutables.Int::getValue); // first locals, then keep with domain
                    super.coldDispatch(t);//to domain
                }

                @Override
                public boolean isIdle() {
                    return super.isIdle();
                }

                @Override
                public void unregister(Consumer<T> consumer) {
                    tryDeactivate(locale.remove(consumer));
                }

                @Override
                public void register(Consumer<T> valueConsumer) {
                    onAdd(valueConsumer,
                            (Function<Consumer<? super T>, Pair.Immutables.Bool<Integer>>) locale::add,
                            Pair.Immutables.Int::getValue
                    );
                }
            }

            protected static class Single<T> extends OutBasePath<T> implements Out.Single<T> {

                private final ConsumerRegister.IConsumerRegister.SnapshottingConsumerRegister<Integer, T>
                        locale = ConsumerRegister.IConsumerRegister.getInstance(this::getVersion);

                public Single(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
                    super(selfMap);
                }

                @Override
                protected void dispatch(Pair.Immutables.Int<T> t) {
                    //It may be the case that the JNI hangs the thread for whatever reason it may deem proper.
                    //One example would arise in the case that the processes between holder CAS and THIS dispatch() takes too long to resolve.
                    //And this, could very well be the case if some heavy transformation is being performed (at the subscriber level (If extended by Subscriber))
                    // between subscriber.accept(t) AND subscriber's own version of dispatch().
                    // But not so much at the Holder level (since there is almost no computation between CAS and this dispatch), but one should never assume...
                    //In that case, a version of T that should have arrived earlier, could arrive as being the last one, overriding the potential true last response.
                    //If this happens the result would be < 0.
                    //To prevent this, Subscriber MUST TAKE NOTES OF ITS OWN VERSION, and grant access to it by overriding getVersion(), for this class to be able to use.
                    super.dispatch(t); // dispatch injective Domain first (One2One Observable)
                    if (locale.isRegistered()) {
                        if (t.compareTo(getVersion()) != 0) return;
                        locale.accept(t.getValue());
                    }

                }

                @Override
                protected void coldDispatch(Pair.Immutables.Int<T> t) {
                    execute(
                            () -> {
                                if (t.compareTo(getVersion()) != 0) return;
                                locale.accept(t.getValue());
                            }
                    );
                    super.coldDispatch(t);
                }

                @Override
                protected boolean deactivationRequirements() {
                    return !locale.isRegistered() && super.deactivationRequirements();
                }

                @SuppressWarnings("unchecked")
                @Override
                public void register(Consumer<T> valueConsumer) {
                    onAdd(
                            valueConsumer,
                            consumer -> locale.snapshotRegister((Consumer<T>) consumer),
                            Pair.Immutables.Int::getValue
                    );
                }

                @Override
                public void unregister() {
                    tryDeactivate(locale.unregister() != null);

                }

            }

        }

        static class ManyImpl<T> extends Holders.ExecutorHolder<T> implements Out.Many<T> {

            private final SimpleLists.SimpleList.LockFree.Snapshotting<Consumer<? super T>, Integer>
                    locale = SimpleLists.getSnapshotting(Consumer.class, this::getVersion);

            protected ManyImpl(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
                super(selfMap);
            }


            @Override
            protected void dispatch(Pair.Immutables.Int<T> t) {
                //It may be the case that the JNI hangs the thread for whatever reason it may deem proper.
                //One example would arise in the case that the processes between holder CAS and THIS dispatch() take too long to resolve.
                //And this, could very well be the case if some heavy transformation is being performed (at the subscriber level (If extended by Subscriber))
                // between subscriber.accept(t) AND subscriber's own version of dispatch().
                // But not so much at the Holder level (since there is almost no computation between CAS and this dispatch), but one should never assume...
                //In that case, a version of T that should have arrived earlier, could arrive as being the last one, overriding the potential true last response.
                //If this happens the result would be < 0.
                //To prevent this, Subscriber MUST TAKE NOTES OF ITS OWN VERSION, and grant access to it by overriding getVersion(), for this class to be able to use.
//                super.dispatch(t); // Dispatch 2ManyDomain first (from Many2Many Obs)
                if (!locale.isEmpty()) {
                    //If we are out of luck, lists may be empty at this point but it won't matter.
                    for (Consumer<? super T> observer:locale
                    ) {
                        if (t.compareTo(getVersion()) != 0) return;
                        //consecutive "losing" threads that got pass this check might get a boost so we should prevent the override of lesser versions on the other end.
                        //And the safety measure will end with subscriber's own version of dispatch();
                        observer.accept(t.getValue());
                    }
                }
            }

            @Override
            protected void coldDispatch(Pair.Immutables.Int<T> t) {
                Consumer<? super T>[] locals = locale.copy();
                final int length = locals.length;
                if (length != 0) parallelDispatch(0, locals, t, Pair.Immutables.Int::getValue); // first locals, then keep with domain
//                super.coldDispatch(t);//to domain
            }

            @Override
            public boolean isIdle() {
                return super.isIdle();
            }

            @Override
            public void unregister(Consumer<T> consumer) {
                tryDeactivate(locale.remove(consumer));
            }

            @Override
            public void register(Consumer<T> valueConsumer) {
                onAdd(valueConsumer,
                        (Function<Consumer<? super T>, Pair.Immutables.Bool<Integer>>) locale::add,
                        (Function<Pair.Immutables.Int<T>, T>) Pair.Immutables.Int::getValue
                );
            }
        }

        static class SingleImpl<T> extends Holders.ExecutorHolder<T> implements Out.Single<T> {

            private final ConsumerRegister.IConsumerRegister.SnapshottingConsumerRegister<Integer, T>
                    locale = ConsumerRegister.IConsumerRegister.getInstance(this::getVersion);

            protected SingleImpl(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
                super(selfMap);
            }

            @Override
            protected void dispatch(Pair.Immutables.Int<T> t) {
                //It may be the case that the JNI hangs the thread for whatever reason it may deem proper.
                //One example would arise in the case that the processes between holder CAS and THIS dispatch() takes too long to resolve.
                //And this, could very well be the case if some heavy transformation is being performed (at the subscriber level (If extended by Subscriber))
                // between subscriber.accept(t) AND subscriber's own version of dispatch().
                // But not so much at the Holder level (since there is almost no computation between CAS and this dispatch), but one should never assume...
                //In that case, a version of T that should have arrived earlier, could arrive as being the last one, overriding the potential true last response.
                //If this happens the result would be < 0.
                //To prevent this, Subscriber MUST TAKE NOTES OF ITS OWN VERSION, and grant access to it by overriding getVersion(), for this class to be able to use.
//                super.dispatch(t); // dispatch injective Domain first (One2One Observable)
                if (locale.isRegistered()) {
                    if (t.compareTo(getVersion()) != 0) {
                        return;
                    }
                    locale.accept(t.getValue());
                }

            }

            @Override
            protected void coldDispatch(Pair.Immutables.Int<T> t) {
                execute(
                        () -> {
                            if (t.compareTo(getVersion()) != 0) return;
                            locale.accept(t.getValue());
                        }
                );
                super.coldDispatch(t);
            }

            @Override
            protected boolean deactivationRequirements() {
                return !locale.isRegistered() && super.deactivationRequirements();
            }

            @SuppressWarnings("unchecked")
            @Override
            public void register(Consumer<T> valueConsumer) {
                onAdd(
                        valueConsumer,
                        consumer -> locale.snapshotRegister((Consumer<T>) consumer),
                        Pair.Immutables.Int::getValue
                );
            }

            @Override
            public void unregister() {
                tryDeactivate(locale.unregister() != null);

            }

        }
    }
}
