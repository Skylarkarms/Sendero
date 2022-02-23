package sendero;

import sendero.functions.Functions;
import sendero.pairs.Pair;

import java.util.function.Predicate;

class DispatcherImpl<T> extends Dispatcher<T> {

    private final Predicate<T> CLEARED_PREDICATE = Functions.always(true);
    private volatile Predicate<T> expectOutput = CLEARED_PREDICATE;

    @Override
    protected void setExpectOutput(Predicate<T> expectOutput) {
        this.expectOutput = expectOutput;
    }

    protected void inferDispatch(Pair.Immutables.Int<T> t, boolean isCold) {
        if (expectOutput.test(t.getValue())) {
            if (isCold) coldDispatch(t);
            else dispatch(t);
        }
    }

    protected boolean outPutTest(T value) {
        return expectOutput.test(value);
    }

    @Override
    protected void coldDispatch(Pair.Immutables.Int<T> t) {}
    @Override
    protected void dispatch(Pair.Immutables.Int<T> t) {}
}