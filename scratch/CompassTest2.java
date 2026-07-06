public class CompassTest2 {
    public static void main(String[] args) {
        test(0, 1); // target South
        test(1, 0); // target East
        test(0, -1); // target North
        test(-1, 0); // target West
    }
    public static void test(double dx, double dz) {
        double yaw = 90; // facing West
        double vanillaAtan = Math.atan2(dz, dx) / (2 * Math.PI);
        double vanillaAngle = 0.5 - (yaw/360.0 - 0.25 - vanillaAtan);
        vanillaAngle = (vanillaAngle % 1.0 + 1.0) % 1.0;
        
        double targetAngleDeg = Math.toDegrees(Math.atan2(-dx, dz));
        double relAngle = ((targetAngleDeg - yaw) % 360 + 360) % 360;
        double ourAngle = relAngle / 360.0;
        
        System.out.println("dx=" + dx + ", dz=" + dz + " -> vanilla=" + vanillaAngle + " our=" + ourAngle);
    }
}
