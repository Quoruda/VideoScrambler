import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

public class VideoPlayer extends Application {

    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    // --- INJECTION FXML ---
    @FXML private TabPane modeTabPane;
    @FXML private Label inputLabel, outputLabel;
    @FXML private ImageView inputImageView, outputImageView;

    // Onglet 1
    @FXML private TextField rField, sField;
    @FXML private Button autoButton, openButton, playButton, prevButton, nextButton, exportButton;

    // Onglet 2
    @FXML private TextField rField2, sField2;
    @FXML private Button autoButton2, openButton2, playButton2, prevButton2, nextButton2, exportButton2;

    // Onglet 3
    @FXML private TextField kField;
    @FXML private Button openButton3, playButton3, prevButton3, nextButton3, exportButton3;

    // Onglet 4
    @FXML private TextField kField4;
    @FXML private CheckBox autoCheckBox4;
    @FXML private Button openButton4, playButton4, prevButton4, nextButton4, exportButton4;


    // --- LOGIQUE INTERNE ---

    private final List<TabContext> tabs = new ArrayList<>();

    private Stage stage;
    private VideoCapture videoCapture;
    private String currentVideoPath;

    // État de lecture
    private AnimationTimer playTimer;
    private boolean isPlaying = false;
    private int currentFrameIndex = 0;
    private int totalFrames = 0;
    private double fps = 30.0;
    private long lastFrameTime = 0;

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
    public void initialize() {
        // Configuration Onglet 0 : Chiffrement (r, s)
        tabs.add(new TabContext(
                "Vidéo d'entrée (claire)", "Vidéo de sortie (chiffrée)",
                openButton, playButton, prevButton, nextButton, exportButton, autoButton,
                frame -> {
                    int r = parse(rField, 3);
                    int s = parse(sField, 7);
                    return Encryption.encrypt(frame, r, s);
                }
        ).addInputs(rField, sField));

        // Configuration Onglet 1 : Déchiffrement (r, s)
        tabs.add(new TabContext(
                "Vidéo d'entrée (chiffrée)", "Vidéo de sortie (déchiffrée)",
                openButton2, playButton2, prevButton2, nextButton2, exportButton2, autoButton2,
                frame -> {
                    int r = parse(rField2, 3);
                    int s = parse(sField2, 7);
                    return Encryption.decrypt(frame, r, s);
                }
        ).addInputs(rField2, sField2));

        // Configuration Onglet 2 : Chiffrement Dynamique (k)
        tabs.add(new TabContext(
                "Vidéo d'entrée (claire)", "Vidéo de sortie (chiffrée - dynamique)",
                openButton3, playButton3, prevButton3, nextButton3, exportButton3, null,
                frame -> {
                    int k = parse(kField, 0);
                    return Encryption.dynamicEncrypt(frame, k);
                }
        ).addInputs(kField));

        // Configuration Onglet 3 : Déchiffrement Dynamique (k)
        tabs.add(new TabContext(
                "Vidéo d'entrée (chiffrée)", "Vidéo de sortie (déchiffrée - dynamique)",
                openButton4, playButton4, prevButton4, nextButton4, exportButton4, null,
                frame -> {
                    if (autoCheckBox4.isSelected()) {
                        Key k = Encryption.bruteForceCrack(frame);
                        return Encryption.decrypt(frame, k.r, k.s);
                    } else {
                        int k = parse(kField4, 0);
                        return Encryption.dynamicDecrypt(frame, k);
                    }
                }
        ).addInputs(kField4));

        // Listener global pour le changement d'onglet
        modeTabPane.getSelectionModel().selectedIndexProperty().addListener((obs, old, neu) -> updateUIForActiveTab());

        // Initialisation de l'état UI
        updateUIForActiveTab();
    }

    // --- GESTION DE L'INTERFACE ---

