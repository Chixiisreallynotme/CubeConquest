package fr.chixi.cubeconquest;
import org.junit.jupiter.api.Test;
public class MenuTest {
    @Test
    void dump() throws Exception {
        try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter("menu_methods.txt"))) {
            for (java.lang.reflect.Method m : net.minecraft.world.inventory.AbstractContainerMenu.class.getMethods()) {
                out.println(m.toString());
            }
        }
    }
}
