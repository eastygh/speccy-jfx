package spectrum.jfx.ui.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.Setter;
import spectrum.jfx.ui.model.TapeCollection;
import spectrum.jfx.ui.model.TapeFile;
import spectrum.jfx.ui.model.TapeSection;
import spectrum.jfx.ui.settings.AppSettings;
import spectrum.jfx.ui.theme.ThemeManager;
import spectrum.jfx.ui.util.TapeFileParser;

import java.io.File;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class TapeLibraryController implements Initializable {

    // Toolbar controls
    @FXML private Button playButton;
    @FXML private Button pauseButton;
    @FXML private Button stopButton;
    @FXML private Button addFileButton;
    @FXML private Button removeFileButton;
    @FXML private Button clearAllButton;
    @FXML private Label statusLabel;

    // File list (left panel)
    @FXML private ListView<TapeFile> fileListView;
    @FXML private Label collectionInfoLabel;
    @FXML private Label collectionSizeLabel;

    // File info and sections (right panel)
    @FXML private Label fileInfoLabel;
    @FXML private Label fileTypeLabel;
    @FXML private Label fileSizeLabel;
    @FXML private ListView<TapeSection> sectionListView;
    @FXML private Button gotoSectionButton;
    @FXML private Label sectionInfoLabel;

    // Status bar
    @FXML private Label playbackStatusLabel;
    @FXML private Label currentFileLabel;
    @FXML private Label currentSectionLabel;
    @FXML private ProgressBar playbackProgressBar;

    private TapeCollection tapeCollection;
    private ObservableList<TapeFile> fileObservableList;
    private ObservableList<TapeSection> sectionObservableList;

    @Setter
    private Stage stage;

    private boolean isPlaying = false;
    private TapeFile currentFile = null;
    private TapeSection currentSection = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Загружаем коллекцию из настроек
        tapeCollection = AppSettings.getInstance().getTapeCollection();
        if (tapeCollection == null) {
            tapeCollection = new TapeCollection();
        }

        // Инициализируем списки - создаем новый ObservableList и заполняем его из коллекции
        fileObservableList = FXCollections.observableArrayList();
        fileObservableList.addAll(tapeCollection.getFiles());

        sectionObservableList = FXCollections.observableArrayList();

        fileListView.setItems(fileObservableList);
        sectionListView.setItems(sectionObservableList);

        // Настраиваем отображение файлов в списке
        fileListView.setCellFactory(listView -> new ListCell<TapeFile>() {
            @Override
            protected void updateItem(TapeFile file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(String.format("%s (%s) - %d секций",
                        file.getFileName(),
                        file.getType().getDisplayName(),
                        file.getSections().size()));
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
                    setText(section.toString());
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

        // Изначально отключаем кнопки воспроизведения
        updatePlaybackControls(false);

        // Обновляем информацию о коллекции
        updateCollectionInfo();
    }

    private void onFileSelected(TapeFile file) {
        currentFile = file;
        sectionObservableList.clear();

        if (file != null) {
            // Отображаем информацию о файле
            fileInfoLabel.setText(file.getFileName());
            fileTypeLabel.setText(file.getType().getDisplayName());
            fileSizeLabel.setText(formatFileSize(file.getFileSize()));

            // Загружаем секции файла
            sectionObservableList.addAll(file.getSections());
        } else {
            fileInfoLabel.setText("Файл не выбран");
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
            sectionInfoLabel.setText(String.format("Секция %d: %s",
                currentSection.getIndex(),
                currentSection.getTitle() != null ? currentSection.getTitle() : "Без названия"));
        } else {
            sectionInfoLabel.setText("");
        }
    }

    private void updateCollectionInfo() {
        collectionInfoLabel.setText(String.format("Файлов: %d, Секций: %d",
            tapeCollection.getTotalFiles(),
            tapeCollection.getTotalSections()));

        collectionSizeLabel.setText("Размер: " + formatFileSize(tapeCollection.getTotalSize()));
    }

    private void updatePlaybackControls(boolean playing) {
        isPlaying = playing;
        playButton.setDisable(playing);
        pauseButton.setDisable(!playing);
        stopButton.setDisable(!playing);

        playbackStatusLabel.setText(playing ? "Воспроизведение" : "Остановлено");
        playbackProgressBar.setVisible(playing);
    }

    // Обработчики событий
    @FXML
    private void onPlay() {
        if (currentFile != null && currentSection != null && currentSection.isPlayable()) {
            updatePlaybackControls(true);
            currentFileLabel.setText("Файл: " + currentFile.getFileName());
            currentSectionLabel.setText("Секция: " + currentSection.getIndex());
            statusLabel.setText("Воспроизведение секции " + currentSection.getIndex());

            // TODO: Интеграция с эмулятором для воспроизведения
            System.out.println("Воспроизведение: " + currentFile.getFileName() +
                             ", секция " + currentSection.getIndex());
        } else {
            showWarning("Выберите файл и воспроизводимую секцию");
        }
    }

    @FXML
    private void onPause() {
        if (isPlaying) {
            updatePlaybackControls(false);
            statusLabel.setText("Пауза");
            // TODO: Интеграция с эмулятором для паузы
            System.out.println("Пауза воспроизведения");
        }
    }

    @FXML
    private void onStop() {
        updatePlaybackControls(false);
        currentFileLabel.setText("");
        currentSectionLabel.setText("");
        statusLabel.setText("Остановлено");
        // TODO: Интеграция с эмулятором для остановки
        System.out.println("Остановка воспроизведения");
    }

    @FXML
    private void onAddFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Добавить файл в коллекцию");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Файлы кассет", "*.tap", "*.tzx"),
            new FileChooser.ExtensionFilter("TAP файлы", "*.tap"),
            new FileChooser.ExtensionFilter("TZX файлы", "*.tzx"),
            new FileChooser.ExtensionFilter("Все файлы", "*.*")
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
            showWarning("Файл уже есть в коллекции");
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
            statusLabel.setText("Добавлен: " + file.getName());
        } catch (Exception e) {
            showError("Ошибка при добавлении файла: " + e.getMessage());
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
            statusLabel.setText("Удален: " + selected.getFileName());

            // Очищаем правую панель
            if (currentFile == selected) {
                onFileSelected(null);
            }
        } else {
            showWarning("Выберите файл для удаления");
        }
    }

    @FXML
    private void onClearAll() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Подтверждение");
        confirmation.setHeaderText("Очистить коллекцию");
        confirmation.setContentText("Вы уверены, что хотите удалить все файлы из коллекции?");

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
                statusLabel.setText("Коллекция очищена");
            }
        });
    }

    @FXML
    private void onGotoSection() {
        if (currentSection != null && currentSection.isPlayable()) {
            statusLabel.setText("Переход к секции " + currentSection.getIndex());
            // TODO: Интеграция с эмулятором для перехода к секции
            System.out.println("Переход к секции " + currentSection.getIndex());
        }
    }

    private void saveCollection() {
        AppSettings.getInstance().saveTapeCollection(tapeCollection);
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Предупреждение");
        alert.setHeaderText(null);
        alert.setContentText(message);
        ThemeManager.applyThemeToDialog(alert);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        ThemeManager.applyThemeToDialog(alert);
        alert.showAndWait();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " байт";
        if (bytes < 1024 * 1024) return String.format("%.1f КБ", bytes / 1024.0);
        return String.format("%.1f МБ", bytes / (1024.0 * 1024.0));
    }
}