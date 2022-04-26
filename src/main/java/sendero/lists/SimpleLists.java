package sendero.lists;

import sendero.pairs.Pair;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SimpleLists {
    private static final Object[] DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA = {};
    private static final int DEFAULT_CAPACITY = 10;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    public static<E, Snapshot> LockFree<E, Snapshot> getSnapshotting(Class<? super E> componentType, Supplier<Snapshot> snapshotSupplier) {
        return new LockFreeImpl<>( componentType, snapshotSupplier);
    }

    public static<E, Snapshot> LockFree<E, Snapshot> get(Class<? super E> componentType) {
        return new LockFreeImpl<>( componentType);
    }

    public interface LockFree<E, Snapshot> extends Iterable<E> {
        /**returns true if this is the first item to be added*/
        Pair.Immutables.Bool<Snapshot> snapshotAdd(E element);
        /**returns true if this is the first item to be added*/
        boolean add(E element);
        /**returns true if this is the last item to be removed*/
        boolean remove(E element);
        boolean removeIf(Predicate<E> removeIf);
        boolean isEmpty();
        E[] copy();
    }

    final static class Snapshot<E> {
        final E[] copy;
        final int version;
        final int size;

        private static<T> Snapshot<T> initialize(T[] EMPTY_ARRAY) {
            return new Snapshot<>(EMPTY_ARRAY, 0, 0);
        }

        private Snapshot(E[] copy, int version, int size/*, boolean removed*/) {
            this.copy = copy;
            this.version = version;
            this.size = size;
        }
        private static<E> int find(E element, E[] prevArray, int prevSize) {
            int i = 0;
            if (element == null) {
                for (; i < prevSize; i++)
                    if (prevArray[i] == null)
                        return i;
            } else {
                for (; i < prevSize; i++)
                    if (element.equals(prevArray[i]))
                        return i;
            }
            return -1;
        }
        private static<E> int findIf(Predicate<E> removeIf, E[] prevArray, int prevSize) {
            int i = 0;
            for (; i < prevSize; i++) {
                E toTest = prevArray[i];
                if (toTest != null && removeIf.test(toTest))
                    return i;
            }
            return -1;
        }
        private static final int retries = 3;
        private static <E> boolean remove(AtomicReference<Snapshot<E>> atomicReference, E element) {
            //When a rapid succession of actions are executed, where an "add" followed by a "remove" is performed && the Collection has 0 items
            //The remove may arrive before the add,
            // And the final state will result in an item being added and size being 1, when it should have been 0.
            // to prevent a wrong final state we must retry until the add arrives.

            int i = 0;

            Snapshot<E> prev = atomicReference.get(),
                    next = remove(element, prev);
            boolean wasLast = prev.size == 1 && next != prev,
                    zeroSize = prev.size <= 0, shouldRetry;

            for (boolean same = true;;i++) {
                if (!same) {
                    zeroSize = prev.size <= 0;
                }
                shouldRetry = zeroSize && i <= retries;
                if (!zeroSize && !same) {
                    next = remove(element, prev);
                    wasLast = prev.size == 1 && next != prev;
                }
                //If the item is not found after all retries, then next remains the same as prev and wasLast == false.
                if (!shouldRetry && atomicReference.compareAndSet(prev, next)) return wasLast;
                if (same == (same = (prev == (prev = atomicReference.get()))) && !shouldRetry) return false;
            }

        }
        private static <E> boolean removeIf(AtomicReference<Snapshot<E>> atomicReference, Predicate<E> removeIf) {
            boolean wasLast;
            Snapshot<E> prev, next;
            do {
                prev = atomicReference.get();
                next = removeIf(removeIf, prev);
                wasLast = prev.size == 1 && next != prev;
            } while (!atomicReference.compareAndSet(prev, next));
            return wasLast;
        }
        private static <S, E> Pair.Immutables.Bool<S> remove(AtomicReference<Snapshot<E>> atomicReference, E element, Supplier<S> sSupplier) {
            boolean wasLast;
            Snapshot<E> prev, next;
            S snap;
            do {
                prev = atomicReference.get();
                next = remove(element, prev);
                wasLast = prev.size == 1 && next != prev;
                snap = sSupplier.get();
            } while (!atomicReference.compareAndSet(prev, next));
            return new Pair.Immutables.Bool<>(wasLast, snap);
        }
        @SuppressWarnings("unchecked")
        private static<E> Snapshot<E> remove(E element, Snapshot<E> prevSnap) {
            E[] prevArray = prevSnap.copy;
            int prevSize = prevSnap.size;
            int index = find(element, prevArray, prevSize);
            if (index != -1) {
                int finalSize = prevSize - 1;
                if (finalSize == 0) return new Snapshot<>((E[]) DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA, prevSnap.version + 1, 0);
                return new Snapshot<>(fastRemove(prevArray, index, prevSize, finalSize), prevSnap.version + 1, finalSize/*, true*/);
            }
            return prevSnap;
        }
        @SuppressWarnings("unchecked")
        private static<E> Snapshot<E> removeIf(Predicate<E> removeIf, Snapshot<E> prevSnap) {
            E[] prevArray = prevSnap.copy;
            int prevSize = prevSnap.size;
            int index = findIf(removeIf, prevArray, prevSize);
            if (index != -1) {
                int finalSize = prevSize - 1;
                if (finalSize == 0) return new Snapshot<>((E[]) DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA, prevSnap.version + 1, 0);
                return new Snapshot<>(fastRemove(prevArray, index, prevSize, finalSize), prevSnap.version + 1, finalSize/*, true*/);
            }
            return prevSnap;
        }
        private static<E> E[] fastRemove(E[] prevArray, int i, int prevSize, int newSize) {
            E[] dest_arr = Arrays.copyOf(prevArray, prevSize);
            if ((newSize) > i)
                System.arraycopy(prevArray, i + 1, dest_arr, i, newSize - i);
            dest_arr[newSize] = null;
            return dest_arr;
        }

        private static<S, E> Pair.Immutables.Bool<S> add(AtomicReference<Snapshot<E>> atomicReference, E element, Supplier<S> sSupplier, E[] componentArr) {
            boolean first;
            Snapshot<E> prev, next;
            S snap;
            do {
                prev = atomicReference.get();
                next = Snapshot.add(element, prev, componentArr);
                first = prev.size == 0;
                snap = sSupplier.get();
            } while (!atomicReference.compareAndSet(prev, next));
            return new Pair.Immutables.Bool<>(first, snap);
        }

        private static<E> boolean add(AtomicReference<Snapshot<E>> atomicReference, E element, E[] componentArr) {
            Snapshot<E> prev, next;
            do {
                prev = atomicReference.get();
                next = Snapshot.add(element, prev, componentArr);
            } while (!atomicReference.compareAndSet(prev, next));
            return prev.size == 0;
        }

        private static<T> Snapshot<T> add(T e, Snapshot<T> snapshot, T[] componentArr) {
            T[] nextArray, prevArray = snapshot.copy;
            int nextSize, prevSize = snapshot.size, prevLength = prevArray.length, nextVersion = snapshot.version + 1;

            nextArray = prevSize == 0 ?
                    Arrays.copyOf(componentArr, DEFAULT_CAPACITY)
                    :

                    prevSize == prevLength ?
                            grow(prevSize + 1, prevArray)
                            :
                            Arrays.copyOf(prevArray, prevLength);

            nextArray[prevSize] = e;
            nextSize = prevSize + 1;
            return new Snapshot<>(nextArray, nextVersion, nextSize);
        }
        private static <T> T[] grow(int minCapacity, T[] array) {
            return Arrays.copyOf(array,
                    newCapacity(minCapacity, array));
        }
        private static<T> int newCapacity(int minCapacity, T[] array) {
            // overflow-conscious code
            int oldCapacity = array.length;
            int newCapacity = oldCapacity + (oldCapacity >> 1);
            if (newCapacity - minCapacity <= 0) {
                if (array == DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA)
                    return Math.max(DEFAULT_CAPACITY, minCapacity);
                if (minCapacity < 0) // overflow
                    throw new OutOfMemoryError();
                return minCapacity;
            }
            return (newCapacity - MAX_ARRAY_SIZE <= 0)
                    ? newCapacity
                    : hugeCapacity(minCapacity);
        }
        private static int hugeCapacity(int minCapacity) {
            if (minCapacity < 0) // overflow
                throw new OutOfMemoryError();
            return (minCapacity > MAX_ARRAY_SIZE)
                    ? Integer.MAX_VALUE
                    : MAX_ARRAY_SIZE;
        }

    }

//    public interface SimpleList<E> extends Iterable<E>{
//        boolean add(E element);
//        void add(int index, E element);
//        E get(int index);
//        E set(int index, E element);
//        E remove(int index);
//        boolean remove(E element);
//        void clear();
//        boolean isEmpty();
//        E[] getArray();
//        E[] copy();
////        interface LockFree<E> extends Iterable<E> {
////            default void compute(int index, Consumer<E> eConsumer) {}
////            /**returns true if this is the first item to be added*/
////            boolean add(E element);
////            /**returns true if this is the last item to be removed*/
////            boolean remove(E element);
////
////            boolean removeIf(Predicate<E> removeIf);
//
////            interface Snapshotting<E, Snapshot> extends Iterable<E> {
////                /**returns true if this is the first item to be added*/
////                Pair.Immutables.Bool<Snapshot> snapshotAdd(E element);
////                /**returns true if this is the first item to be added*/
////                boolean add(E element);
////                /**returns true if this is the last item to be removed*/
////                boolean remove(E element);
////                boolean removeIf(Predicate<E> removeIf);
////                boolean isEmpty();
////                E[] copy();
////            }
////
//////            boolean isEmpty();
//////            E[] copy();
////
////            final class Snapshot<E> {
////                final E[] copy;
////                final int version;
////                final int size;
////
////                private static<T> Snapshot<T> initialize(T[] EMPTY_ARRAY) {
////                    return new Snapshot<>(EMPTY_ARRAY, 0, 0);
////                }
////
////                private Snapshot(E[] copy, int version, int size/*, boolean removed*/) {
////                    this.copy = copy;
////                    this.version = version;
////                    this.size = size;
////                }
////                private static<E> int find(E element, E[] prevArray, int prevSize) {
////                    int i = 0;
////                    if (element == null) {
////                        for (; i < prevSize; i++)
////                            if (prevArray[i] == null)
////                                return i;
////                    } else {
////                        for (; i < prevSize; i++)
////                            if (element.equals(prevArray[i]))
////                                return i;
////                    }
////                    return -1;
////                }
////                private static<E> int findIf(Predicate<E> removeIf, E[] prevArray, int prevSize) {
////                    int i = 0;
////                    for (; i < prevSize; i++) {
////                        E toTest = prevArray[i];
////                        if (toTest != null && removeIf.test(toTest))
////                            return i;
////                    }
////                    return -1;
////                }
////                private static final int retries = 3;
////                private static <E> boolean remove(AtomicReference<Snapshot<E>> atomicReference, E element) {
////                    //When a rapid succession of actions are executed, where an "add" followed by a "remove" is performed && the Collection has 0 items
////                    //The remove may arrive before the add,
////                    // And the final state will result in an item being added and size being 1, when it should have been 0.
////                    // to prevent a wrong final state we must retry until the add arrives.
////
////                    int i = 0;
////
////                    Snapshot<E> prev = atomicReference.get(),
////                            next = remove(element, prev);
////                    boolean wasLast = prev.size == 1 && next != prev,
////                            zeroSize = prev.size <= 0, shouldRetry;
////
////                    for (boolean same = true;;i++) {
////                        if (!same) {
////                            zeroSize = prev.size <= 0;
////                        }
////                        shouldRetry = zeroSize && i <= retries;
////                        if (!zeroSize && !same) {
////                            next = remove(element, prev);
////                            wasLast = prev.size == 1 && next != prev;
////                        }
////                        //If the item is not found after all retries, then next remains the same as prev and wasLast == false.
////                        if (!shouldRetry && atomicReference.compareAndSet(prev, next)) return wasLast;
////                        if (same == (same = (prev == (prev = atomicReference.get()))) && !shouldRetry) return false;
////                    }
////
////                }
////                private static <E> boolean removeIf(AtomicReference<Snapshot<E>> atomicReference, Predicate<E> removeIf) {
////                    boolean wasLast;
////                    Snapshot<E> prev, next;
////                    do {
////                        prev = atomicReference.get();
////                        next = removeIf(removeIf, prev);
////                        wasLast = prev.size == 1 && next != prev;
////                    } while (!atomicReference.compareAndSet(prev, next));
////                    return wasLast;
////                }
////                private static <S, E> Pair.Immutables.Bool<S> remove(AtomicReference<Snapshot<E>> atomicReference, E element, Supplier<S> sSupplier) {
////                    boolean wasLast;
////                    Snapshot<E> prev, next;
////                    S snap;
////                    do {
////                        prev = atomicReference.get();
////                        next = remove(element, prev);
////                        wasLast = prev.size == 1 && next != prev;
////                        snap = sSupplier.get();
////                    } while (!atomicReference.compareAndSet(prev, next));
////                    return new Pair.Immutables.Bool<>(wasLast, snap);
////                }
////                @SuppressWarnings("unchecked")
////                private static<E> Snapshot<E> remove(E element, Snapshot<E> prevSnap) {
////                    E[] prevArray = prevSnap.copy;
////                    int prevSize = prevSnap.size;
////                    int index = find(element, prevArray, prevSize);
////                    if (index != -1) {
////                        int finalSize = prevSize - 1;
////                        if (finalSize == 0) return new Snapshot<>((E[]) DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA, prevSnap.version + 1, 0);
////                        return new Snapshot<>(fastRemove(prevArray, index, prevSize, finalSize), prevSnap.version + 1, finalSize/*, true*/);
////                    }
////                    return prevSnap;
////                }
////                @SuppressWarnings("unchecked")
////                private static<E> Snapshot<E> removeIf(Predicate<E> removeIf, Snapshot<E> prevSnap) {
////                    E[] prevArray = prevSnap.copy;
////                    int prevSize = prevSnap.size;
////                    int index = findIf(removeIf, prevArray, prevSize);
////                    if (index != -1) {
////                        int finalSize = prevSize - 1;
////                        if (finalSize == 0) return new Snapshot<>((E[]) DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA, prevSnap.version + 1, 0);
////                        return new Snapshot<>(fastRemove(prevArray, index, prevSize, finalSize), prevSnap.version + 1, finalSize/*, true*/);
////                    }
////                    return prevSnap;
////                }
////                private static<E> E[] fastRemove(E[] prevArray, int i, int prevSize, int newSize) {
////                    E[] dest_arr = Arrays.copyOf(prevArray, prevSize);
////                    if ((newSize) > i)
////                        System.arraycopy(prevArray, i + 1, dest_arr, i, newSize - i);
////                    dest_arr[newSize] = null;
////                    return dest_arr;
////                }
////
////                private static<S, E> Pair.Immutables.Bool<S> add(AtomicReference<Snapshot<E>> atomicReference, E element, Supplier<S> sSupplier, E[] componentArr) {
////                    boolean first;
////                    Snapshot<E> prev, next;
////                    S snap;
////                    do {
////                        prev = atomicReference.get();
////                        next = Snapshot.add(element, prev, componentArr);
////                        first = prev.size == 0;
////                        snap = sSupplier.get();
////                    } while (!atomicReference.compareAndSet(prev, next));
////                    return new Pair.Immutables.Bool<>(first, snap);
////                }
////
////                private static<S, E> boolean add(AtomicReference<Snapshot<E>> atomicReference, E element, E[] componentArr) {
////                    Snapshot<E> prev, next;
////                    do {
////                        prev = atomicReference.get();
////                        next = Snapshot.add(element, prev, componentArr);
////                    } while (!atomicReference.compareAndSet(prev, next));
////                    return prev.size == 0;
////                }
////
////                private static<T> Snapshot<T> add(T e, Snapshot<T> snapshot, T[] componentArr) {
////                    T[] nextArray, prevArray = snapshot.copy;
////                    int nextSize, prevSize = snapshot.size, prevLength = prevArray.length, nextVersion = snapshot.version + 1;
////
////                    nextArray = prevSize == 0 ?
////                            Arrays.copyOf(componentArr, DEFAULT_CAPACITY)
////                            :
////
////                    prevSize == prevLength ?
////                            grow(prevSize + 1, prevArray)
////                            :
////                            Arrays.copyOf(prevArray, prevLength);
////
////                    nextArray[prevSize] = e;
////                    nextSize = prevSize + 1;
////                    return new Snapshot<>(nextArray, nextVersion, nextSize);
////                }
////                private static <T> T[] grow(int minCapacity, T[] array) {
////                    return Arrays.copyOf(array,
////                            newCapacity(minCapacity, array));
////                }
////                private static<T> int newCapacity(int minCapacity, T[] array) {
////                    // overflow-conscious code
////                    int oldCapacity = array.length;
////                    int newCapacity = oldCapacity + (oldCapacity >> 1);
////                    if (newCapacity - minCapacity <= 0) {
////                        if (array == DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA)
////                            return Math.max(DEFAULT_CAPACITY, minCapacity);
////                        if (minCapacity < 0) // overflow
////                            throw new OutOfMemoryError();
////                        return minCapacity;
////                    }
////                    return (newCapacity - MAX_ARRAY_SIZE <= 0)
////                            ? newCapacity
////                            : hugeCapacity(minCapacity);
////                }
////                private static int hugeCapacity(int minCapacity) {
////                    if (minCapacity < 0) // overflow
////                        throw new OutOfMemoryError();
////                    return (minCapacity > MAX_ARRAY_SIZE)
////                            ? Integer.MAX_VALUE
////                            : MAX_ARRAY_SIZE;
////                }
////
////            }
////        }
//    }

    private static final class LockFreeImpl<E, S> implements LockFree<E, S> {
//    private static final class SnapshottingImpl<E, S> implements SimpleList.LockFree.Snapshotting<E, S> {
        private final E[] EMPTY_ELEMENT_ARRAY;

        private final Supplier<S> sSupplier;

        private final AtomicReference<SimpleLists.Snapshot<E>> core;


        @SuppressWarnings("unchecked")
        private LockFreeImpl(Class<? super E> componentType, Supplier<S> sSupplier) {
            this.EMPTY_ELEMENT_ARRAY = (E[]) Array.newInstance(componentType, 0);
            SimpleLists.Snapshot<E> FIRST = SimpleLists.Snapshot.initialize((E[])DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA);
            this.core = new AtomicReference<>(FIRST);
            this.sSupplier = sSupplier;
        }

        @SuppressWarnings("unchecked")
        private LockFreeImpl(Class<? super E> componentType) {
            this.EMPTY_ELEMENT_ARRAY = (E[]) Array.newInstance(componentType, 0);
            SimpleLists.Snapshot<E> FIRST = SimpleLists.Snapshot.initialize((E[])DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA);
            this.core = new AtomicReference<>(FIRST);
            this.sSupplier = () -> null;
        }

        @Override
        public Pair.Immutables.Bool<S> snapshotAdd(E element) {
            return SimpleLists.Snapshot.add(core, element, sSupplier, EMPTY_ELEMENT_ARRAY);
        }

        @Override
        public boolean add(E element) {
            return SimpleLists.Snapshot.add(core, element, EMPTY_ELEMENT_ARRAY);
        }

        @Override
        public boolean remove(E element) {
            return SimpleLists.Snapshot.remove(core, element);
        }

        @Override
        public boolean removeIf(Predicate<E> removeIf) {
            return SimpleLists.Snapshot.removeIf(core, removeIf);
        }

        @Override
        public boolean isEmpty() {
            return core.get().size == 0;
        }

        @Override
        public E[] copy() {
            SimpleLists.Snapshot<E> snapshot = core.get();
            E[] res = snapshot.copy;
            return res == DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA ? EMPTY_ELEMENT_ARRAY : trimToSize(snapshot);
        }

        public E[] trimToSize(SimpleLists.Snapshot<E> snap) {
            E[] copy = snap.copy;
            int size = snap.size;
            if (size < copy.length) return Arrays.copyOf(copy, size);
            else return copy;
        }

        @Override
        public Iterator<E> iterator() {
            return new ArrayIterator<>(copy());
        }
    }

    private static final class ArrayIterator<T> implements Iterator<T> {

        private int index = 0;
        private final int lastIndex;
        private final T[] copy;

        public ArrayIterator(T[] copy) {
            this.copy = copy;
            lastIndex = copy.length - 1;
        }

        @Override
        public boolean hasNext() {
            return index < lastIndex;
        }

        @Override
        public T next() {
            return copy[index++];
        }
    }
}
