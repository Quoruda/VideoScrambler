import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import javax.swing.text.Position;
import java.util.*;


public class Encryption {

    public static ArrayList<Point> getPositionsForDynamicEncryption(int height, int width, int k) {
        SplittableRandom random = new SplittableRandom(k);
        ArrayList<Point> positions = new ArrayList<>();

        int x = random.nextInt(width);
        int y = random.nextInt(height);

        while (positions.size() < 15) {
            while (positions.contains(new Point(x, y))) {
                x = random.nextInt(width);
                y = random.nextInt(height);
            }
            positions.add(new Point(x, y));
        }

        return positions;
    }

    public static int largestPowerOf2(int n) {
        if (n <= 0) return 0;
        if (n == 1) return 1;

        int power = 1;
        while (power * 2 <= n) {
            power *= 2;
        }
        return power;
    }

    public static Mat encrypt(Mat input, int r, int s) {
        if (input == null || input.empty()) return input;
        if (r == 0 && s == 0) return input.clone();

        int height = input.rows();
        int width = input.cols();
        int channels = input.channels();

        int rowSize = width * channels;
        int totalSize = height * rowSize;
        byte[] sourceData = new byte[totalSize];
        byte[] destData = new byte[totalSize];

        input.get(0, 0, sourceData);

        int step = 2 * s + 1;
        int startLine = 0;

        int remainingLines, blockSize, blockMask, destIndexInBlock, sourceRow, destRow;

        while (startLine < height) {
            remainingLines = height - startLine;
            blockSize = largestPowerOf2(remainingLines);

            blockMask = blockSize - 1;

            for (int i = 0; i < blockSize; i++) {
                destIndexInBlock = (r + step * i) & blockMask;

                sourceRow = startLine + i;
                destRow = startLine + destIndexInBlock;

                System.arraycopy(sourceData, sourceRow * rowSize,
                        destData, destRow * rowSize,
                        rowSize);
            }
            startLine += blockSize;
        }

        Mat output = new Mat(height, width, input.type());
        output.put(0, 0, destData);

        return output;
    }

    public static Mat decrypt(Mat input, int r, int s) {
        if (input == null || input.empty()) return input;
        if (r == 0 && s == 0) return input.clone();

        int height = input.rows();
        int width = input.cols();
        int channels = input.channels();

        int rowSize = width * channels;
        int totalSize = height * rowSize;
        byte[] sourceData = new byte[totalSize];
        byte[] destData = new byte[totalSize];

        input.get(0, 0, sourceData);

        int step = 2 * s + 1;
        int startLine = 0;

        int remainingLines, blockSize, blockMask, sourceIndexInBlock, sourceRow, destRow;

        while (startLine < height) {
            remainingLines = height - startLine;
            blockSize = largestPowerOf2(remainingLines);
            blockMask = blockSize - 1;

            for (int i = 0; i < blockSize; i++) {
                // Calcul inverse : on trouve où était la ligne source
                sourceIndexInBlock = (r + step * i) & blockMask;

                sourceRow = startLine + sourceIndexInBlock; // Ligne chiffrée
                destRow = startLine + i;                    // Ligne originale

                System.arraycopy(sourceData, sourceRow * rowSize,
                        destData, destRow * rowSize,
                        rowSize);
            }
            startLine += blockSize;
        }

        Mat output = new Mat(height, width, input.type());
        output.put(0, 0, destData);

        return output;
    }

    public static Mat dynamicEncrypt(Mat input, int k) {
        int height = input.rows();
        int width = input.cols();
        ArrayList<Point> positions = getPositionsForDynamicEncryption(height, width, k);

        Random random = new Random();
        int r = random.nextInt(256);
        int s = random.nextInt(128);

        Mat encrypted = encrypt(input, r, s);

        boolean[] keyBits = new boolean[15];

        for (int i = 0; i < 8; i++) {
            keyBits[i] = ((r >> i) & 1) == 1;
        }
        for (int i = 0; i < 7; i++) {
            keyBits[8 + i] = ((s >> i) & 1) == 1;
        }

        for (int i = 0; i < 15; i++) {
            Point p = positions.get(i);
            int row = (int) p.y;
            int col = (int) p.x;
            boolean bitToHide = keyBits[i];

            // Lire le pixel (dans un tableau de double ou byte selon l'implémentation OpenCV)
            double[] pixel = encrypted.get(row, col);

            // On ne modifie qu'un seul canal (le Bleu/Gris, souvent l'indice 0)
            int channelIndex = 0;

            // Extraire la valeur entière du canal (0-255)
            // On prend le premier canal (Blue ou Grayscale)
            int channelValue = (int) pixel[channelIndex];

            // Mise à jour de l'LSB :
            // 1. channelValue & 0xFE : Met le LSB à zéro.
            // 2. | (bitToHide ? 1 : 0) : Ajoute le bit de la clé.
            int newValue = (channelValue & 0xFE) | (bitToHide ? 1 : 0);

            // Réinjecter la nouvelle valeur dans le pixel
            pixel[channelIndex] = newValue;

            // Mettre à jour la Mat
            encrypted.put(row, col, pixel);
        }

        return encrypted;
    }

