package ProactiveSupplierTest;

import sendero.AtomicBinaryEventConsumer;
import sendero.ProactiveSupplier;
import sendero.ProactiveSuppliers;
import sendero.switchers.Switchers;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class LifeCycledViewModel extends AtomicBinaryEventConsumer implements Supplier<SwitchSwitchMapTest.Result> {

    ProactiveSupplier<SwitchSwitchMapTest.Result> resultSupp = ProactiveSuppliers.Bound.bound(
            SwitchSwitchMapTest.getResult()
    );

    @Override
    protected void onStateChange(boolean isActive) {
        if (isActive) resultSupp.start();
        else resultSupp.shutoff();
    }

    @Override
    public SwitchSwitchMapTest.Result get() {
        return resultSupp.get();
    }
}
