package sendero.utils;

import sendero.interfaces.StringSupplier;
import sendero.interfaces.ToBooleanFunction;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ObjectUtils {
    public static double[] doubleDefaultArr = new double[0];
    public static long[] longDefaultArr = new long[0];
    public static Long[] objectLongDefaultArr = new Long[0];
    public static double[][] doubleDoubleDefaultArr = new double[0][0];
    private static final IntFunction<long[]> sysLongFun = long[]::new;
    private static final IntFunction<Long[]> sysObjectLongFun = Long[]::new;
    private static final IntFunction<double[][]> sysDoubleDoubleFun = double[][]::new;
    public static final IntFunction<double[][]> doubleDoubleFun = i -> {
        if (i == 0) return doubleDoubleDefaultArr;
        else return sysDoubleDoubleFun.apply(i);
    };
    public static final IntFunction<long[]> longFun = i -> {
        if (i == 0) return longDefaultArr;
        else return sysLongFun.apply(i);
    };
    public static final IntFunction<Long[]> objectLongFun = i -> {
        if (i == 0) return objectLongDefaultArr;
        else return sysObjectLongFun.apply(i);
    };
    public static boolean isDefault(double[][] arr) {
        return arr == doubleDoubleDefaultArr;
    }
    public static boolean isDefault(long[] arr) {
        return arr == longDefaultArr;
    }
    public static boolean isDefault(Long[] arr) {
        return arr == objectLongDefaultArr;
    }

    public static <T> void exist(T t, Consumer<T> action) {
        if (Objects.nonNull(t)) action.accept(t);
    }

    public static <T, S> S testMap(T t, Predicate<T> test, Function<T, S> map) {
        return testGet(() -> test.test(t), () -> map.apply(t));
    }

    public static <T, S> S testMapElse(T t, Predicate<T> test, Function<T, S> map, Function<T, S> elseMap) {
        return testGetElse(test.test(t), () -> map.apply(t), () -> elseMap.apply(t));
    }

    public static <T> T testGet(BooleanSupplier test, Supplier<T> get) {
        if (test.getAsBoolean()) return get.get();
        else return null;
    }

    public static <T> T testGet(boolean test, Supplier<T> get) {
        if (test) return get.get();
        else return null;
    }

    public static <T> T testGetElse(
            boolean test,
            Supplier<T> get, Supplier<T> elseGet) {
        if (test) return get.get();
        else return elseGet.get();
    }

    /**@returns null if t == null*/
    public static <T, S> S map(T t, Function<T, S> map) {
        return testMap(t, Objects::nonNull, map);
    }

    public static class TypeToken {
        private final Object object;
        public final Type type;

        public TypeToken(Object object) {
            this.object = object;
            type = Type.typeOf(object);
        }

        public<T> T get() {
            return type.cast(object);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TypeToken typeToken = (TypeToken) o;
            return Objects.equals(object, typeToken.object) && type == typeToken.type;
        }

        public boolean equalToObject(Object object) {
            boolean thisNull = this.object == null;
            if (thisNull && object == null) return true;
            return !thisNull && this.object.equals(object);
        }

        @Override
        public int hashCode() {
            return Objects.hash(object, type);
        }
    }
    public enum Type {
        type_null(Void.class),
        type_long(Long.class),
        type_int(Integer.class),
        type_float(Float.class),
        type_string(String.class),
        type_other(Object.class);

        private final Class<?> aClass;

        <T> Type(Class<T> aClass) {
            this.aClass = aClass;
        }

        public Class<?> getTypeClass() {
            return aClass;
        }

        public static Type typeOf(Object o) {
            if (o == null) return type_null;
            Class<?> aClass = o.getClass();
            if (Long.class.equals(aClass)) {
                return type_long;
            } else if (Float.class.equals(aClass)) {
                return type_float;
            } else if (Integer.class.equals(aClass)) {
                return type_int;
            } else if (String.class.equals(aClass)) {
                return type_string;
            }
            else return type_other;
        }

        @SuppressWarnings("unchecked")
        public<T> T cast(Object o) {
            return (T) aClass.cast(o);
        }
    }

    public static<T> long unboxLong(Long val) {
        assert nonNull(val);
        return val;
    }

    public static<T> T getNonNull(T val, String message) {
        assert nonNull(val) : message;
        return val;
    }

    private static <T> boolean nonNull(T val) {
        return Objects.nonNull(val);
    }

    public static<T> T getNonNull(T val) {
        assert nonNull(val) : "Value was null";
        return val;
    }
    public static void assertNonNull(Object val) {
        assert nonNull(val) : "Value was null";
    }
    public static void assertNonNull(Object val, String message) {
        assert nonNull(val) : message;
    }
    public static<T> T getNonNull(T val, StringSupplier message) {
        assert nonNull(val) : message.get();
        return val;
    }
    public static<T> T initialize(Class<T> aClass) {
        try {
            final Constructor<T> c = aClass.getDeclaredConstructor();
            return c.newInstance();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
    public static void assertNull(Object val, StringSupplier message) {
        assert val == null : message.get();
    }
    public static void assertNull(Object val, String message) {
        assert val == null : message;
    }
    public static<T> T lazyAssert(T t, Predicate<T> expect, StringSupplier message) {
        assert expect.test(t) : message.get();
        return t;
    }
    public static<T> void lazyAssert(boolean expect, StringSupplier message) {
        assert expect : message.get();
    }

    public static int assertInt(int val, IntPredicate test, String message) {
        assert test.test(val) : message;
        return val;
    }
    public static int assertInt(int val, IntPredicate test) {
        assert test.test(val);
        return val;
    }
    public static long assertLong(long val, LongPredicate test, String message) {
        assert test.test(val) : message;
        return val;
    }
    public static long assertLong(long val, LongPredicate test) {
        assert test.test(val);
        return val;
    }
    @FunctionalInterface
    public interface FloatPredicate {
        boolean test(float val);
    }
    public static float assertFloat(float val, FloatPredicate test) {
        assert test.test(val);
        return val;
    }

    public static double unbox(Double val) {
        assert nonNull(val);
        return val;
    }
    public static int unbox(Integer val) {
        assert nonNull(val);
        return val;
    }
    public static long unbox(Long val) {
        assert nonNull(val);
        return val;
    }
    public static double round(double val, double toNearest) {
        return Math.round(val / toNearest) * toNearest;
    }

    /**
     * System.out.println(scale(10, 1, 2)); // 100.0
     * System.out.println(scale(10, 1, -2)); // 0.01
     * System.out.println(scale(10, 2, 4)); // 20000.0
     * System.out.println(scale(10, 3, -1)); // 0.3
     * */
    public static double scale(double mult, int scale, int by) {
        return mult * Math.pow(scale, by);
    }
    public static double unbox(Double val, String error) {
        assert nonNull(val) : error;
        return val;
    }
    public static double unbox(Double val, StringSupplier error) {
        assert nonNull(val) : error.get();
        return val;
    }
    public static int unbox(Integer val, StringSupplier error) {
        assert nonNull(val) : error.get();
        return val;
    }
    public static double unbox(Double val, double orElse) {
        return nonNull(val) ? val : orElse;
    }
    public static int unbox(Integer val, int orElse) {
        return nonNull(val) ? val : orElse;
    }
    public static long unboxLong(Long val, long orElse) {
        return nonNull(val) ? val : orElse;
    }

//    /**@return false if null*/
//    public static boolean notNull(Boolean aBoolean) {
//        return aBoolean != null;
//    }
    /**@return false if null*/
    public static boolean isFalse(Boolean aBoolean) {
        return nonNull(aBoolean) && !aBoolean;
    }
    /**@return false if null*/
    public static boolean isTrue(Boolean aBoolean) {
        return nonNull(aBoolean) && aBoolean;
    }

    /**Breaks if test == true else false*/
    public static<E> boolean testOR(
            Predicate<E> test,
            E withA,
            E withB,
            E withC
    ) {
        assertNonNull(test);
        return test.test(withA)
                || test.test(withB)
                || test.test(withC);
    }
    /**Breaks if test == true else false*/
    public static<E> boolean testOR(
            Predicate<E> test,
            E withA,
            E withB
    ) {
        assertNonNull(test);
        return test.test(withA)
                || test.test(withB);
    }

    @SafeVarargs
    public static<T, R> Optional<R> when(
            T t,
            Map.Entry<Predicate<T>, Supplier<R>>... entries
    ) {
        for (Map.Entry<Predicate<T>, Supplier<R>> e:entries
             ) {
            if (e.getKey().test(t)) return Optional.of(e.getValue().get());
        }
        return Optional.empty();
    }

    public static final class Caze {
        private final ToBooleanFunction<Object> tryConsumer;

        public static<T> Caze get(
                Class<T> test, Consumer<T> runnable
        ) {
            return new Caze(test, runnable);
        }
        private<T> Caze(Class<T> test, Consumer<T> consumer) {
            this.tryConsumer = o -> {
                if (test.isInstance(o)) {
                    consumer.accept(test.cast(o));
                    return true;
                }
                return false;
            };
        }

        boolean tryConsume(Object o) {
            return this.tryConsumer.asBoolean(o);
        }
    }

    public static<T> boolean autoCast(
            T t,
            Caze... entries
    ) {
        for (Caze e:entries) if (e.tryConsume(t)) return true;
        return false;
    }

    public static<E> boolean refMatch(E toMatch, E withA, E withB) {
        return testOR(e -> e == toMatch, withA, withB);
    }

    public void assertImmutable(Object object) {
        assert isImmutable(object) : "Object " + object + " not immutable";
    }

    static boolean isImmutable(Object value) {
        if (value == null) {
            return true; // null is immutable
        }
        Class<?> clazz = value.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isFinal(field.getModifiers())) {
                return false; // has non-final field
            }
        }
        return true; // no non-final fields found
    }
}