    private TabContext getCurrentTab() {
        int index = modeTabPane.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < tabs.size()) return tabs.get(index);
        return tabs.get(0);
    }

    private void updateUIForActiveTab() {
        TabContext current = getCurrentTab();
        inputLabel.setText(current.inputLabel);
        outputLabel.setText(current.outputLabel);
        refreshDisplay();
    }

    private void refreshDisplay() {
        if (videoCapture != null && videoCapture.isOpened()) {
            showFrame(currentFrameIndex);
        }
    }

    private void setAllControlsDisabled(boolean disabled) {
        tabs.forEach(tab -> tab.setControlsDisabled(disabled));
    }

    // --- ACTIONS FXML ---

    @FXML private void handleOpenVideo() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vidéos", "*.mp4", "*.avi", "*.mkv"));
        File file = fc.showOpenDialog(stage);
        if (file != null) {
            loadVideo(file.getAbsolutePath());
            setAllControlsDisabled(false);
        }
    }

    @FXML private void handlePlayPause() {
        if (isPlaying) pauseVideo();
        else playVideo();
    }

    @FXML private void handlePrevFrame() { navigateFrame(-1); }
    @FXML private void handleNextFrame() { navigateFrame(1); }

    @FXML private void handleAutoCheckBox() {
        boolean isAuto = autoCheckBox4.isSelected();
        kField4.setDisable(isAuto);
        refreshDisplay();
    }

    // --- GESTION VIDÉO ---

    private void loadVideo(String path) {
        if (videoCapture != null) videoCapture.release();
        currentVideoPath = path;
        videoCapture = new VideoCapture(path);

        if (videoCapture.isOpened()) {
            totalFrames = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_COUNT);
            fps = videoCapture.get(Videoio.CAP_PROP_FPS);
            if (fps <= 0) fps = 30.0;
            currentFrameIndex = 0;
            showFrame(0);
        }
    }

    private void navigateFrame(int delta) {
        int newIndex = currentFrameIndex + delta;
        if (newIndex >= 0 && newIndex < totalFrames) {
            currentFrameIndex = newIndex;
            showFrame(currentFrameIndex);
        }
    }

    private void playVideo() {
        if (videoCapture == null || !videoCapture.isOpened()) return;

        isPlaying = true;
        getCurrentTab().playButton.setText("⏸ Pause");
        lastFrameTime = System.nanoTime();

        videoCapture.set(Videoio.CAP_PROP_POS_FRAMES, currentFrameIndex);

        playTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastFrameTime >= (1_000_000_000 / fps)) {
                    if (currentFrameIndex < totalFrames - 1) {
                        currentFrameIndex++;
                        Mat frame = new Mat();
                        if (videoCapture.read(frame) && !frame.empty()) {
                            processAndDisplay(frame);
                        }
                        lastFrameTime = now;
                    } else {
                        pauseVideo();
                        currentFrameIndex = 0;
                        showFrame(0);
                    }
                }
            }
        };
        playTimer.start();
    }

    private void pauseVideo() {
        isPlaying = false;
        getCurrentTab().playButton.setText("▶ Lecture");
        if (playTimer != null) playTimer.stop();
    }

    private void showFrame(int index) {
        if (videoCapture == null || !videoCapture.isOpened()) return;
        videoCapture.set(Videoio.CAP_PROP_POS_FRAMES, index);
        Mat frame = new Mat();
        if (videoCapture.read(frame)) {
            processAndDisplay(frame);
        }
    }

    private void processAndDisplay(Mat rawFrame) {
        try {
            Mat processed = getCurrentTab().processor.apply(rawFrame);
            inputImageView.setImage(matToImage(rawFrame));
            outputImageView.setImage(matToImage(processed));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- ACTIONS AUTOMATIQUE & EXPORT ---

    @FXML
    private void handleAutoKey() {
        if (videoCapture == null) return;

        TabContext current = getCurrentTab();
        setAllControlsDisabled(true);


        Key result;
        if (modeTabPane.getSelectionModel().getSelectedIndex() == 0) {
            result = new Key(new Random().nextInt(256), new Random().nextInt(128));
        } else {

            int step = Math.max(1, totalFrames / 10);
            Mat mat = new Mat();
            Mat bestFrame = new Mat();

            double bestScore = Double.NEGATIVE_INFINITY;
            for(int i = 0; i < totalFrames; i += step) {
                videoCapture.set(Videoio.CAP_PROP_POS_FRAMES, i);
                videoCapture.read(mat);
                double score = Encryption.evaluateFrameForKeyFinding(mat);
                if(score > bestScore) {
                    bestScore = score;
                    bestFrame = mat.clone();
                }
            }

            result = Encryption.bruteForceCrack(bestFrame);
        }

        Platform.runLater(() -> {
            if (current.inputs.size() >= 2) {
                current.inputs.get(0).setText(String.valueOf(result.r));
                current.inputs.get(1).setText(String.valueOf(result.s));
            }

            showAlert("Succès", "Clé trouvée : R=" + result.r + ", S=" + result.s);
            setAllControlsDisabled(false);
            refreshDisplay();
        });

    }

    @FXML
    private void handleExportVideo() {
        if (videoCapture == null) return;

        File file = promptForSave();
        if (file == null) return;

        // Arrêt de la lecture si en cours pour libérer les ressources
        if (isPlaying) pauseVideo();

        setAllControlsDisabled(true);

        ProgressBar progressBar = new ProgressBar(0);
        Alert progressDialog = createProgressDialog(progressBar);
        progressDialog.show();

        Function<Mat, Mat> currentProcessor = getCurrentTab().processor;

        // Thread sécurisé avec try-catch pour garantir la fermeture de la popup
        new Thread(() -> {
            boolean success = false;
            String errorMsg = "Erreur inconnue";

            try {
                success = exportLoop(file.getAbsolutePath(), currentProcessor, progressBar);
                if (!success) errorMsg = "Impossible d'initialiser l'export (Source ou Destination invalide).";
            } catch (Exception e) {
                e.printStackTrace();
                errorMsg = e.getMessage();
                success = false;
            }

            boolean finalSuccess = success;
            String finalErrorMsg = errorMsg;

            Platform.runLater(() -> {
                // On force la fermeture
                progressDialog.setResult(ButtonType.CANCEL);
                progressDialog.close();

                setAllControlsDisabled(false);

                if (finalSuccess) {
                    showAlert("Export", "Export terminé avec succès !");
                } else {
                    showAlert("Erreur Export", "Echec de l'export : " + finalErrorMsg);
                }
            });
        }).start();
    }

    private boolean exportLoop(String outPath, Function<Mat, Mat> processor, ProgressBar bar) {
        VideoCapture cap = new VideoCapture(currentVideoPath);
        if (!cap.isOpened()) {
            System.err.println("Erreur: Impossible d'ouvrir la vidéo source pour l'export.");
            return false;
        }

        int w = (int) cap.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int h = (int) cap.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        double vidFps = cap.get(Videoio.CAP_PROP_FPS);
        if (vidFps <= 0) vidFps = 30.0;

        // --- MODIFICATION CODEC ---
        // Utilisation de HuffYUV (HFYU)
        // C'est un codec Lossless (Sans perte) mais beaucoup plus rapide que FFV1
        // Le fichier sera un peu plus gros, mais l'export sera rapide.

        int fourcc = VideoWriter.fourcc('H','F','Y','U');

        VideoWriter writer = new VideoWriter(outPath, fourcc, vidFps, new Size(w, h), true);

        if (!writer.isOpened()) {
            System.err.println("Erreur: Impossible de créer le fichier de sortie avec HFYU. Codec manquant ?");
            cap.release();
            return false;
        }

        Mat frame = new Mat();
        int count = 0;
        int total = (int) cap.get(Videoio.CAP_PROP_FRAME_COUNT);
        if (total <= 0) total = 1;

        while (cap.read(frame) && !frame.empty()) {
            Mat out = processor.apply(frame);
            writer.write(out);
            count++;

            if (count % 5 == 0) { // Mise à jour de la barre plus fréquente
                double p = (double) count / total;
                Platform.runLater(() -> bar.setProgress(p));
            }
        }

        writer.release();
        cap.release();
        return true;
    }

    // --- UTILITAIRES ---

    private int parse(TextField field, int def) {
        try { return Integer.parseInt(field.getText()); }
        catch (Exception e) { return def; }
    }

    private File promptForSave() {
        FileChooser fc = new FileChooser();
        // HFYU fonctionne bien avec .avi ou .mkv
        fc.setInitialFileName("video_export.avi");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("AVI Video", "*.avi"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("MKV Video", "*.mkv"));
        return fc.showSaveDialog(stage);
    }

    private void showAlert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content);
        a.showAndWait();
    }

    private Alert createProgressDialog(ProgressBar bar) {
        bar.setPrefWidth(300);
        // Changement AlertType.NONE -> INFORMATION pour avoir un comportement standard de fenêtre
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Export en cours");
        a.setHeaderText(null);
        a.getDialogPane().setContent(new VBox(10, new Label("Traitement en cours..."), bar));
        // On enlève le bouton OK pour forcer l'attente (ou permettre fermeture propre via code)
        a.getDialogPane().getButtonTypes().clear();
        return a;
    }

    private Image matToImage(Mat mat) {
        int w = mat.cols(), h = mat.rows(), c = mat.channels();
        WritableImage img = new WritableImage(w, h);
        PixelWriter pw = img.getPixelWriter();
        byte[] buf = new byte[w * h * c];
        mat.get(0, 0, buf);
        int[] pixels = new int[w * h];

        for (int i = 0; i < pixels.length; i++) {
            if (c == 3) {
                int b = buf[i*3]&0xFF, g = buf[i*3+1]&0xFF, r = buf[i*3+2]&0xFF;
                pixels[i] = 0xFF000000 | (r<<16) | (g<<8) | b;
            } else {
                int g = buf[i]&0xFF;
                pixels[i] = 0xFF000000 | (g<<16) | (g<<8) | g;
            }
        }
        pw.setPixels(0,0,w,h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        return img;
    }

    // --- CLASSE INTERNE CONTEXTE ---

    private class TabContext {
        String inputLabel, outputLabel;
        Button openButton, playButton, prevButton, nextButton, exportButton, autoButton;
        Function<Mat, Mat> processor;
        List<TextField> inputs = new ArrayList<>();

        public TabContext(String inLbl, String outLbl,
                          Button open, Button play, Button prev, Button next, Button export, Button auto,
                          Function<Mat, Mat> proc) {
            this.inputLabel = inLbl; this.outputLabel = outLbl;
            this.openButton = open; this.playButton = play;
            this.prevButton = prev; this.nextButton = next;
            this.exportButton = export; this.autoButton = auto;
            this.processor = proc;
        }

        public TabContext addInputs(TextField... fields) {
            for (TextField f : fields) {
                this.inputs.add(f);
                f.textProperty().addListener((o, old, val) -> refreshDisplay());
            }
            return this;
        }

        public void setControlsDisabled(boolean disabled) {
            if (openButton != null) openButton.setDisable(disabled);
            if (playButton != null) playButton.setDisable(disabled);
            if (prevButton != null) prevButton.setDisable(disabled);
            if (nextButton != null) nextButton.setDisable(disabled);
            if (exportButton != null) exportButton.setDisable(disabled);
            if (autoButton != null) autoButton.setDisable(disabled);
        }
    }

    public static void main(String[] args) { launch(args); }
}