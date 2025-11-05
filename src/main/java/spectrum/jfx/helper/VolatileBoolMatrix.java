package spectrum.jfx.helper;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VolatileBoolMatrix {
    // VarHandle для элемента массива byte[]
    private static final VarHandle BYTE_ELEM =
            MethodHandles.arrayElementVarHandle(boolean[].class);

    private final boolean[][] a; // финальное поле — безопасно публикуем матрицу

    public VolatileBoolMatrix(int rows, int cols) {
        this.a = new boolean[rows][cols];
    }

    public boolean get(int i, int j) {
        return (boolean) BYTE_ELEM.getVolatile(a[i], j);
    }

    public void set(int i, int j, boolean v) {
        BYTE_ELEM.setVolatile(a[i], j, v);
    }

    public boolean compareAndSet(int i, int j, boolean expect, boolean update) {
        return BYTE_ELEM.compareAndSet(a[i], j, expect, update);
    }

}
