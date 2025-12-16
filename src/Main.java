import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

public class Main {

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        String imagePath = "4k.jpg";
        Mat image = Imgcodecs.imread(imagePath);

        if (image.empty()) {
            System.err.println("Erreur : Impossible de charger l'image '" + imagePath + "'");
            System.err.println("Vérifiez que le fichier existe dans le répertoire courant.");
            return;
        }

        Mat encrypted = Encryption.encrypt(image, 203, 57);

        //save
        Imgcodecs.imwrite("encrypted_4k.jpg", encrypted);
    }
}
