package cc.silk.module.modules.combat;

public class HitboxHelper {
    private static final ThreadLocal<Boolean> isRaycasting = ThreadLocal.withInitial(() -> false);

    public static void setRaycasting(boolean value) {
        isRaycasting.set(value);
    }

    public static boolean isRaycasting() {
        return isRaycasting.get();
    }
}
