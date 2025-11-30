package spectrum.jfx.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import lombok.Setter;
import spectrum.jfx.model.TapeSection;
import spectrum.jfx.ui.localization.LocalizationManager;
import spectrum.jfx.ui.localization.LocalizationManager.LocalizationChangeListener;

import java.net.URL;
import java.util.ResourceBundle;

public class HexEditorController implements Initializable, LocalizationChangeListener {

    @FXML private Label titleLabel;
    @FXML private Label infoLabel;
    @FXML private VBox hexDataContainer;
    @FXML private Button closeButton;

    @Setter
    private Stage stage;

    private TapeSection tapeSection;
    private LocalizationManager localizationManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        localizationManager = LocalizationManager.getInstance();
        localizationManager.addLanguageChangeListener(this);

        // Настраиваем контейнер для данных
        setupHexDisplay();
    }

    private void setupHexDisplay() {
        // Контейнер уже настроен в FXML
        hexDataContainer.getChildren().clear();
    }

    public void setSectionData(TapeSection section) {
        this.tapeSection = section;
        updateDisplay();
    }

    private void updateDisplay() {
        if (tapeSection == null) return;

        // Обновляем заголовок
        String localizedTitle = localizeTitle(tapeSection.getTitle(), tapeSection.getType());
        String typeDisplayName = localizationManager.getString(tapeSection.getType().getLocalizationKey());
        titleLabel.setText(localizationManager.getString("hex.editor.title",
            tapeSection.getIndex(), localizedTitle));

        // Обновляем информацию
        infoLabel.setText(localizationManager.getString("hex.editor.info",
            typeDisplayName, tapeSection.getLength()));

        // Генерируем HEX представление
        generateHexDisplay();
    }

    private void generateHexDisplay() {
        if (tapeSection == null || tapeSection.getData() == null) {
            Label noDataLabel = new Label(localizationManager.getString("hex.editor.nodata"));
            hexDataContainer.getChildren().add(noDataLabel);
            return;
        }

        byte[] data = tapeSection.getData();
        hexDataContainer.getChildren().clear();

        final int bytesPerLine = 16;
        String monoFont = "-fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace; -fx-font-size: 12px;";

        for (int i = 0; i < data.length; i += bytesPerLine) {
            // Создаем строку с точными колонками
            HBox row = new HBox();
            row.setSpacing(0);
            row.setAlignment(Pos.CENTER_LEFT);

            // Колонка 1: Offset (70px как в заголовке)
            Label offsetLabel = new Label(String.format("%08X", i));
            offsetLabel.setPrefWidth(70);
            offsetLabel.setAlignment(Pos.CENTER);
            offsetLabel.setStyle(monoFont);

            // Разделитель 1
            Separator sep1 = new Separator();
            sep1.setOrientation(javafx.geometry.Orientation.VERTICAL);
            sep1.setPrefHeight(15);

            // Колонка 2: HEX байты - каждый байт отдельный Label (22px как в заголовке)
            HBox hexBox = new HBox();
            hexBox.setSpacing(0);
            hexBox.setPrefWidth(400);

            for (int j = 0; j < 16; j++) {
                Label byteLabel = new Label();
                byteLabel.setPrefWidth(22);
                byteLabel.setAlignment(Pos.CENTER);
                byteLabel.setStyle(monoFont);

                if (i + j < data.length) {
                    byte b = data[i + j];
                    byteLabel.setText(String.format("%02X", b & 0xFF));
                } else {
                    byteLabel.setText("");
                }

                hexBox.getChildren().add(byteLabel);

                // Пробел между группами (после 8-го байта)
                if (j == 7) {
                    Label spacer = new Label("");
                    spacer.setPrefWidth(10);
                    hexBox.getChildren().add(spacer);
                }
            }

            // Разделитель 2
            Separator sep2 = new Separator();
            sep2.setOrientation(javafx.geometry.Orientation.VERTICAL);
            sep2.setPrefHeight(15);

            // Колонка 3: ASCII символы - каждый символ отдельный Label
            HBox asciiBox = new HBox();
            asciiBox.setSpacing(0);
            asciiBox.setPrefWidth(130);
            asciiBox.setStyle("-fx-padding: 0 0 0 10;"); // отступ как в заголовке

            for (int j = 0; j < 16; j++) {
                Label charLabel = new Label();
                charLabel.setPrefWidth(8); // фиксированная ширина для каждого символа
                charLabel.setAlignment(Pos.CENTER);
                charLabel.setStyle(monoFont);

                if (i + j < data.length) {
                    byte b = data[i + j];
                    char c = (char) (b & 0xFF);
                    if (c >= 32 && c <= 126) {
                        charLabel.setText(String.valueOf(c));
                    } else {
                        charLabel.setText(".");
                    }
                } else {
                    charLabel.setText("");
                }

                asciiBox.getChildren().add(charLabel);
            }

            // Добавляем все компоненты в строку
            row.getChildren().addAll(offsetLabel, sep1, hexBox, sep2, asciiBox);
            hexDataContainer.getChildren().add(row);
        }
    }

    /**
     * Локализует названия секций (копировано из TapeLibraryController)
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
                    String msValue = title.substring(7, title.length() - 4);
                    return localizationManager.getString("parser.pause.format", msValue);
                }
                if (title.startsWith("Description: ")) {
                    String description = title.substring(13);
                    return localizationManager.getString("parser.description.format", description);
                }
                if (title.startsWith("Unknown block 0x") || title.startsWith("Block 0x")) {
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

    @FXML
    private void onClose() {
        if (stage != null) {
            stage.close();
        }
    }

    // Реализация LocalizationChangeListener
    @Override
    public void onLanguageChanged(LocalizationManager.Language newLanguage) {
        Platform.runLater(this::updateDisplay);
    }
}