import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DumpCompass {
    public static void main(String[] args) throws Exception {
        String jarPath = "C:/Users/Chixi/.gradle/caches/fabric-loom/26.2/minecraft-client.jar";
        try (ZipFile zipFile = new ZipFile(jarPath)) {
            ZipEntry entry = zipFile.getEntry("assets/minecraft/models/item/compass.json");
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
