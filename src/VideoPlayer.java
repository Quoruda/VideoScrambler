import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.io.File;

public class VideoPlayer extends Application {

    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    // Composants de l'interface
    @FXML
    private RadioButton encryptModeRadio;

    @FXML
    private RadioButton decryptModeRadio;

    @FXML
    private TextField rField;

    @FXML
    private TextField sField;

    @FXML
    private Button autoButton;

    @FXML
    private Label inputLabel;

    @FXML
    private Label outputLabel;

    @FXML
    private ImageView inputImageView;

    @FXML
    private ImageView outputImageView;

    @FXML
    private Button openButton;

    @FXML
    private Button playButton;

    @FXML
    private Button prevButton;

    @FXML
    private Button nextButton;

    @FXML
    private Button exportButton;

    // Variables pour la gestion de la vidéo
    private VideoCapture videoCapture;
    private String currentVideoPath;
    private Mat currentFrame;
    private int currentFrameIndex = 0;
    private int totalFrames = 0;
    private Stage stage;

    private AnimationTimer playTimer;
    private boolean isPlaying = false;
    private double fps = 30.0;
    private long lastFrameTime = 0;

    private boolean isEncryptMode = true;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("VideoPlayer.fxml"));
        Parent root = loader.load();

        VideoPlayer controller = loader.getController();
        controller.stage = primaryStage;

        Scene scene = new Scene(root, 1000, 550);
        primaryStage.setTitle("Chiffrement/Déchiffrement de Vidéo");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @FXML
    private void handleModeChange() {
        isEncryptMode = encryptModeRadio.isSelected();

        if (isEncryptMode) {
            inputLabel.setText("Vidéo d'entrée (claire)");
            outputLabel.setText("Vidéo de sortie (chiffrée)");
            autoButton.setVisible(false);
        } else {
            inputLabel.setText("Vidéo d'entrée (chiffrée)");
            outputLabel.setText("Vidéo de sortie (déchiffrée)");
            autoButton.setVisible(true);
        }

        // Rafraîchir l'affichage si une vidéo est chargée
        if (videoCapture != null && videoCapture.isOpened()) {
            showFrame(currentFrameIndex);
        }
    }

    @FXML
    private void handleAutoDecrypt() {
        if (videoCapture == null || !videoCapture.isOpened()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Aucune vidéo");
            alert.setHeaderText(null);
            alert.setContentText("Veuillez d'abord ouvrir une vidéo.");
            alert.showAndWait();
            return;
        }

        // Désactiver les contrôles pendant la recherche
        autoButton.setDisable(true);
        playButton.setDisable(true);

        // Lancer la recherche dans un thread séparé pour ne pas bloquer l'interface
        new Thread(() -> {
            System.out.println("Recherche automatique de clé...");

            // Prendre une frame aléatoire
            java.util.Random rand = new java.util.Random();
            int iframe = rand.nextInt(totalFrames);
            Mat frame = readFrame(iframe);

            if (frame == null) {
                javafx.application.Platform.runLater(() -> {
                    autoButton.setDisable(false);
                    playButton.setDisable(false);
                });
                return;
            }

            Key key = Encryption.findKeyForDecryption(frame);

            System.out.println("Clé trouvée: r=" + key.getR() + ", s=" + key.getS());

            // Mettre à jour l'interface dans le thread JavaFX
            javafx.application.Platform.runLater(() -> {
                rField.setText(String.valueOf(key.getR()));
                sField.setText(String.valueOf(key.getS()));

                // Rafraîchir l'affichage avec la nouvelle clé
                showFrame(currentFrameIndex);

                // Afficher un message de succès
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Recherche terminée");
                alert.setHeaderText("Clé trouvée !");
                alert.setContentText("r = " + key.getR() + "\ns = " + key.getS());
                alert.showAndWait();

                // Réactiver les contrôles
                autoButton.setDisable(false);
                playButton.setDisable(false);
            });
        }).start();
    }

    @FXML
    private void handleOpenVideo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sélectionner une vidéo");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers vidéo", "*.mp4", "*.avi", "*.mov", "*.mkv")
        );
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            openVideo(file.getAbsolutePath());
            playButton.setDisable(false);
            prevButton.setDisable(false);
            nextButton.setDisable(false);
            exportButton.setDisable(false);
        }
    }

    @FXML
    private void handlePlayPause() {
        if (isPlaying) {
            pauseVideo();
        } else {
            playVideo();
        }
    }

    @FXML
    private void handlePrevFrame() {
        if (currentFrameIndex > 0) {
            currentFrameIndex--;
            showFrame(currentFrameIndex);
        }
    }

    @FXML
    private void handleNextFrame() {
        if (currentFrameIndex < totalFrames - 1) {
            currentFrameIndex++;
            showFrame(currentFrameIndex);
        }
    }

    @FXML
    private void handleExportVideo() {
        if (videoCapture == null || !videoCapture.isOpened()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Aucune vidéo");
            alert.setHeaderText(null);
            alert.setContentText("Veuillez d'abord ouvrir une vidéo.");
            alert.showAndWait();
            return;
        }

        // Choisir le fichier de destination
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Enregistrer la vidéo");
        fileChooser.setInitialFileName(isEncryptMode ? "video_chiffree.mp4" : "video_dechiffree.mp4");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers MP4", "*.mp4")
        );
        File file = fileChooser.showSaveDialog(stage);

        if (file == null) {
            return; // L'utilisateur a annulé
        }

        // Désactiver les contrôles pendant l'export
        exportButton.setDisable(true);
        playButton.setDisable(true);
        prevButton.setDisable(true);
        nextButton.setDisable(true);
        openButton.setDisable(true);

        // Créer une barre de progression
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        Label progressLabel = new Label("Préparation de l'export...");

        VBox progressBox = new VBox(10, progressLabel, progressBar);
        progressBox.setPadding(new javafx.geometry.Insets(20));

        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle("Export en cours");
        progressAlert.setHeaderText("Traitement de la vidéo");
        progressAlert.getDialogPane().setContent(progressBox);
        progressAlert.getButtonTypes().setAll(ButtonType.CANCEL);

        // Variable pour suivre l'annulation
        final boolean[] cancelled = {false};

        // Gérer l'annulation
        progressAlert.setOnCloseRequest(event -> {
            cancelled[0] = true;
        });

        progressAlert.show();

        // Lancer l'export dans un thread séparé
        new Thread(() -> {
            try {
                boolean success = exportVideoProcess(file.getAbsolutePath(), progressBar, progressLabel, cancelled);

                // Afficher un message de succès ou d'annulation
                javafx.application.Platform.runLater(() -> {
                    progressAlert.hide();

                    if (success) {
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("Export terminé");
                        successAlert.setHeaderText("Succès !");
                        successAlert.setContentText("La vidéo a été exportée avec succès vers :\n" + file.getAbsolutePath());
                        successAlert.showAndWait();
                    } else {
                        Alert cancelAlert = new Alert(Alert.AlertType.WARNING);
                        cancelAlert.setTitle("Export annulé");
                        cancelAlert.setHeaderText("Export annulé");
                        cancelAlert.setContentText("L'export de la vidéo a été annulé.");
                        cancelAlert.showAndWait();
                    }

                    // Réactiver les contrôles
                    exportButton.setDisable(false);
                    playButton.setDisable(false);
                    prevButton.setDisable(false);
                    nextButton.setDisable(false);
                    openButton.setDisable(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    progressAlert.close();

                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Erreur d'export");
                    errorAlert.setHeaderText("Échec de l'export");
                    errorAlert.setContentText("Une erreur s'est produite : " + e.getMessage());
                    errorAlert.showAndWait();

                    // Réactiver les contrôles
                    exportButton.setDisable(false);
                    playButton.setDisable(false);
                    prevButton.setDisable(false);
                    nextButton.setDisable(false);
                    openButton.setDisable(false);
                });
            }
        }).start();
    }

    private boolean exportVideoProcess(String outputPath, ProgressBar progressBar, Label progressLabel, boolean[] cancelled) {
        // Créer une nouvelle VideoCapture dédiée à l'export pour éviter les conflits d'accès
        VideoCapture exportCapture = new VideoCapture(currentVideoPath);

        if (!exportCapture.isOpened()) {
            throw new RuntimeException("Impossible d'ouvrir la vidéo source pour l'export");
        }

        System.out.println("VideoCapture d'export créée pour: " + currentVideoPath);

        // Obtenir les paramètres de la vidéo source
        int frameWidth = (int) exportCapture.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int frameHeight = (int) exportCapture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        double exportFps = exportCapture.get(Videoio.CAP_PROP_FPS);
        if (exportFps <= 0) exportFps = 30.0;

        System.out.println("Démarrage export - Taille: " + frameWidth + "x" + frameHeight + ", FPS: " + exportFps);

        // Créer le VideoWriter avec différents codecs selon la plateforme
        VideoWriter videoWriter = null;

        // Essayer plusieurs codecs pour maximiser la compatibilité
        int[] codecs = {
            VideoWriter.fourcc('m', 'p', '4', 'v'),  // MPEG-4
            VideoWriter.fourcc('M', 'P', '4', 'V'),  // MPEG-4 (majuscules)
            VideoWriter.fourcc('X', 'V', 'I', 'D'),  // Xvid
            VideoWriter.fourcc('H', '2', '6', '4'),  // H264
            VideoWriter.fourcc('a', 'v', 'c', '1')   // H264
        };

        for (int fourcc : codecs) {
            videoWriter = new VideoWriter(outputPath, fourcc, exportFps, new Size(frameWidth, frameHeight));
            if (videoWriter.isOpened()) {
                System.out.println("VideoWriter ouvert avec codec: " + fourcc);
                break;
            }
            videoWriter.release();
        }

        if (videoWriter == null || !videoWriter.isOpened()) {
            if (exportCapture != videoCapture) {
                exportCapture.release();
            }
            throw new RuntimeException("Impossible de créer le fichier vidéo de sortie avec les codecs disponibles");
        }

        // Utiliser totalFrames (déjà calculé à l'ouverture)
        final int actualTotalFrames = totalFrames > 0 ? totalFrames : 1000;

        // Traiter chaque frame
        Mat frame = new Mat();
        int processedFrames = 0;

        System.out.println("Début du traitement des frames... (Total attendu: " + actualTotalFrames + ")");

        while (!cancelled[0]) {
            boolean frameRead = exportCapture.read(frame);

            if (!frameRead || frame.empty()) {
                System.out.println("Fin de la lecture à la frame " + processedFrames + " (frameRead=" + frameRead + ", empty=" + frame.empty() + ")");
                break;
            }

            // Traiter la frame (chiffrement ou déchiffrement)
            Mat processedFrame = processFrame(frame);

            // Vérifier que la frame traitée n'est pas vide
            if (processedFrame == null || processedFrame.empty()) {
                System.err.println("ERREUR: Frame traitée vide à l'index " + processedFrames);
                continue;
            }

            // Écrire la frame traitée
            videoWriter.write(processedFrame);

            processedFrames++;
            final int current = processedFrames;

            // Mettre à jour la barre de progression tous les 10 frames pour réduire la charge
            if (current % 10 == 0 || current == 1) {
                javafx.application.Platform.runLater(() -> {
                    double progress = (double) current / actualTotalFrames;
                    progressBar.setProgress(Math.min(progress, 1.0));
                    progressLabel.setText(String.format("Traitement : %d / %d frames (%.1f%%)",
                        current, actualTotalFrames, Math.min(progress * 100, 100.0)));
                });
            }
        }

        System.out.println("Traitement terminé, fermeture du VideoWriter...");

        // Libérer les ressources
        videoWriter.release();

        // Libérer la VideoCapture d'export si c'est une instance séparée
        if (exportCapture != videoCapture) {
            exportCapture.release();
        }

        System.out.println("Export terminé : " + processedFrames + " frames traitées sur " + actualTotalFrames + " attendues");

        return !cancelled[0] && processedFrames > 0;
    }


    private void openVideo(String videoPath) {
        if (videoCapture != null) {
            videoCapture.release();
        }

        currentVideoPath = videoPath;
        videoCapture = new VideoCapture(videoPath);
        if (videoCapture.isOpened()) {
            totalFrames = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_COUNT);
            fps = videoCapture.get(Videoio.CAP_PROP_FPS);
            if (fps <= 0) fps = 30.0; // Valeur par défaut si non disponible
            currentFrameIndex = 0;
            showFrame(currentFrameIndex);
        }
    }

    private void playVideo() {
        if (videoCapture == null || !videoCapture.isOpened()) {
            return;
        }

        isPlaying = true;
        playButton.setText("⏸ Pause");
        lastFrameTime = System.nanoTime();

        // Synchroniser le curseur vidéo avec l'index actuel
        videoCapture.set(Videoio.CAP_PROP_POS_FRAMES, currentFrameIndex);

        playTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                long frameDelay = (long) (1_000_000_000 / fps);
                if (now - lastFrameTime >= frameDelay) {
                    if (currentFrameIndex < totalFrames - 1) {
                        currentFrameIndex++;
                        // Lecture séquentielle de la frame (plus rapide que readFrame qui repositionne)
                        Mat frame = new Mat();
                        if (videoCapture.read(frame) && !frame.empty()) {
                            Mat processedFrame = processFrame(frame);
                            displayFrame(frame, processedFrame);
                        }
                        lastFrameTime = now;
                    } else {
                        // Retour au début ou arrêt
                        pauseVideo();
                        currentFrameIndex = 0;
                        showFrame(currentFrameIndex);
                    }
                }
            }
        };
        playTimer.start();
    }

    private void pauseVideo() {
        isPlaying = false;
        playButton.setText("▶ Lecture");
        if (playTimer != null) {
            playTimer.stop();
        }
    }

    private Mat readFrame(int frameIndex) {
        if (videoCapture == null || !videoCapture.isOpened()) {
            return null;
        }
        videoCapture.set(Videoio.CAP_PROP_POS_FRAMES, frameIndex);
        Mat frame = new Mat();
        if (videoCapture.read(frame)) {
            return frame;
        }
        return null;
    }

    /**
     * Méthode pour traiter la frame avec OpenCV.
     * Applique le chiffrement ou déchiffrement selon le mode sélectionné.
     */
    private Mat processFrame(Mat frame) {
        try {
            int r = Integer.parseInt(rField.getText());
            int s = Integer.parseInt(sField.getText());

            // Valider les valeurs
            if (r < 0 || r > 255) r = 3;
            if (s < 0 || s > 127) s = 7;

            if (isEncryptMode) {
                return Encryption.encrypt(frame, r, s);
            } else {
                return Encryption.decrypt(frame, r, s);
            }
        } catch (NumberFormatException e) {
            // En cas d'erreur, retourner la frame telle quelle
            return frame;
        }
    }

    private void displayFrame(Mat originalFrame, Mat processedFrame) {
        if (originalFrame != null && !originalFrame.empty()) {
            Image inputImage = matToImage(originalFrame);
            inputImageView.setImage(inputImage);
        }

        if (processedFrame != null && !processedFrame.empty()) {
            Image outputImage = matToImage(processedFrame);
            outputImageView.setImage(outputImage);
        }
    }

    private void showFrame(int frameIndex) {
        Mat frame = readFrame(frameIndex);
        if (frame != null) {
            Mat processedFrame = processFrame(frame);
            displayFrame(frame, processedFrame);
        }
    }

    private Image matToImage(Mat mat) {
        // Méthode optimisée : conversion directe sans encodage PNG
        int width = mat.cols();
        int height = mat.rows();
        int channels = mat.channels();

        WritableImage image = new WritableImage(width, height);
        PixelWriter pixelWriter = image.getPixelWriter();

        if (channels == 3) {
            // Image BGR (OpenCV) -> RGB (JavaFX)
            byte[] buffer = new byte[width * height * 3];
            mat.get(0, 0, buffer);

            int[] pixelBuffer = new int[width * height];
            for (int i = 0; i < width * height; i++) {
                int b = buffer[i * 3] & 0xFF;
                int g = buffer[i * 3 + 1] & 0xFF;
                int r = buffer[i * 3 + 2] & 0xFF;
                pixelBuffer[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }

            pixelWriter.setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixelBuffer, 0, width);
        } else if (channels == 1) {
            // Image en niveaux de gris
            byte[] buffer = new byte[width * height];
            mat.get(0, 0, buffer);

            int[] pixelBuffer = new int[width * height];
            for (int i = 0; i < width * height; i++) {
                int gray = buffer[i] & 0xFF;
                pixelBuffer[i] = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
            }

            pixelWriter.setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixelBuffer, 0, width);
        }

        return image;
    }

    @Override
    public void stop() {
        if (playTimer != null) {
            playTimer.stop();
        }
        if (videoCapture != null) {
            videoCapture.release();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

