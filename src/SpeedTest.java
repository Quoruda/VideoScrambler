import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

public class SpeedTest {

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        String imagePath = "inputs/fhd.jpg";
        int iterations_crypt = 1000;
        int iterations_decrypt = 1000;
        int iterations_key_finding = 100;
        int iterations_smart_key_finding = 1000;

        Mat image = Imgcodecs.imread(imagePath);

        // Vérifier que l'image a bien été chargée
        if (image.empty()) {
            System.err.println("Erreur : Impossible de charger l'image '" + imagePath + "'");
            System.err.println("Vérifiez que le fichier existe dans le répertoire courant.");
            return;
        }

        System.out.println("Image chargée avec succès : " + image.rows() + "x" + image.cols() + " pixels");
        int r = 5;
        int s = 10;
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations_crypt; i++) {
            Encryption.encrypt(image, r, s);
        }
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        double averageTimePerEncryption = (double) duration / iterations_crypt;
        double averageTimePerEncryptionMs = averageTimePerEncryption / 1_000_000;
        System.out.println("Average time per encryption: " + String.format("%.4f", averageTimePerEncryptionMs) + " ms (" + String.format("%.2f", averageTimePerEncryption) + " ns)");

        startTime = System.nanoTime();
        for (int i = 0; i < iterations_decrypt; i++) {
            Encryption.decrypt(image, r, s);
        }
        endTime = System.nanoTime();
        duration = endTime - startTime;
        double averageTimePerDecryption = (double) duration / iterations_decrypt;
        double averageTimePerDecryptionMs = averageTimePerDecryption / 1_000_000;
        System.out.println("Average time per decryption: " + String.format("%.4f", averageTimePerDecryptionMs) + " ms (" + String.format("%.2f", averageTimePerDecryption) + " ns)");

        startTime = System.nanoTime();
        for (int i = 0; i < iterations_key_finding; i++) {
            Encryption.bruteForceCrack(image);
        }
        endTime = System.nanoTime();
        duration = endTime - startTime;
        double averageTimePerKeyFinding = (double) duration / iterations_key_finding;
        double averageTimePerKeyFindingMs = averageTimePerKeyFinding / 1_000_000;
        System.out.println("Average time per key finding: " + String.format("%.4f", averageTimePerKeyFindingMs) + " ms (" + String.format("%.2f", averageTimePerKeyFinding) + " ns)");


        //Smart Key Finding
        startTime = System.nanoTime();
        for (int i = 0; i < iterations_smart_key_finding; i++) {
            Encryption.smartCrack(image);
        }
        endTime = System.nanoTime();
        duration = endTime - startTime;
        double averageTimePerSmartKeyFinding = (double) duration / iterations_smart_key_finding;
        double averageTimePerSmartKeyFindingMs = averageTimePerSmartKeyFinding / 1_000_000;
        System.out.println("Average time per smart key finding: " + String.format("%.4f", averageTimePerSmartKeyFindingMs) + " ms (" + String.format("%.2f", averageTimePerSmartKeyFinding) + " ns)");
    }



}
