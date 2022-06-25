package sendero.interfaces;

@FunctionalInterface
public interface ActivationListener {
    void onStateChange(boolean isActive);
}
