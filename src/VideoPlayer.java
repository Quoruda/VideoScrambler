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

import java.io.Console;
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

        // Ajouter les listeners sur les champs r et s
        controller.setupKeyChangeListeners();

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
        } else {
            inputLabel.setText("Vidéo d'entrée (chiffrée)");
            outputLabel.setText("Vidéo de sortie (déchiffrée)");
        }

        if (videoCapture != null && videoCapture.isOpened()) {
            showFrame(currentFrameIndex);
        }
    }

    /**
     * Configure les listeners sur les champs r et s pour détecter les changements
     */
    private void setupKeyChangeListeners() {
        rField.textProperty().addListener((observable, oldValue, newValue) -> handleKeyChange("r", newValue));
        sField.textProperty().addListener((observable, oldValue, newValue) -> handleKeyChange("s", newValue));
    }

    /**
     * Fonction appelée à chaque changement de r ou s
     * Affiche les nouvelles valeurs dans la console
     */
    private void handleKeyChange(String paramName, String newValue) {
        try {
            int r = Integer.parseInt(rField.getText());
            int s = Integer.parseInt(sField.getText());

            if (r < 0 || r > 255) return;
            if (s < 0 || s > 127) return;

            if (videoCapture != null && videoCapture.isOpened()) {
                showFrame(currentFrameIndex);
            }
        } catch (NumberFormatException e) {
            // Ignore les valeurs non numériques pendant la saisie
        }
    }

    @FXML
    private void handleAutoKey() {
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
            String modeText = isEncryptMode ? "chiffrement optimal" : "déchiffrement";
            System.out.println("Recherche automatique de clé pour " + modeText + "...");

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

            // Choisir la fonction selon le mode
            Key key;
            if (isEncryptMode) {
                key = Encryption.findKeyForEncryption(frame);
                System.out.println("Clé de chiffrement optimal trouvée: r=" + key.getR() + ", s=" + key.getS());
            } else {
                key = Encryption.findSmartKey(frame);
                System.out.println("Clé de déchiffrement trouvée: r=" + key.getR() + ", s=" + key.getS());
            }

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
                String contentText = "r = " + key.getR() + "\ns = " + key.getS();
                if (isEncryptMode) {
                    contentText += "\n\n(Clé optimale pour maximiser le brouillage)";
                } else {
                    contentText += "\n\n(Clé optimale pour restaurer l'image)";
                }
                alert.setContentText(contentText);
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
            setAppBusy(false);
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

    // Gère l'activation/désactivation massive des boutons
    private void setAppBusy(boolean disabled) {
        exportButton.setDisable(disabled);
        playButton.setDisable(disabled);
        prevButton.setDisable(disabled);
        nextButton.setDisable(disabled);
        openButton.setDisable(disabled);
        autoButton.setDisable(disabled);
    }

    // Gère la boîte de dialogue de sauvegarde
    private File promptForSaveFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Enregistrer la vidéo");
        fileChooser.setInitialFileName(isEncryptMode ? "video_chiffree.mkv" : "video_dechiffree.mkv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers MKV", "*.mkv")
        );
        return fileChooser.showSaveDialog(stage);
    }

    // Crée la fenêtre de progression
    private Alert createProgressAlert(ProgressBar progressBar, Label progressLabel, boolean[] cancelled) {
        progressBar.setPrefWidth(400);
        VBox progressBox = new VBox(10, progressLabel, progressBar);
        progressBox.setPadding(new javafx.geometry.Insets(20));

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export en cours");
        alert.setHeaderText("Traitement de la vidéo");
        alert.getDialogPane().setContent(progressBox);
        alert.getButtonTypes().setAll(ButtonType.CANCEL);

        alert.setOnCloseRequest(event -> cancelled[0] = true);
        return alert;
    }

    // Gère l'affichage du résultat final (Succès ou Annulation)
    private void handleExportCompletion(boolean success, File file, boolean wasCancelled) {
        if (success) {
            showAlert(Alert.AlertType.INFORMATION, "Export terminé",
                    "La vidéo a été exportée avec succès vers :\n" + file.getAbsolutePath());
        } else if (wasCancelled) {
            showAlert(Alert.AlertType.WARNING, "Export annulé", "L'export de la vidéo a été annulé.");
        } else {
            showAlert(Alert.AlertType.ERROR, "Echec", "L'export a échoué pour une raison inconnue.");
        }
    }

    @FXML
    private void handleExportVideo() {
        // 1. Validation
        if (videoCapture == null || !videoCapture.isOpened()) {
            showAlert(Alert.AlertType.WARNING, "Aucune vidéo", "Veuillez d'abord ouvrir une vidéo.");
            return;
        }

        // 2. Sélection du fichier
        File file = promptForSaveFile();
        if (file == null) return;

        // 3. Préparation de l'interface (UI)
        setAppBusy(true); // Désactive les boutons

        // Création de la modale de progression
        ProgressBar progressBar = new ProgressBar(0);
        Label progressLabel = new Label("Préparation de l'export...");
        // Variable pour l'annulation (tableau pour être final/mutable dans la lambda)
        final boolean[] cancelled = {false};

        Alert progressAlert = createProgressAlert(progressBar, progressLabel, cancelled);
        progressAlert.show();

        // 4. Lancement du processus en arrière-plan
        new Thread(() -> {
            try {
                // Exécution lourde
                boolean success = exportVideoProcess(file.getAbsolutePath(), progressBar, progressLabel, cancelled);

                // Mise à jour de l'UI une fois fini
                javafx.application.Platform.runLater(() -> {
                    progressAlert.hide();
                    handleExportCompletion(success, file, cancelled[0]);
                    setAppBusy(false); // Réactive les boutons
                });

            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    progressAlert.hide();
                    showAlert(Alert.AlertType.ERROR, "Erreur d'export", "Une erreur s'est produite : " + e.getMessage());
                    setAppBusy(false); // Réactive les boutons
                });
            }
        }).start();
    }

    // Utilitaire générique pour afficher une alerte simple
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private boolean exportVideoProcess(String outputPath, ProgressBar progressBar, Label progressLabel, boolean[] cancelled) {
        // 1. Initialisation de la lecture
        VideoCapture exportCapture = new VideoCapture(currentVideoPath);
        if (!exportCapture.isOpened()) {
            throw new RuntimeException("Impossible d'ouvrir la vidéo source.");
        }

        int width = (int) exportCapture.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int height = (int) exportCapture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        double fps = exportCapture.get(Videoio.CAP_PROP_FPS);
        if (fps <= 0) fps = 30.0;

        // 2. Initialisation de l'écriture (Codec unique : HuffYUV)
        // 'H', 'F', 'Y', 'U' est rapide et sans perte (Lossless)
        int fourcc = VideoWriter.fourcc('H', 'F', 'Y', 'U');
        VideoWriter videoWriter = new VideoWriter(outputPath, fourcc, fps, new Size(width, height), true);

        if (!videoWriter.isOpened()) {
            exportCapture.release();
            throw new RuntimeException("Erreur : Impossible d'initialiser l'encodage vidéo (Codec HFYU non supporté).");
        }

        // 3. Boucle de traitement
        Mat frame = new Mat();
        int processedFrames = 0;
        final int actualTotalFrames = totalFrames > 0 ? totalFrames : 1000;

        // Optimisation : Mise à jour de l'interface toutes les 1% seulement
        int updateFrequency = Math.max(10, actualTotalFrames / 100);

        try {
            while (!cancelled[0]) {
                if (!exportCapture.read(frame) || frame.empty()) break;

                // Traitement (Chiffrer/Déchiffrer)
                Mat processedFrame = processFrame(frame);

                // Écriture
                if (processedFrame != null && !processedFrame.empty()) {
                    videoWriter.write(processedFrame);
                }

                processedFrames++;
                final int current = processedFrames;

                // Mise à jour de la barre de progression (Optimisée)
                if (current % updateFrequency == 0 || current == actualTotalFrames) {
                    double progress = (double) current / actualTotalFrames;
                    javafx.application.Platform.runLater(() -> {
                        progressBar.setProgress(progress);
                        progressLabel.setText(String.format("Export : %d / %d", current, actualTotalFrames));
                    });
                }
            }
        } finally {
            // 4. Nettoyage propre
            videoWriter.release();
            exportCapture.release();
            frame.release();
        }

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

