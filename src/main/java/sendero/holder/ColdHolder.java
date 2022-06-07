package sendero.holder;

import java.util.function.Supplier;

public interface ColdHolder<T> extends Supplier<T>/*, ColdReceptor<T>*/ {
//public interface ColdHolder<T> extends Supplier<T>, BranchSynchronizer<T> {
//    T getAndInvalidate();
//    Immutable<T> getSnapshot();
}
