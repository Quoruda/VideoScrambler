import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class Encryption {

    /**
     * Chiffre une image en mélangeant les lignes selon le principe de permutation par itérations.
     *
     * @param input Image d'entrée (Mat)
     * @param r Décalage (offset) codé sur 8 bits (0-255)
     * @param s Pas (step) codé sur 7 bits (0-127)
     * @return Image chiffrée (Mat)
     */
    public static Mat encrypt(Mat input, int r, int s) {
        if (input == null || input.empty()) {
            return input;
        }

        // Optimisation : si r=0 et s=0, pas de mélange nécessaire
        if (r == 0 && s == 0) {
            return input.clone();
        }

        int height = input.rows();
        int width = input.cols();

        // Créer une image de sortie
        Mat output = new Mat(height, width, input.type());

        // Précalculer (2*s+1) pour éviter de le recalculer à chaque fois
        int step = 2 * s + 1;

        // Traiter par itérations (blocs de puissances de 2)
        int startLine = 0;

        while (startLine < height) {
            // Calculer la taille du bloc : plus grande puissance de 2 <= lignes restantes
            int remainingLines = height - startLine;
            int blockSize = largestPowerOf2(remainingLines);

            // Mélanger les lignes dans ce bloc
            scrambleBlockOptimized(input, output, startLine, blockSize, r, step);

            // Passer au bloc suivant
            startLine += blockSize;
        }

        return output;
    }

    /**
     * Trouve la plus grande puissance de 2 inférieure ou égale à n.
     *
     * @param n Nombre
     * @return Plus grande puissance de 2 <= n
     */
    private static int largestPowerOf2(int n) {
        if (n <= 0) return 0;
        if (n == 1) return 1;

        // Trouver la position du bit le plus significatif
        int power = 1;
        while (power * 2 <= n) {
            power *= 2;
        }
        return power;
    }

    /**
     * Déchiffre une image en inversant le mélange des lignes.
     *
     * @param input Image chiffrée (Mat)
     * @param r Décalage (offset) codé sur 8 bits (0-255)
     * @param s Pas (step) codé sur 7 bits (0-127)
     * @return Image déchiffrée (Mat)
     */
    public static Mat decrypt(Mat input, int r, int s) {
        if (input == null || input.empty()) {
            return input;
        }

        // Optimisation : si r=0 et s=0, pas de démélange nécessaire
        if (r == 0 && s == 0) {
            return input.clone();
        }

        int height = input.rows();
        int width = input.cols();

        // Créer une image de sortie
        Mat output = new Mat(height, width, input.type());

        // Précalculer (2*s+1) pour éviter de le recalculer à chaque fois
        int step = 2 * s + 1;

        // Traiter par itérations (blocs de puissances de 2)
        int startLine = 0;

        while (startLine < height) {
            // Calculer la taille du bloc : plus grande puissance de 2 <= lignes restantes
            int remainingLines = height - startLine;
            int blockSize = largestPowerOf2(remainingLines);

            // Démélanger les lignes dans ce bloc (inversion)
            unscrambleBlockOptimized(input, output, startLine, blockSize, r, step);

            // Passer au bloc suivant
            startLine += blockSize;
        }

        return output;
    }

    /**
     * Mélange un bloc de lignes selon la formule : ((r + (2s+1)*idLigne) % size).
     *
     * @param input Image source
     * @param output Image destination
     * @param startLine Indice de la première ligne du bloc
     * @param size Taille du bloc (puissance de 2)
     * @param r Décalage (offset)
     * @param s Pas (step)
     */
    private static void scrambleBlock(Mat input, Mat output, int startLine, int size, int r, int s) {
        for (int idLigne = 0; idLigne < size; idLigne++) {
            // Calculer la position de destination selon la formule
            int destPos = (r + (2 * s + 1) * idLigne) % size;

            // Copier la ligne source vers la position de destination
            int sourceLine = startLine + idLigne;
            int destLine = startLine + destPos;

            // Copier la ligne
            Mat rowSrc = input.row(sourceLine);
            Mat rowDst = output.row(destLine);
            rowSrc.copyTo(rowDst);
            rowSrc.release();
            rowDst.release();
        }
    }

    /**
     * Version optimisée de scrambleBlock avec step précalculé.
     */
    private static void scrambleBlockOptimized(Mat input, Mat output, int startLine, int size, int r, int step) {
        for (int idLigne = 0; idLigne < size; idLigne++) {
            // Calculer la position de destination selon la formule
            int destPos = (r + step * idLigne) % size;

            // Copier la ligne source vers la position de destination
            int sourceLine = startLine + idLigne;
            int destLine = startLine + destPos;

            // Copier la ligne et libérer les vues
            Mat rowSrc = input.row(sourceLine);
            Mat rowDst = output.row(destLine);
            rowSrc.copyTo(rowDst);
            rowSrc.release();
            rowDst.release();
        }
    }

    /**
     * Démélange un bloc de lignes (inverse du scramble).
     *
     * @param input Image source (chiffrée)
     * @param output Image destination (déchiffrée)
     * @param startLine Indice de la première ligne du bloc
     * @param size Taille du bloc (puissance de 2)
     * @param r Décalage (offset)
     * @param s Pas (step)
     */
    private static void unscrambleBlock(Mat input, Mat output, int startLine, int size, int r, int s) {
        for (int idLigne = 0; idLigne < size; idLigne++) {
            // Calculer la position source selon la formule
            int sourcePos = (r + (2 * s + 1) * idLigne) % size;

            // Copier la ligne source vers la position originale
            int sourceLine = startLine + sourcePos;
            int destLine = startLine + idLigne;

            // Copier la ligne
            Mat rowSrc = input.row(sourceLine);
            Mat rowDst = output.row(destLine);
            rowSrc.copyTo(rowDst);
            rowSrc.release();
            rowDst.release();
        }
    }

    /**
     * Version optimisée de unscrambleBlock avec step précalculé.
     */
    private static void unscrambleBlockOptimized(Mat input, Mat output, int startLine, int size, int r, int step) {
        for (int idLigne = 0; idLigne < size; idLigne++) {
            // Calculer la position source selon la formule
            int sourcePos = (r + step * idLigne) % size;

            // Copier la ligne source vers la position originale
            int sourceLine = startLine + sourcePos;
            int destLine = startLine + idLigne;

            // Copier la ligne et libérer les vues
            Mat rowSrc = input.row(sourceLine);
            Mat rowDst = output.row(destLine);
            rowSrc.copyTo(rowDst);
            rowSrc.release();
            rowDst.release();
        }
    }

    private static double pearson(byte[] x, byte[] y) {
        int n = x.length;

        double sumX = 0, sumY = 0;
        double sumX2 = 0, sumY2 = 0;
        double sumXY = 0;

        for (int i = 0; i < n; i++) {
            // Convertir byte en unsigned int (0-255)
            int xi = x[i] & 0xFF;
            int yi = y[i] & 0xFF;

            sumX += xi;
            sumY += yi;
            sumX2 += xi * xi;
            sumY2 += yi * yi;
            sumXY += xi * yi;
        }

        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));

        if (denominator == 0) return 0;
        return numerator / denominator;
    }


    private static double getQuality(Mat mat) {
        int rows = mat.rows();
        if (rows < 2) return 0.0;

        int size = (int) (mat.cols() * mat.elemSize());
        byte[] x = new byte[size];
        byte[] y = new byte[size];

        double sum = 0.0;
        int count = 0;

        int maxRows = rows;

        for (int i = 1; i < maxRows; i++) {
            Mat rowPrev = null;
            Mat rowCurr = null;
            try {
                rowPrev = mat.row(i - 1);
                rowCurr = mat.row(i);
                rowPrev.get(0, 0, x);
                rowCurr.get(0, 0, y);
                sum += pearson(x, y);
                count++;
            } finally {
                // Libérer les vues de lignes
                if (rowPrev != null) rowPrev.release();
                if (rowCurr != null) rowCurr.release();
            }
        }

        return count > 0 ? sum / count : 0.0;
    }

    private static void findKeyProcess(HashMap<Key, Double> map, Key key, Mat frame) {
        Mat decrypted_frame = null;
        Mat bw_frame = null;
        decrypted_frame = decrypt(frame, key.getR(), key.getS());
        bw_frame = new Mat();

        Imgproc.cvtColor(decrypted_frame, bw_frame, Imgproc.COLOR_BGR2GRAY);

        double quality = getQuality(bw_frame);
        map.put(key, quality);

        bw_frame.release();
        decrypted_frame.release();
    }


    /**
     * Trouve la clé (r, s) qui maximise la qualité de l'image déchiffrée.
     *
     * @param frame Image chiffrée (Mat)
     * @return Clé optimale (Key)
     */
    public static Key findKey(Mat frame) {
        HashMap<Key, Double> keysQualityHashmap = new HashMap<>();


        for (int s = 0; s < 128; s+=1) {
            for (int r = 1; r < 256; r+=64) {
                findKeyProcess(keysQualityHashmap, new Key(r, s), frame);
            }
        }
        // s_median
        ArrayList<Key> keys = new ArrayList<Key>(keysQualityHashmap.keySet());
        keys.sort(Comparator.comparingDouble(keysQualityHashmap::get));
        Collections.reverse(keys);

        int s = keys.getFirst().getS();

        keysQualityHashmap.clear();
        for(int r = 0; r < 256; r+=1) {
            findKeyProcess(keysQualityHashmap, new Key(r, s), frame);
        }
        keys = new ArrayList<Key>(keysQualityHashmap.keySet());
        keys.sort(Comparator.comparingDouble(keysQualityHashmap::get));
        Collections.reverse(keys);

        return keys.getFirst();
    }

}
