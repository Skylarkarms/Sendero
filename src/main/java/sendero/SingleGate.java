package sendero;

import sendero.event_registers.ConsumerRegisters;
import sendero.interfaces.BinaryPredicate;
import sendero.interfaces.Register;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class SingleGate {
    public static class IO<T> extends Outs.OutBaseSinglePath.Single<T> implements Holders.StatefulHolder<T> {

        public IO() {
            super();
        }

        public IO(T value) {
            super(Builders.getHolderBuild(holderBuilder -> holderBuilder.withInitial(value)));
        }

        public IO(UnaryOperator<Builders.HolderBuilder<T>> operator) {
            super(Builders.getHolderBuild(operator));
        }

        @Override
        public IO<T> setMap(UnaryOperator<T> map) {
            super.setMap(map);
            return this;
        }

        @Override
        public IO<T> expectIn(Predicate<T> expect) {
            super.setExpectInput(expect);
            return this;
        }

        @Override
        public Holders.StatefulHolder<T> expectIn(BinaryPredicate<T> expect) {
            super.setExpectInput(expect);
            return this;
        }

        @Override
        public IO<T> expectOut(Predicate<T> expect) {
            setExpectOutput(expect);
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

        @Override
        public void update(long delay, UnaryOperator<T> update) {
            super.update(delay, update);
        }
    }
    public static class In<T> extends SinglePath<T> implements Holders.StatefulHolder<T> {

        public In() {
            super();
        }

        public In(T value) {
            super(Builders.getHolderBuild(tHolderBuilder -> tHolderBuilder.withInitial(value)));
        }

        public In(UnaryOperator<Builders.HolderBuilder<T>> operator) {
            super(Builders.getHolderBuild(operator));
        }

        @Override
        public In<T> setMap(UnaryOperator<T> map) {
            super.setMap(map);
            return this;
        }

        @Override
        public In<T> expectIn(Predicate<T> expect) {
            super.setExpectInput(expect);
            return this;
        }

        @Override
        public In<T> expectIn(BinaryPredicate<T> expect) {
            super.setExpectInput(expect);
            return this;
        }

        @Override
        public In<T> expectOut(Predicate<T> expect) {
            setExpectOutput(expect);
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

        @Override
        public void update(long delay, UnaryOperator<T> update) {
            super.update(delay, update);
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

        static class OutBaseSinglePath<T> extends SinglePath<T> {
//        static class OutBasePath<T> extends Path<T> {
            public OutBaseSinglePath() {
            }

            OutBaseSinglePath(Builders.HolderBuilder<T> holderBuilder) {
                super(holderBuilder);
            }

            protected static class Many<T> extends OutBaseSinglePath<T> implements Out.Many<T> {

                private final SimpleLists.LockFree<Consumer<? super T>, Integer>
                        locale = SimpleLists.getSnapshotting(Consumer.class, this::getVersion);

                public Many() {
                    super();
                }

                Many(Builders.HolderBuilder<T> holderBuilder) {
                    super(holderBuilder);
                }

                private Runnable dispatchCommandFun(Pair.Immutables.Int<T> pair)/* = pair -> (Runnable) () ->*/ {
                    return () -> {
                        boolean emptyLocale = locale.isEmpty();
                        if (emptyLocale) pathDispatch(false, pair);
                        else {
                            pathDispatch(true, pair);
                            //If we are out of luck, lists may be empty at this point but it won't matter.
                            for (Consumer<? super T> observer:locale
                            ) {
                                if (pair.compareTo(getVersion()) != 0) return;
                                //consecutive "losing" threads that got pass this check might get a boost so we should prevent the override of lesser versions on the other end.
                                //And the safety measure will end with subscriber's own version of dispatch();
                                observer.accept(pair.getValue());
                            }
                        }
                    };
                }

                @Override
                void dispatch(long delay, Pair.Immutables.Int<T> t) {
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
                void coldDispatch(Pair.Immutables.Int<T> t) {
                    Consumer<? super T>[] locals = locale.copy();
                    final int length = locals.length;
                    if (length != 0) parallelDispatch(0, locals, t, Pair.Immutables.Int::getValue); // first locals, then keep with domain
                    pathDispatch(false, t);//to appointees
                }

                @Override
                public boolean isIdle() {
                    return super.isIdle();
                }

                @Override
                public void unregister(Consumer<T> consumer) {
                    if (locale.remove(consumer)) {
                        tryDeactivate();
                    }
                }

                @Override
                public void register(Consumer<? super T> valueConsumer) {
                    onAdd(valueConsumer,
                            (Function<Consumer<? super T>, Pair.Immutables.Bool<Integer>>) locale::snapshotAdd,
                            Pair.Immutables.Int::getValue
                    );
                }

                @Override
                boolean deactivationRequirements() {
                    return locale.isEmpty() && super.deactivationRequirements();
                }
            }

            protected static class Single<T> extends OutBaseSinglePath<T> implements Out.Single<T> {

                private final ConsumerRegisters.IConsumerRegister.SnapshottingConsumerRegister<Integer, T>
                        locale = ConsumerRegisters.IConsumerRegister.getInstance(this::getVersion);

                public Single() {
                }

                Single(Builders.HolderBuilder<T> holderBuilder) {
                    super(holderBuilder);
                }

                private Runnable dispatchCommandFunction(Pair.Immutables.Int<T> t) {
                    return () -> {
                        boolean registered = locale.isRegistered();
                        if (!registered) pathDispatch(false, t);
//                    if (!registered) super.dispatchAppointees(false, t);
                        else {
                            pathDispatch(true, t);
                            if (t.compareTo(getVersion()) != 0) return;
                            locale.accept(t.getValue());
                        }
                    };
                }

                @Override
                void dispatch(long delay, Pair.Immutables.Int<T> t) {
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
                void coldDispatch(Pair.Immutables.Int<T> t) {
                    fastExecute(
                            () -> {
                                if (t.compareTo(getVersion()) != 0) return;
                                locale.accept(t.getValue());
                            }
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
                            valueConsumer,
                            consumer -> locale.snapshotRegister((Consumer<T>) consumer),
                            Pair.Immutables.Int::getValue
                    );
                }

                @Override
                public void unregister() {
                    if (locale.unregister() != null) tryDeactivate();

                }

            }

        }
    }
}
