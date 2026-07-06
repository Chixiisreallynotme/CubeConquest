package fr.chixi.cubeconquest;
import org.junit.jupiter.api.Test;
public class EntityDumpTest {
    @Test
    void dump() throws Exception {
        try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter("entity_methods.txt"))) {
            for (java.lang.reflect.Method m : net.minecraft.world.entity.Entity.class.getMethods()) {
                if (m.getName().toLowerCase().contains("rot") || m.getName().toLowerCase().contains("yaw")) {
                    out.println(m.getName());
                }
            }
        }
    }
}
