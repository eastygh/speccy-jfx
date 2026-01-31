package spectrum.jfx.ui.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import spectrum.hardware.machine.Machine;
import spectrum.hardware.tape.events.CassetteDeckEvent;
import spectrum.hardware.tape.flash.FlashTapLoader;
import spectrum.hardware.tape.model.TapeFile;
import spectrum.hardware.tape.model.TapeSection;
import spectrum.hardware.tape.record.RecordingState;
import spectrum.hardware.tape.record.TapeRecordListener;
import spectrum.hardware.tape.tap.TapBlock;
import spectrum.jfx.ui.localization.LocalizationManager;
import spectrum.jfx.ui.localization.LocalizationManager.LocalizationChangeListener;
import spectrum.jfx.ui.model.TapeCollection;
import spectrum.jfx.ui.settings.AppSettings;
import spectrum.jfx.ui.theme.ThemeManager;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

import static spectrum.hardware.tape.flash.FlashTapLoader.triggerLoadCommand;
import static spectrum.jfx.ui.util.TapeFileParser.parseTapeFile;

@Slf4j
public class TapeLibraryController implements Initializable, LocalizationChangeListener, CassetteDeckEvent, TapeRecordListener {

    // Toolbar controls
    @FXML
    private Button playButton;
    @FXML
    private Button stopButton;
    @FXML
    private Button recordButton;
    @FXML
    private Button saveRecordingButton;
    @FXML
    private Button flashLoad;
    @FXML
    private Button fastLoad;
    @FXML
    private Button addFileButton;
    @FXML
    private Button removeFileButton;
    @FXML
    private Button clearAllButton;
    @FXML
    public CheckBox toggleTapeSound;
    @FXML
    private Label statusLabel;

    // File list (left panel)
    @FXML
    private ListView<TapeFile> fileListView;
    @FXML
    private Label collectionInfoLabel;
    @FXML
    private Label collectionSizeLabel;

    // File info and sections (right panel)
    @FXML
    private Label fileInfoLabel;
    @FXML
    private Label fileTypeLabel;
    @FXML
    private Label fileSizeLabel;
    @FXML
    private ListView<TapeSection> sectionListView;
    @FXML
    private Button gotoSectionButton;
    @FXML
    private Label sectionInfoLabel;

    // Status bar
    @FXML
    private Label playbackStatusLabel;
    @FXML
    private HBox recordingIndicatorBox;
    @FXML
    private Circle recordingCircle;
    @FXML
    private Label recordingStatusLabel;
    @FXML
    private Separator recordingSeparator;
    @FXML
    private Label currentFileLabel;
    @FXML
    private Label currentSectionLabel;
    @FXML
    private Label recordedBlocksLabel;
    @FXML
    private ProgressBar playbackProgressBar;

    // Recording panel
    @FXML
    private VBox sectionsPanel;
    @FXML
    private VBox recordingPanel;
    @FXML
    private ListView<TapBlock> recordedBlocksListView;
    @FXML
    private Button clearRecordingButton;

    private TapeCollection tapeCollection;
    private ObservableList<TapeFile> fileObservableList;
    private ObservableList<TapeSection> sectionObservableList;
    private ObservableList<TapBlock> recordedBlocksObservableList;

    @Setter
    private Stage stage;

    private LocalizationManager localizationManager;
    private boolean isPlaying = false;
    private boolean isRecording = false;
    private Timeline recordingBlinkAnimation;
    private TapeFile currentFile = null;
    private TapeSection currentSection = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        AppSettings settings = AppSettings.getInstance();

        // Initialize LocalizationManager and register listener
        localizationManager = LocalizationManager.getInstance();
        localizationManager.addLanguageChangeListener(this);

        // Load collection from settings
        tapeCollection = settings.getTapeCollection();
        if (tapeCollection == null) {
            tapeCollection = new TapeCollection();
        }

