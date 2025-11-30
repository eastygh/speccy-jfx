package spectrum.jfx.machine;

import lombok.experimental.UtilityClass;
import spectrum.jfx.hardware.machine.HardwareProvider;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@UtilityClass
public class Machine {

    private final AtomicReference<HardwareProvider> hardwareProvider = new AtomicReference<>();

    public HardwareProvider getHardwareProvider() {
        return hardwareProvider.get();
    }

    public void setHardwareProvider(HardwareProvider provider) {
        hardwareProvider.set(provider);
    }

    public void withHardwareProvider(Consumer<HardwareProvider> consumer) {
        withHardwareProvider(consumer, () -> {
        });
    }

    public void withHardwareProvider(Consumer<HardwareProvider> consumer, Runnable onNotFound) {
        HardwareProvider provider = hardwareProvider.get();
        if (provider != null) {
            consumer.accept(provider);
        } else {
            onNotFound.run();
        }
    }

}