    public static Mat dynamicDecrypt(Mat input, int k) {
        int height = input.rows();
        int width = input.cols();
        ArrayList<Point> positions = getPositionsForDynamicEncryption(height, width, k);

        boolean[] keyBits = new boolean[15];

        for (int i = 0; i < 15; i++) {
            Point p = positions.get(i);
            int row = (int) p.y;
            int col = (int) p.x;

            double[] pixel = input.get(row, col);
            int channelIndex = 0;
            int channelValue = (int) pixel[channelIndex];

            // Extraire le LSB
            keyBits[i] = (channelValue & 1) == 1;
        }

        int r = 0;
        for (int i = 0; i < 8; i++) {
            if (keyBits[i]) {
                r |= (1 << i);
            }
        }

        int s = 0;
        for (int i = 0; i < 7; i++) {
            if (keyBits[8 + i]) {
                s |= (1 << i);
            }
        }

        Mat decrypted = decrypt(input, r, s);

        return decrypted;
    }

    public static double pearson(byte[] x, byte[] y) {
        int n = x.length;

        double sumX = 0, sumY = 0;
        double sumX2 = 0, sumY2 = 0;
        double sumXY = 0;

        for (int i = 0; i < n; i++) {
            int xi = x[i] & 0xFF;
            int yi = y[i] & 0xFF;

            sumX += xi;
            sumY += yi;
            sumX2 += xi * xi;
            sumY2 += yi * yi;
            sumXY += xi * yi;
        }

        double numerator = n * sumXY - sumX * sumY;
        double denominator = (n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY);

        if (denominator == 0) return 0;
        return numerator / denominator;
    }

    public static double pearsonFast(byte[] data, int r1, int r2, int length, int step) {
        double sumX = 0, sumY = 0;
        double sumX2 = 0, sumY2 = 0;
        double sumXY = 0;

        int xi, yi;

        for (int i = 0; i < length; i+=step) {
            xi = data[r1 * length + i] & 0xFF;
            yi = data[r2 * length + i] & 0xFF;

            sumX += xi;
            sumY += yi;
            sumX2 += xi * xi;
            sumY2 += yi * yi;
            sumXY += xi * yi;
        }

        double numerator = length * sumXY - sumX * sumY;
        double denominator = (length * sumX2 - sumX * sumX) * (length * sumY2 - sumY * sumY);

        if (denominator == 0) return 0;
        return numerator / denominator;
    }

    public static double euclideanDistance(byte[] row1, byte[] row2) {
        long sumSq = 0;

        for (int i = 0; i < row1.length; i++) {
            int p1 = row1[i] & 0xFF;
            int p2 = row2[i] & 0xFF;

            int diff = p1 - p2;

            sumSq += diff * diff;
        }

        return -sumSq;
    }

    public static double euclideanDistanceFast(byte[] data, int r1, int r2, int length, int step) {
        long sumSq = 0;
        int p1, p2, diff;

        for (int i = 0; i < length; i+=step) {
            p1 = data[r1 * length + i] & 0xFF;
            p2 = data[r2 * length + i] & 0xFF;

            diff = p1 - p2;

            sumSq += diff * diff;
        }

        return -sumSq;
    }

    public static double evaluateFrameForKeyFinding(Mat frame) {
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stdDev = new MatOfDouble();
        Core.meanStdDev(frame, mean, stdDev);
        return 0.0722 * stdDev.get(0, 0)[0]+ 0.7152  * stdDev.get(1, 0)[0]+ 0.2126* stdDev.get(2, 0)[0];
    }