        // Set localized name if name is empty
        if (tapeCollection.getName() == null || tapeCollection.getName().isEmpty()) {
            tapeCollection.setName(localizationManager.getString("collection.defaultName"));
        }

        // Initialize lists - create new ObservableList and fill it from collection
        fileObservableList = FXCollections.observableArrayList();
        fileObservableList.addAll(tapeCollection.getFiles());

        sectionObservableList = FXCollections.observableArrayList();

        fileListView.setItems(fileObservableList);
        sectionListView.setItems(sectionObservableList);

        // Configure file display in list
        fileListView.setCellFactory(listView -> new ListCell<TapeFile>() {
            @Override
            protected void updateItem(TapeFile file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String typeDisplayName = localizationManager.getString(file.getType().getLocalizationKey());
                    setText(String.format("%s (%s) - %s",
                            file.getFileName(),
                            typeDisplayName,
                            localizationManager.getString("sections.count", file.getSections().size())));
                }
            }
        });

        // Настраиваем отображение секций в списке
        sectionListView.setCellFactory(listView -> new ListCell<TapeSection>() {
            @Override
            protected void updateItem(TapeSection section, boolean empty) {
                super.updateItem(section, empty);
                if (empty || section == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String titleText = localizeTitle(section.getTitle(), section.getType());
                    String typeDisplayName = localizationManager.getString(section.getType().getLocalizationKey());

                    setText(localizationManager.getString("section.format",
                            String.format("%02d", section.getIndex()),
                            titleText,
                            typeDisplayName,
                            String.valueOf(section.getLength())));

                    if (!section.isPlayable()) {
                        setStyle("-fx-text-fill: gray;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        //
        fileListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldFile, newFile) -> onFileSelected(newFile));

        sectionListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSection, newSection) -> onSectionSelected(newSection));

        // Open hex editor on double click
        sectionListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TapeSection selectedSection = sectionListView.getSelectionModel().getSelectedItem();
                TapeFile tapFile = new TapeFile(currentFile.getFilePath());
                parseTapeFile(tapFile);
                TapeSection viewSection = tapFile.getSections().get(selectedSection.getIndex() - 1);
                if (viewSection != null && viewSection.getData() != null) {
                    openHexEditor(viewSection);
                } else if (viewSection != null) {
                    showWarning(localizationManager.getString("warning.sectionNoData"));
                }
            }
        });

        // Изначально отключаем кнопки воспроизведения
        updatePlaybackControls(false);

        // Initialize recorded blocks list
        recordedBlocksObservableList = FXCollections.observableArrayList();
        recordedBlocksListView.setItems(recordedBlocksObservableList);
        recordedBlocksListView.setCellFactory(listView -> new ListCell<TapBlock>() {
            @Override
            protected void updateItem(TapBlock block, boolean empty) {
                super.updateItem(block, empty);
                if (empty || block == null) {
                    setText(null);
                    setStyle("");
                } else {
                    int index = getIndex() + 1;
                    String type = block.isHeader()
                            ? localizationManager.getString("recording.headerBlock")
                            : localizationManager.getString("recording.dataBlock");
                    String checksum = block.isChecksumValid()
                            ? localizationManager.getString("recording.checksumOk")
                            : localizationManager.getString("recording.checksumFail");

                    String name = block.getFilename();
                    String displayType = name != null ? type + ": " + name : type;

                    setText(localizationManager.getString("recording.blockInfo",
                            index, displayType, block.getBlockLength(), checksum));

                    if (!block.isChecksumValid()) {
                        setStyle("-fx-text-fill: red;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Initialize blinking animation for recording indicator
        recordingBlinkAnimation = new Timeline(
                new KeyFrame(Duration.millis(500), e -> {
                    recordingCircle.setVisible(!recordingCircle.isVisible());
                })
        );
        recordingBlinkAnimation.setCycleCount(Animation.INDEFINITE);

        // Обновляем информацию о коллекции
        updateCollectionInfo();

        Machine.withCassetteDeck((cd, hp) -> {
            cd.addCassetteDeckEventListener(this);
            cd.addRecordListener(this);
            cd.setSoundPushBack(settings.isEmulateTapeSound());
            toggleTapeSound.setSelected(settings.isEmulateTapeSound());
        });

    }

    private void onFileSelected(TapeFile file) {
        currentFile = file;
        sectionObservableList.clear();

        if (file != null) {
            // Отображаем информацию о файле
            fileInfoLabel.setText(file.getFileName());
            String typeDisplayName = localizationManager.getString(file.getType().getLocalizationKey());
            fileTypeLabel.setText(typeDisplayName);
            fileSizeLabel.setText(formatFileSize(file.getFileSize()));

            // Загружаем секции файла
            sectionObservableList.addAll(file.getSections());
        } else {
            fileInfoLabel.setText(localizationManager.getString("tape.noFileSelected"));
            fileTypeLabel.setText("");
            fileSizeLabel.setText("");
        }

        updateSectionInfo();
    }

    private void onSectionSelected(TapeSection section) {
        currentSection = section;
        gotoSectionButton.setDisable(section == null || !section.isPlayable());
        updateSectionInfo();
    }

    private void updateSectionInfo() {
        if (currentSection != null) {
            String localizedTitle = localizeTitle(currentSection.getTitle(), currentSection.getType());
            sectionInfoLabel.setText(localizationManager.getString("tape.section",
                    currentSection.getIndex(),
                    localizedTitle));
        } else {
            sectionInfoLabel.setText("");
        }
    }

    private void updateCollectionInfo() {
        collectionInfoLabel.setText(localizationManager.getString("tape.files",
                tapeCollection.getTotalFiles(),
                tapeCollection.getTotalSections()));

        collectionSizeLabel.setText(localizationManager.getString("tape.size", formatFileSize(tapeCollection.getTotalSize())));
    }

    private void updatePlaybackControls(boolean playing) {
        isPlaying = playing;
        playButton.setDisable(playing || isRecording);
        stopButton.setDisable(!playing && !isRecording);
        recordButton.setDisable(playing || isRecording);

        fastLoad.setDisable(playing || isRecording);
        flashLoad.setDisable(playing || isRecording);

        // Disable collection management during playback/recording
        addFileButton.setDisable(isRecording);
        removeFileButton.setDisable(isRecording);
        clearAllButton.setDisable(isRecording);

        // Disable file/section lists during recording
        fileListView.setDisable(isRecording);
        sectionListView.setDisable(isRecording);

        playbackStatusLabel.setText(playing ? localizationManager.getString("playback.playing") : localizationManager.getString("playback.stopped"));
        playbackProgressBar.setVisible(playing);
    }

    private void updateRecordingControls(boolean recording) {
        isRecording = recording;
        playButton.setDisable(isPlaying || recording);
        stopButton.setDisable(!isPlaying && !recording);
        recordButton.setDisable(isPlaying || recording);

        fastLoad.setDisable(isPlaying || recording);
        flashLoad.setDisable(isPlaying || recording);

        // Disable collection management during recording
        addFileButton.setDisable(recording);
        removeFileButton.setDisable(recording);
        clearAllButton.setDisable(recording);

        // Disable file/section lists during recording
        fileListView.setDisable(recording);

        // Update recording status indicator with blinking
        recordingIndicatorBox.setVisible(recording);
        recordingIndicatorBox.setManaged(recording);
        recordingSeparator.setVisible(recording);
        recordingSeparator.setManaged(recording);

        // Switch between sections panel and recording panel
        sectionsPanel.setVisible(!recording);
        sectionsPanel.setManaged(!recording);
        recordingPanel.setVisible(recording || recordedBlocksObservableList.size() > 0);
        recordingPanel.setManaged(recording || recordedBlocksObservableList.size() > 0);

        if (recording) {
            // Start blinking animation
            recordingCircle.setVisible(true);
            recordingBlinkAnimation.play();
            statusLabel.setText(localizationManager.getString("recording.started"));
        } else {
            // Stop blinking animation
            recordingBlinkAnimation.stop();
            recordingCircle.setVisible(true);
            statusLabel.setText(localizationManager.getString("recording.stopped"));
        }
    }

    private void updateRecordedBlocksDisplay(int blockCount) {
        Platform.runLater(() -> {
            if (blockCount > 0) {
                recordedBlocksLabel.setText(localizationManager.getString("recording.blocks", blockCount));
                saveRecordingButton.setDisable(false);
            } else {
                recordedBlocksLabel.setText("");
                saveRecordingButton.setDisable(true);
            }
        });
    }

    // Normal tape playback
    @FXML
    @SneakyThrows
    private void onPlay() {
        currentFile = fileListView.getSelectionModel().getSelectedItem();
        currentSection = sectionListView.getSelectionModel().getSelectedItem();
        if (currentFile != null) {
            if (currentSection == null) {
                currentSection = currentFile.getFirstPlayableSection();
                if (currentSection == null) {
                    showWarning(localizationManager.getString("warning.noPlayableSection"));
                    return;
                }
            }

            updatePlaybackControls(true);
            currentFileLabel.setText(localizationManager.getString("playback.file.prefix", currentFile.getFileName()));
            currentSectionLabel.setText(localizationManager.getString("playback.section.prefix", currentSection.getIndex()));
            statusLabel.setText(localizationManager.getString("playback.playingSection", currentSection.getIndex()));

            log.info("Playing: " + currentFile.getFileName() +
                    ", section " + currentSection.getIndex());
            Machine.withCassetteDeck((cd, hv) -> {
                if (cd != null) {
                    // Create a new instance tapFile to get binary data
                    TapeFile tapeFile = new TapeFile(currentFile.getFilePath());
                    parseTapeFile(tapeFile);
                    triggerLoadCommand(hv.getMemory(), hv.getCPU());
                    cd.insertTape(tapeFile);
                    cd.setSectionIndex(currentSection.getIndex() - 1);
                    cd.setMotor(true);
                }
            });
        } else {
            showWarning(localizationManager.getString("warning.selectPlayableSection"));
        }
    }

    @FXML
    private void onStop() {
        if (isRecording) {
            Machine.withCassetteDeck((cassetteDeck, hardwareProvider) -> {
                if (cassetteDeck != null) {
                    cassetteDeck.stopRecording();
                }
            });
            Platform.runLater(() -> updateRecordingControls(false));
            log.info("Stopping recording");
            return;
        }

        setSpeedUpMode(false);
        updatePlaybackControls(false);
        currentFileLabel.setText("");
        currentSectionLabel.setText("");
        statusLabel.setText(localizationManager.getString("playback.stopped"));
        Machine.withCassetteDeck((cassetteDeck, hardwareProvider) -> {
            if (cassetteDeck != null) {
                cassetteDeck.setMotor(false);
            }
        });
        log.info("Stopping playback");
    }

    @FXML
    private void onRecord() {
        Machine.withCassetteDeck((cassetteDeck, hardwareProvider) -> {
            if (cassetteDeck != null) {
                // Check if there are already recorded blocks
                if (cassetteDeck.getRecordedBlockCount() > 0) {
                    Platform.runLater(() -> {
                        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
                        confirmation.setTitle(localizationManager.getString("confirm.clearRecording.title"));
                        confirmation.setHeaderText(localizationManager.getString("confirm.clearRecording.header"));
                        confirmation.setContentText(localizationManager.getString("confirm.clearRecording.content",
                                cassetteDeck.getRecordedBlockCount()));
                        ThemeManager.applyThemeToDialog(confirmation);

                        confirmation.showAndWait().ifPresent(response -> {
                            if (response == ButtonType.OK) {
                                Machine.withCassetteDeck((cd, hp) -> {
                                    cd.clearRecording();
                                    cd.startRecording();
                                });
                                recordedBlocksObservableList.clear();
                                updateRecordingControls(true);
                                updateRecordedBlocksDisplay(0);
                            }
                        });
                    });
                } else {
                    cassetteDeck.startRecording();
                    Platform.runLater(() -> {
                        recordedBlocksObservableList.clear();
                        updateRecordingControls(true);
                        updateRecordedBlocksDisplay(0);
                    });
                }
            }
        });
        log.info("Starting recording");
    }

    @FXML
    private void onSaveRecording() {
        Machine.withCassetteDeck((cassetteDeck, hardwareProvider) -> {
            if (cassetteDeck == null || cassetteDeck.getRecordedBlockCount() == 0) {
                Platform.runLater(() -> showWarning(localizationManager.getString("recording.noBlocks")));
                return;
            }

            Platform.runLater(() -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle(localizationManager.getString("recording.saveTitle"));
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(localizationManager.getString("filechooser.tap"), "*.tap")
                );
                fileChooser.setInitialFileName("recording.tap");

                AppSettings settings = AppSettings.getInstance();
                if (!settings.getLastSnapshotPath().isEmpty()) {
                    File lastDir = new File(settings.getLastSnapshotPath()).getParentFile();
                    if (lastDir != null && lastDir.exists()) {
                        fileChooser.setInitialDirectory(lastDir);
                    }
                }

                File file = fileChooser.showSaveDialog(stage);
                if (file != null) {
                    try {
                        Machine.withCassetteDeck((cd, hp) -> {
                            try {
                                cd.saveRecordedTape(file.getAbsolutePath());
                                Platform.runLater(() -> {
                                    statusLabel.setText(localizationManager.getString("recording.saved", file.getName()));
                                    // Optionally add to collection
                                    addFileToCollection(file);
                                });
                            } catch (Exception e) {
                                Platform.runLater(() -> showError(localizationManager.getString("error.message", e.getMessage())));
                            }
                        });
                        settings.saveLastSnapshotPath(file.getAbsolutePath());
                    } catch (Exception e) {
                        showError(localizationManager.getString("error.message", e.getMessage()));
                    }
                }
            });
        });
    }

    @FXML
    private void onAddFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(localizationManager.getString("filechooser.tapes"));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(localizationManager.getString("filechooser.tapes"), "*.tap", "*.tzx"),
                new FileChooser.ExtensionFilter(localizationManager.getString("filechooser.tap"), "*.tap"),
                new FileChooser.ExtensionFilter(localizationManager.getString("filechooser.tzx"), "*.tzx"),
                new FileChooser.ExtensionFilter(localizationManager.getString("filechooser.all"), "*.*")
        );

        //Last recent path
        AppSettings settings = AppSettings.getInstance();
        if (!settings.getLastSnapshotPath().isEmpty()) {
            File lastDir = new File(settings.getLastSnapshotPath()).getParentFile();
            if (lastDir != null && lastDir.exists()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            addFileToCollection(file);
            settings.saveLastSnapshotPath(file.getAbsolutePath());
        }
    }

    private void addFileToCollection(File file) {
        if (tapeCollection.containsFile(file.getAbsolutePath())) {
            showWarning(localizationManager.getString("tape.fileAlreadyExists"));
            return;
        }

        TapeFile tapeFile = new TapeFile(file.getAbsolutePath());
        tapeFile.setFileSize(file.length());

        // Parse file
        try {
            parseTapeFile(tapeFile);

            // add to tapes collection
            tapeCollection.addFile(tapeFile);

            // Add to view
            fileObservableList.add(tapeFile);

            updateCollectionInfo();
            saveCollection();
            statusLabel.setText(localizationManager.getString("tape.fileAdded", file.getName()));
        } catch (Exception e) {
            showError(localizationManager.getString("tape.errorAddingFile", e.getMessage()));
        }
    }

    @FXML
    private void onRemoveFile() {
        TapeFile selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            tapeCollection.removeFile(selected);
            fileObservableList.remove(selected);
            updateCollectionInfo();
            saveCollection();
            statusLabel.setText(localizationManager.getString("tape.fileRemoved", selected.getFileName()));

            // clear section view
            if (currentFile == selected) {
                onFileSelected(null);
            }
        } else {
            showWarning(localizationManager.getString("warning.selectFileToRemove"));
        }
    }

    @FXML
    private void onClearAll() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle(localizationManager.getString("confirm.clearCollection.title"));
        confirmation.setHeaderText(localizationManager.getString("confirm.clearCollection.header"));
        confirmation.setContentText(localizationManager.getString("confirm.clearCollection.content"));

        // apply theme
        ThemeManager.applyThemeToDialog(confirmation);

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                tapeCollection.clear();
                fileObservableList.clear();
                sectionObservableList.clear();
                onFileSelected(null);
                updateCollectionInfo();
                saveCollection();
                statusLabel.setText(localizationManager.getString("tape.collectionCleared"));
            }
        });
    }

    @FXML
    private void onGotoSection() {
        if (currentSection != null && currentSection.isPlayable()) {
            statusLabel.setText(localizationManager.getString("playback.gotoingSection", currentSection.getIndex()));
            // TODO: Integration with emulator for section navigation
            log.info("Going to section " + currentSection.getIndex());
        }
    }

    private void saveCollection() {
        AppSettings.getInstance().saveTapeCollection(tapeCollection);
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(localizationManager.getString("warning.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        ThemeManager.applyThemeToDialog(alert);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(localizationManager.getString("error.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        ThemeManager.applyThemeToDialog(alert);
        alert.showAndWait();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " " + localizationManager.getString("filesize.bytes");
        if (bytes < 1024 * 1024)
            return String.format("%.1f %s", bytes / 1024.0, localizationManager.getString("filesize.kb"));
        return String.format("%.1f %s", bytes / (1024.0 * 1024.0), localizationManager.getString("filesize.mb"));
    }

    // Реализация LocalizationChangeListener
    @Override
    public void onLanguageChanged(LocalizationManager.Language newLanguage) {
        Platform.runLater(this::updateAllUITexts);
    }

    private void updateAllUITexts() {
        // Обновляем имя коллекции если оно было пустым (значит, использовалось по умолчанию)
        if (tapeCollection.getName() == null || tapeCollection.getName().isEmpty() ||
                tapeCollection.getName().equals("My Tape Collection") || tapeCollection.getName().equals("Моя коллекция кассет")) {
            tapeCollection.setName(localizationManager.getString("collection.defaultName"));
        }

        // Обновляем информацию о коллекции
        updateCollectionInfo();

        // Обновляем статус воспроизведения
        updatePlaybackControls(isPlaying);

        // Обновляем информацию о секции
        updateSectionInfo();

        // Обновляем статус файла, если файл выбран
        if (currentFile == null) {
            fileInfoLabel.setText(localizationManager.getString("tape.noFileSelected"));
        } else {
            // Обновляем тип файла с локализацией
            String typeDisplayName = localizationManager.getString(currentFile.getType().getLocalizationKey());
            fileTypeLabel.setText(typeDisplayName);
        }

        // Обновляем списки файлов и секций для перерисовки
        fileListView.refresh();
        sectionListView.refresh();

        // Обновляем статус по умолчанию если нет воспроизведения
        if (!isPlaying) {
            statusLabel.setText(localizationManager.getString("tape.ready"));
        }
    }

    /**
     * Локализует стандартные названия секций из парсера
     */
    private String localizeTitle(String title, TapeSection.SectionType type) {
        if (title == null || title.isEmpty()) {
            return localizationManager.getString("section.untitled");
        }

        // Локализуем стандартные названия от парсера
        switch (title) {
            case "Data":
                return localizationManager.getString("parser.data.block");
            case "Standard Data":
                return localizationManager.getString("parser.data.standard");
            case "Turbo Data":
                return localizationManager.getString("parser.data.turbo");
            default:
                // Для сложных форматов (паузы, описания, блоки)
                if (title.startsWith("Pause (") && title.endsWith(" ms)")) {
                    // Извлекаем число миллисекунд из "Pause (XXX ms)"
                    String msValue = title.substring(7, title.length() - 4);
                    return localizationManager.getString("parser.pause.format", msValue);
                }
                if (title.startsWith("Description: ")) {
                    // Извлекаем текст описания из "Description: XXX"
                    String description = title.substring(13);
                    return localizationManager.getString("parser.description.format", description);
                }
                if (title.startsWith("Unknown block 0x") || title.startsWith("Block 0x")) {
                    // Извлекаем hex код из "Unknown block 0xXX" или "Block 0xXX"
                    String hexCode = title.substring(title.indexOf("0x") + 2);
                    if (title.startsWith("Unknown")) {
                        return localizationManager.getString("parser.unknown.block", hexCode);
                    } else {
                        return localizationManager.getString("parser.block.format", hexCode);
                    }
                }

                // Для остальных (имена программ и т.д.) возвращаем как есть
                return title;
        }
    }

    /**
     * Открывает HEX редактор для выбранной секции
     */
    private void openHexEditor(TapeSection section) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/controller/hex-editor-view.fxml"));

            // Set the resource bundle for the FXML loader
            loader.setResources(java.util.ResourceBundle.getBundle("i18n/messages", localizationManager.getCurrentLanguage().getLocale()));

            // Загружаем корневой элемент (BorderPane)
            Object root = loader.load();

            // Создаем Scene из корневого элемента
            Scene hexScene = new Scene((javafx.scene.Parent) root, 900, 700);

            HexEditorController hexController = loader.getController();

            Stage hexStage = new Stage();
            hexStage.setTitle(localizationManager.getString("hex.editor.title",
                    section.getIndex(),
                    localizeTitle(section.getTitle(), section.getType())));

            // Применяем текущую тему к новому окну
            ThemeManager.applyCurrentTheme(hexScene);

            hexStage.setScene(hexScene);
            hexStage.setMinWidth(700);
            hexStage.setMinHeight(500);

            hexController.setStage(hexStage);
            hexController.setSectionData(section);

            // Устанавливаем иконку (если есть)
            if (stage != null) {
                hexStage.getIcons().addAll(stage.getIcons());
            }

            hexStage.show();

        } catch (Exception e) {
            showError(localizationManager.getString("error.message", e.getMessage()));
        }
    }

    @Override
    public void onTapeChanged(TapeFile tape) {

    }

    @Override
    public void onTapeSectionChanged(int sectionIndex, TapeFile tape) {
        Platform.runLater(() -> {
            fileListView.getSelectionModel().clearSelection();
            fileListView.getSelectionModel().select(tape);
            fileListView.scrollTo(tape);
            sectionListView.getSelectionModel().select(sectionIndex);
            sectionListView.scrollTo(sectionIndex);
        });
    }

    @Override
    public void onTapePositionChanged(long position) {

    }

    @Override
    public void onTapeMotorChanged(boolean on) {

    }

    @Override
    public void onTapeFinished(boolean success) {
        setSpeedUpMode(false);
        Platform.runLater(() -> updatePlaybackControls(false));
    }

    @Override
    public void onTapeError(String message) {

    }

    public void onFlashLoad(ActionEvent actionEvent) {
        if (currentFile == null) {
            showError(localizationManager.getString("tape.noFileSelected"));
            return;
        }
        Machine.withHardwareProvider(
                provider -> {
                    TapeFile tapeFile = new TapeFile(currentFile.getFilePath());
                    parseTapeFile(tapeFile);
                    FlashTapLoader fastLoader = new FlashTapLoader(tapeFile, provider);
                    fastLoader.load();
                }
        );

//        fastLoader.setOnSuccess(() -> {
//            Platform.runLater(() -> {
//                statusLabel.setText(localizationManager.getString("tape.loaded"));
//                updatePlaybackControls(true);
//            });
//        })
    }

    public void onToggleTapeSound(ActionEvent actionEvent) {
        Machine.withCassetteDeck((cassetteDeck, hardwareProvider) -> {
            AppSettings settings = AppSettings.getInstance();
            settings.setEmulateTapeSound(toggleTapeSound.isSelected());
            settings.saveSettings();
            cassetteDeck.setSoundPushBack(toggleTapeSound.isSelected());
        });
    }

    public void onFastLoad(ActionEvent actionEvent) {
        setSpeedUpMode(true);
        onPlay();
    }


    private void setSpeedUpMode(boolean speedUpMode) {
        Machine.withHardwareProvider(
                hardwareProvider -> hardwareProvider.getEmulator().setSpeedUpMode(speedUpMode)
        );
    }

    // ========== TapeRecordListener Implementation ==========

    @Override
    public void onPilotDetected(int pilotCount) {
        log.debug("Pilot detected: {} pulses", pilotCount);
    }

    @Override
    public void onByteRecorded(int byteValue, int byteIndex) {
        // Called frequently during recording, no UI update needed
    }

    @Override
    public void onBlockRecorded(int flagByte, byte[] data, boolean valid) {
        log.info("Block recorded: flag=0x{}, size={}, valid={}",
                Integer.toHexString(flagByte), data.length, valid);
        Machine.withCassetteDeck((cd, hp) -> {
            Platform.runLater(() -> {
                // Add the new block to the list
                var blocks = cd.getRecordedBlocks();
                if (!blocks.isEmpty()) {
                    TapBlock lastBlock = blocks.get(blocks.size() - 1);
                    if (!recordedBlocksObservableList.contains(lastBlock)) {
                        recordedBlocksObservableList.add(lastBlock);
                    }
                }
                updateRecordedBlocksDisplay(cd.getRecordedBlockCount());
            });
        });
    }

    @Override
    public void onRecordingError(RecordingState state, String message) {
        log.warn("Recording error in state {}: {}", state, message);
    }

    @Override
    public void onRecordingStopped(int blocksRecorded) {
        log.info("Recording stopped, {} blocks recorded", blocksRecorded);
        Platform.runLater(() -> {
            updateRecordingControls(false);
            updateRecordedBlocksDisplay(blocksRecorded);
            // Show recording panel if there are recorded blocks
            if (blocksRecorded > 0) {
                sectionsPanel.setVisible(false);
                sectionsPanel.setManaged(false);
                recordingPanel.setVisible(true);
                recordingPanel.setManaged(true);
            }
        });
    }

    @FXML
    private void onClearRecording() {
        int blockCount = recordedBlocksObservableList.size();
        if (blockCount == 0) {
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle(localizationManager.getString("confirm.clearRecording.title"));
        confirmation.setHeaderText(localizationManager.getString("confirm.clearRecording.header"));
        confirmation.setContentText(localizationManager.getString("confirm.clearRecording.content", blockCount));
        ThemeManager.applyThemeToDialog(confirmation);

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Machine.withCassetteDeck((cd, hp) -> cd.clearRecording());
                recordedBlocksObservableList.clear();
                updateRecordedBlocksDisplay(0);

                // Switch back to sections panel
                sectionsPanel.setVisible(true);
                sectionsPanel.setManaged(true);
                recordingPanel.setVisible(false);
                recordingPanel.setManaged(false);

                statusLabel.setText(localizationManager.getString("tape.ready"));
            }
        });
    }
}