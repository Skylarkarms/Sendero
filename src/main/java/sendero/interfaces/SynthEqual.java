package sendero.interfaces;

public interface SynthEqual {
    static int hashCodeOf(int... hashCodes) {
        if (hashCodes == null)
            return 0;

        int result = 1;

        for (int hashes : hashCodes)
            result = 31 * result + hashes;

        return result;
    }

    default <S> boolean equalTo(int at, S arg) {
        return paramAt(this, at).equals(arg);
    }

    default<S> boolean equalTo(int at, SynthEqual that) {
        return that != null && paramAt(this, at).equals(paramAt(that, at));
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
