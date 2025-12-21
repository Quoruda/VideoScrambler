// Auteurs : Audrick SOLTNER et Gaẽl RÖTHLIN

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

/**
 * Application JavaFX permettant de chiffrer et déchiffrer des vidéos en temps réel.
 * Supporte plusieurs modes : chiffrement/déchiffrement statique (r, s) et dynamique (k).
 * Permet la lecture, la navigation frame par frame et l'export de vidéos traitées.
 */
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

    /**
     * Démarre l'application JavaFX et charge l'interface FXML.
     *
     * @param primaryStage la fenêtre principale de l'application
     * @throws Exception si le chargement du fichier FXML échoue
     */
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

    /**
     * Initialise les composants de l'interface et configure les 4 onglets de traitement.
     * Configure également les listeners pour les changements d'onglet et l'état initial de l'UI.
     */
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
                        Key k = Encryption.smartCrack(frame);
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

    /**
     * Récupère le contexte de l'onglet actuellement sélectionné.
     *
     * @return le TabContext de l'onglet actif
     */
    private TabContext getCurrentTab() {
        int index = modeTabPane.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < tabs.size()) return tabs.get(index);
        return tabs.get(0);
    }

    /**
     * Met à jour l'interface utilisateur en fonction de l'onglet actif.
     * Change les labels et rafraîchit l'affichage.
     */
    private void updateUIForActiveTab() {
        TabContext current = getCurrentTab();
        inputLabel.setText(current.inputLabel);
        outputLabel.setText(current.outputLabel);
        refreshDisplay();
    }

    /**
     * Rafraîchit l'affichage de la frame courante avec le traitement de l'onglet actif.
     */
    private void refreshDisplay() {
        if (videoCapture != null && videoCapture.isOpened()) {
            showFrame(currentFrameIndex);
        }
    }

    /**
     * Active ou désactive tous les contrôles de tous les onglets.
     *
     * @param disabled true pour désactiver, false pour activer
     */
    private void setAllControlsDisabled(boolean disabled) {
        tabs.forEach(tab -> tab.setControlsDisabled(disabled));
    }

    // --- ACTIONS FXML ---

    /**
     * Gestionnaire pour l'ouverture d'une vidéo via FileChooser.
     */
    @FXML private void handleOpenVideo() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vidéos", "*.mp4", "*.avi", "*.mkv"));
        File file = fc.showOpenDialog(stage);
        if (file != null) {
            loadVideo(file.getAbsolutePath());
            setAllControlsDisabled(false);
        }
    }

    /**
     * Gestionnaire pour basculer entre lecture et pause de la vidéo.
     */
    @FXML private void handlePlayPause() {
        if (isPlaying) pauseVideo();
        else playVideo();
    }

    /**
     * Gestionnaire pour naviguer vers la frame précédente.
     */
    @FXML private void handlePrevFrame() { navigateFrame(-1); }

    /**
     * Gestionnaire pour naviguer vers la frame suivante.
     */
    @FXML private void handleNextFrame() { navigateFrame(1); }

    /**
     * Gestionnaire pour la checkbox de détection automatique de clé (onglet 4).
     * Active/désactive le champ de saisie de clé et rafraîchit l'affichage.
     */
    @FXML private void handleAutoCheckBox() {
        boolean isAuto = autoCheckBox4.isSelected();
        kField4.setDisable(isAuto);
        refreshDisplay();
    }

    // --- GESTION VIDÉO ---

    /**
     * Charge une vidéo depuis le chemin spécifié.
     * Initialise les propriétés de la vidéo (nombre de frames, FPS) et affiche la première frame.
     *
     * @param path le chemin du fichier vidéo à charger
     */
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

    /**
     * Navigue vers une frame relative à la position actuelle.
     *
     * @param delta le déplacement relatif (négatif pour reculer, positif pour avancer)
     */
    private void navigateFrame(int delta) {
        int newIndex = currentFrameIndex + delta;
        if (newIndex >= 0 && newIndex < totalFrames) {
            currentFrameIndex = newIndex;
            showFrame(currentFrameIndex);
        }
    }

    /**
     * Lance la lecture de la vidéo avec le traitement de l'onglet actif.
     * Utilise un AnimationTimer pour synchroniser l'affichage avec le FPS de la vidéo.
     */
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

    /**
     * Met en pause la lecture de la vidéo et arrête le timer d'animation.
     */
    private void pauseVideo() {
        isPlaying = false;
        getCurrentTab().playButton.setText("▶ Lecture");
        if (playTimer != null) playTimer.stop();
    }

    /**
     * Affiche une frame spécifique de la vidéo avec traitement.
     *
     * @param index l'indice de la frame à afficher
     */
    private void showFrame(int index) {
        if (videoCapture == null || !videoCapture.isOpened()) return;
        videoCapture.set(Videoio.CAP_PROP_POS_FRAMES, index);
        Mat frame = new Mat();
        if (videoCapture.read(frame)) {
            processAndDisplay(frame);
        }
    }

    /**
     * Traite une frame brute avec le processeur de l'onglet actif et met à jour l'affichage.
     *
     * @param rawFrame la frame brute à traiter
     */
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

    /**
     * Gestionnaire pour la recherche automatique de clé de chiffrement.
     * Pour l'onglet 0 (chiffrement) : génère une clé aléatoire.
     * Pour l'onglet 1 (déchiffrement) : analyse plusieurs frames et utilise brute force.
     * Exécute le traitement dans un thread séparé pour ne pas bloquer l'UI.
     */
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

    /**
     * Gestionnaire pour l'export de la vidéo traitée.
     * Demande un chemin de destination, puis traite toutes les frames en arrière-plan.
     * Affiche une barre de progression pendant l'export.
     */
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

    /**
     * Boucle d'export qui traite chaque frame de la vidéo et l'écrit dans le fichier de sortie.
     * Utilise le codec HuffYUV (HFYU) pour un export lossless rapide.
     *
     * @param outPath le chemin du fichier de sortie
     * @param processor la fonction de traitement à appliquer à chaque frame
     * @param bar la barre de progression à mettre à jour
     * @return true si l'export a réussi, false sinon
     */
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

    /**
     * Parse le texte d'un TextField en entier.
     *
     * @param field le champ texte à parser
     * @param def la valeur par défaut en cas d'erreur de parsing
     * @return la valeur parsée ou la valeur par défaut
     */
    private int parse(TextField field, int def) {
        try { return Integer.parseInt(field.getText()); }
        catch (Exception e) { return def; }
    }

    /**
     * Affiche un FileChooser pour sélectionner le fichier de destination de l'export.
     *
     * @return le fichier sélectionné ou null si annulé
     */
    private File promptForSave() {
        FileChooser fc = new FileChooser();
        // HFYU fonctionne bien avec .avi ou .mkv
        fc.setInitialFileName("video_export.avi");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("AVI Video", "*.avi"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("MKV Video", "*.mkv"));
        return fc.showSaveDialog(stage);
    }

    /**
     * Affiche une boîte de dialogue d'information.
     *
     * @param title le titre de la fenêtre
     * @param content le message à afficher
     */
    private void showAlert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content);
        a.showAndWait();
    }

    /**
     * Crée une fenêtre de dialogue avec une barre de progression pour l'export.
     *
     * @param bar la barre de progression à afficher
     * @return l'Alert configurée
     */
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

    /**
     * Convertit une Mat OpenCV en Image JavaFX pour l'affichage.
     * Gère les images en niveaux de gris et en couleur (BGR).
     *
     * @param mat la matrice OpenCV à convertir
     * @return l'Image JavaFX correspondante
     */
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

    /**
     * Classe interne représentant le contexte d'un onglet de traitement.
     * Encapsule les labels, boutons, champs de saisie et la fonction de traitement.
     */
    private class TabContext {
        String inputLabel, outputLabel;
        Button openButton, playButton, prevButton, nextButton, exportButton, autoButton;
        Function<Mat, Mat> processor;
        List<TextField> inputs = new ArrayList<>();

        /**
         * Construit un contexte d'onglet avec ses composants et son processeur.
         *
         * @param inLbl le label pour la vidéo d'entrée
         * @param outLbl le label pour la vidéo de sortie
         * @param open le bouton d'ouverture
         * @param play le bouton lecture/pause
         * @param prev le bouton frame précédente
         * @param next le bouton frame suivante
         * @param export le bouton d'export
         * @param auto le bouton de détection automatique (peut être null)
         * @param proc la fonction de traitement à appliquer aux frames
         */
        public TabContext(String inLbl, String outLbl,
                          Button open, Button play, Button prev, Button next, Button export, Button auto,
                          Function<Mat, Mat> proc) {
            this.inputLabel = inLbl; this.outputLabel = outLbl;
            this.openButton = open; this.playButton = play;
            this.prevButton = prev; this.nextButton = next;
            this.exportButton = export; this.autoButton = auto;
            this.processor = proc;
        }

        /**
         * Ajoute des champs de saisie au contexte et configure des listeners pour rafraîchir l'affichage.
         *
         * @param fields les champs de saisie à ajouter
         * @return ce TabContext pour chaînage
         */
        public TabContext addInputs(TextField... fields) {
            for (TextField f : fields) {
                this.inputs.add(f);
                f.textProperty().addListener((o, old, val) -> refreshDisplay());
            }
            return this;
        }

        /**
         * Active ou désactive tous les contrôles de cet onglet.
         *
         * @param disabled true pour désactiver, false pour activer
         */
        public void setControlsDisabled(boolean disabled) {
            if (openButton != null) openButton.setDisable(disabled);
            if (playButton != null) playButton.setDisable(disabled);
            if (prevButton != null) prevButton.setDisable(disabled);
            if (nextButton != null) nextButton.setDisable(disabled);
            if (exportButton != null) exportButton.setDisable(disabled);
            if (autoButton != null) autoButton.setDisable(disabled);
        }
    }

    /**
     * Point d'entrée principal de l'application.
     *
     * @param args arguments de la ligne de commande
     */
    public static void main(String[] args) { launch(args); }
}