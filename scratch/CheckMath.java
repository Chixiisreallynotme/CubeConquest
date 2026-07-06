public class CheckMath {
    public static void main(String[] args) {
        System.out.println("East (+X): " + Math.toDegrees(Math.atan2(-10, 0)));
        System.out.println("West (-X): " + Math.toDegrees(Math.atan2(10, 0)));
        System.out.println("South (+Z): " + Math.toDegrees(Math.atan2(0, 10)));
        System.out.println("North (-Z): " + Math.toDegrees(Math.atan2(0, -10)));
    }
}
