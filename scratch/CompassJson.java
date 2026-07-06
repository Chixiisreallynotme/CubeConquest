import java.nio.file.*;
import java.util.zip.*;
import java.io.*;

public class CompassJson {
    public static void main(String[] args) throws Exception {
        String jarPath = "C:/Users/Chixi/.gradle/caches/fabric-loom/1.17.13/minecraftMaven/net/minecraft/minecraft-project/@local/net.fabricmc.yarn.1_20_4.1.20.4+build.3-v2/minecraft-project-@local-net.fabricmc.yarn.1_20_4.1.20.4+build.3-v2.jar";
        try (ZipFile zip = new ZipFile(jarPath)) {
            ZipEntry entry = zip.getEntry("assets/minecraft/models/item/compass.json");
            if (entry != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            } else {
                System.out.println("Entry not found");
            }
        }
    }
}