    public static Key bruteForceCrack(Mat image){
        int N = largestPowerOf2(image.rows());
        int width = image.cols();
        byte[] imageData = new byte[N * width];
        Mat bwImage = new Mat();
        Imgproc.cvtColor(image, bwImage, Imgproc.COLOR_BGR2GRAY);
        bwImage.get(0, 0, imageData);

        Key bestKey = new Key(0,0);
        double bestScore = Double.NEGATIVE_INFINITY;
        double score;
        int[] destIndexInBlockArr = new int[N];
        int mask = N -1;
        int pearsonStep =   1;

        for(int s = 0; s < 128; s++){
            int steps = 2*s +1;
            for(int i = 0; i < N; i++){
                destIndexInBlockArr[i] = (bestKey.r + steps * i) & mask;
            }
            score = 0.0;
            for(int i = 0; i < mask; i++){
                score += euclideanDistanceFast(imageData, destIndexInBlockArr[i], destIndexInBlockArr[i+1], width, pearsonStep);
            }
            if(score > bestScore){
                bestScore = score;
                bestKey.s = s;
            }
        }
        bestScore = Double.NEGATIVE_INFINITY;
        int steps = 2*bestKey.s +1;
        for(int r = 0; r < 256; r++){
            for(int i = 0; i < N; i++){
                destIndexInBlockArr[i] = (r + steps * i) & mask;
            }
            score = 0.0;
            for(int i = 0; i < mask; i++){
                score += euclideanDistanceFast(imageData, destIndexInBlockArr[i], destIndexInBlockArr[i+1], width, pearsonStep);
            }
            if(score > bestScore){
                bestScore = score;
                bestKey.r = r;
            }
        }
        return bestKey;
    }

    public static Key smartCrack(Mat encryptedImage) {
        int height = encryptedImage.rows();
        int width = encryptedImage.cols();
        int channels = encryptedImage.channels();
        int rowSize = width * channels;

        int blockSize = Integer.highestOneBit(height);
        int blockMask = blockSize - 1;

        // S
        int bestPivotIndex = 0;
        long maxVariance = -1;
        byte[] tempRowData = new byte[rowSize];
        int searchLimit = Math.min(height, blockSize);

        for (int i = 0; i < searchLimit; i += 40) {
            encryptedImage.get(i, 0, tempRowData);
            long variance = calculateRowVariance(tempRowData, channels);

            if (variance > maxVariance) {
                maxVariance = variance;
                bestPivotIndex = i;
            }
        }

        byte[] pivotRowData = new byte[rowSize];
        encryptedImage.get(bestPivotIndex, 0, pivotRowData);

        byte[] candidateRowData = new byte[rowSize];
        long minDistanceSq = Long.MAX_VALUE;
        int bestS = 0;

        for (int s = 0; s < 128; s++) {
            int step = 2 * s + 1;
            int neighborIndex = (bestPivotIndex + step) & blockMask;

            encryptedImage.get(neighborIndex, 0, candidateRowData);
            long currentDistanceSq = calculateEuclideanDistanceSq(pivotRowData, candidateRowData);

            if (currentDistanceSq < minDistanceSq) {
                minDistanceSq = currentDistanceSq;
                bestS = s;
            }
        }

        int step = 2 * bestS + 1;
        int topIndex, bottomIndex;
        double minCorrelation = Double.MAX_VALUE;
        int bestR = 0;
        Mat firstBlock = encryptedImage.submat(0, blockSize, 0, width);
        Mat grayBlock = new Mat();
        Imgproc.cvtColor(firstBlock, grayBlock, Imgproc.COLOR_BGR2GRAY);
        byte[] data = new byte[width*blockSize];
        grayBlock.get(0, 0, data);

        for (int r = 0; r < 256; r++) {
            topIndex = r;
            bottomIndex  = (r - step) & blockMask;
            double correlation = Encryption.euclideanDistanceFast(data, bottomIndex, topIndex, width, 1);

            if (correlation < minCorrelation) {
                minCorrelation = correlation;
                bestR = r;
            }
        }

        return new Key(bestR, bestS);
    }

    private static long calculateRowVariance(byte[] rowData, int channels) {
        long totalVariance = 0;
        for (int i = 0; i < rowData.length - channels; i++) {
            int pixelValue1 = rowData[i] & 0xFF;
            int pixelValue2 = rowData[i + channels] & 0xFF;
            totalVariance += Math.abs(pixelValue1 - pixelValue2);
        }
        return totalVariance;
    }

    private static long calculateEuclideanDistanceSq(byte[] row1, byte[] row2) {
        long sumSq = 0;
        for (int i = 0; i < row1.length; i+=10) {
            int val1 = row1[i] & 0xFF;
            int val2 = row2[i] & 0xFF;
            int diff = val1 - val2;
            sumSq += diff * diff;
        }
        return sumSq;
    }

}
