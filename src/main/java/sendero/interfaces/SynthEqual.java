package sendero.interfaces;

import java.util.function.Predicate;

public interface SynthEqual {
    static int hashCodeOf(int... hashCodes) {
        if (hashCodes == null)
            return 0;

        int result = 1;

        for (int hashes : hashCodes)
            result = 31 * result + hashes;

        return result;
    }

    @SuppressWarnings("unchecked")
    default <S> boolean expect(int at, Predicate<S> that) {
        return that.test((S) paramAt(this, at));
    }


    default <S> boolean equalTo(int at, S arg) {
        return paramAt(this, at).equals(arg);
    }

    default<S> boolean equalTo(int at, SynthEqual that) {
        return that != null && equalTo(at, paramAt(that, at));
    }

    default int hashAt(int at) {
        return paramAt(this, at).hashCode();
    }

    static Object paramAt(SynthEqual self, int at) {
        try {
            return self.getClass().getDeclaredFields()[at].get(self);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
