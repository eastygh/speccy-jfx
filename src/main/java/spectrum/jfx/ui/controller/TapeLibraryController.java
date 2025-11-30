package spectrum.jfx.ui.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.Setter;
import spectrum.jfx.hardware.tape.CassetteDeck;
import spectrum.jfx.hardware.tape.CassetteDeckEvent;
import spectrum.jfx.hardware.tape.FastTapLoader;
import spectrum.jfx.machine.Machine;
import spectrum.jfx.model.TapeFile;
import spectrum.jfx.model.TapeSection;
import spectrum.jfx.ui.localization.LocalizationManager;
import spectrum.jfx.ui.localization.LocalizationManager.LocalizationChangeListener;
import spectrum.jfx.ui.model.TapeCollection;
import spectrum.jfx.ui.settings.AppSettings;
import spectrum.jfx.ui.theme.ThemeManager;
import spectrum.jfx.ui.util.TapeFileParser;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class TapeLibraryController implements Initializable, LocalizationChangeListener, CassetteDeckEvent {

    private static final Logger logger = Logger.getLogger(TapeLibraryController.class.getName());

    // Toolbar controls
    @FXML
    private Button playButton;
    @FXML
    private Button pauseButton;
    @FXML
    private Button stopButton;
    @FXML
    private Button fastLoad;
    @FXML
    private Button addFileButton;
    @FXML
    private Button removeFileButton;
    @FXML
    private Button clearAllButton;
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
    private Label currentFileLabel;
    @FXML
    private Label currentSectionLabel;
    @FXML
    private ProgressBar playbackProgressBar;

    private TapeCollection tapeCollection;
    private ObservableList<TapeFile> fileObservableList;
    private ObservableList<TapeSection> sectionObservableList;

    @Setter
    private Stage stage;

    private LocalizationManager localizationManager;
    private boolean isPlaying = false;
    private TapeFile currentFile = null;
    private TapeSection currentSection = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize LocalizationManager and register listener
        localizationManager = LocalizationManager.getInstance();
        localizationManager.addLanguageChangeListener(this);

        // Load collection from settings
        tapeCollection = AppSettings.getInstance().getTapeCollection();
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

        // Обработчики выбора
        fileListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldFile, newFile) -> onFileSelected(newFile));

        sectionListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSection, newSection) -> onSectionSelected(newSection));

        // Обработчик двойного клика для открытия HEX редактора
        sectionListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TapeSection selectedSection = sectionListView.getSelectionModel().getSelectedItem();

                if (selectedSection != null && selectedSection.getData() != null) {
                    openHexEditor(selectedSection);
                } else if (selectedSection != null) {
                    showWarning(localizationManager.getString("warning.sectionNoData"));
                }
            }
        });

        // Изначально отключаем кнопки воспроизведения
        updatePlaybackControls(false);

        // Обновляем информацию о коллекции
        updateCollectionInfo();

        Machine.withHardwareProvider(provider -> provider.getCassetteDeck().addCassetteDeckEventListener(this));

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
        playButton.setDisable(playing);
        pauseButton.setDisable(!playing);
        stopButton.setDisable(!playing);

        playbackStatusLabel.setText(playing ? localizationManager.getString("playback.playing") : localizationManager.getString("playback.stopped"));
        playbackProgressBar.setVisible(playing);
    }

    // Обработчики событий
    @FXML
    private void onPlay() {
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

            logger.info("Playing: " + currentFile.getFileName() +
                    ", section " + currentSection.getIndex());
            Machine.withHardwareProvider(hardwareProvider -> {
                CassetteDeck cassetteDeck = hardwareProvider.getCassetteDeck();
                if (cassetteDeck != null) {
                    cassetteDeck.insertTape(currentFile);
                    cassetteDeck.setSectionIndex(currentSection.getIndex());
                    cassetteDeck.setMotor(true);
                }
            });
        } else {
            showWarning(localizationManager.getString("warning.selectPlayableSection"));
        }
    }

    @FXML
    private void onPause() {
        if (isPlaying) {
            updatePlaybackControls(false);
            statusLabel.setText(localizationManager.getString("playback.paused"));
            // TODO: Integration with emulator for pause
            logger.info("Pausing playback");
        }
    }

    @FXML
    private void onStop() {
        updatePlaybackControls(false);
        currentFileLabel.setText("");
        currentSectionLabel.setText("");
        statusLabel.setText(localizationManager.getString("playback.stopped"));
        // TODO: Integration with emulator for stop
        logger.info("Stopping playback");
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

        // Устанавливаем последний использованный путь
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

        // Парсим файл для получения секций
        try {
            TapeFileParser.parseTapeFile(tapeFile);

            // Добавляем в коллекцию
            tapeCollection.addFile(tapeFile);

            // Добавляем в UI список
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

            // Очищаем правую панель
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

        // Применяем тему к диалогу
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
            logger.info("Going to section " + currentSection.getIndex());
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/hex-editor-view.fxml"));

            // Set the resource bundle for the FXML loader
            loader.setResources(java.util.ResourceBundle.getBundle("messages", localizationManager.getCurrentLanguage().getLocale()));

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
    public void onTapeEndReached() {

    }

    @Override
    public void onTapeError(String message) {

    }

    public void onFastLoad(ActionEvent actionEvent) {
        if (currentFile == null) {
            showError(localizationManager.getString("tape.noFileSelected"));
            return;
        }
        Machine.withHardwareProvider(
                provider -> {
                    provider.getEmulator().pause();
                    FastTapLoader fastLoader = new FastTapLoader(currentFile, provider.getMemory());
                    fastLoader.load();
                    provider.getEmulator().resume();
                }
        );

//        fastLoader.setOnSuccess(() -> {
//            Platform.runLater(() -> {
//                statusLabel.setText(localizationManager.getString("tape.loaded"));
//                updatePlaybackControls(true);
//            });
//        })
    }
}