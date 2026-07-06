import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class DumpClass {
    public static void main(String[] args) throws Exception {
        String jarPath = "C:/Users/Chixi/.gradle/caches/fabric-loom/1.17.13/minecraftMaven/net/minecraft/minecraft-project/@local/minecraft-project-@local-sources.jar";
        try (ZipFile zipFile = new ZipFile(jarPath)) {
            ZipEntry entry = zipFile.getEntry("net/minecraft/client/renderer/item/properties/numeric/CompassItemPropertyFunction.java");
            if (entry != null) {
                try (InputStream is = zipFile.getInputStream(entry)) {
                    byte[] data = is.readAllBytes();
                    System.out.println(new String(data));
                }
            } else {
                System.out.println("Entry not found!");
            }
        }
    }
}
