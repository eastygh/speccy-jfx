package spectrum.jfx.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.debug.DebugListener;
import spectrum.jfx.debug.SuspendType;
import spectrum.jfx.debug.Z80Disassembler;
import spectrum.jfx.hardware.cpu.CPU;
import spectrum.jfx.hardware.machine.HardwareProvider;
import spectrum.jfx.hardware.memory.Memory;
import spectrum.jfx.snapshot.CPUSnapShot;
import spectrum.jfx.ui.theme.ThemeManager;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
public class DebugController implements Initializable, DebugListener {

    private static final PseudoClass PC_PSEUDO_CLASS = PseudoClass.getPseudoClass("pc-line");

    @FXML
    private TableView<DisassemblyRow> disassemblyTable;
    @FXML
    private TableColumn<DisassemblyRow, Boolean> breakpointColumn;
    @FXML
    private TableColumn<DisassemblyRow, String> pcColumn;
    @FXML
    private TableColumn<DisassemblyRow, String> addressColumn;
    @FXML
    private TableColumn<DisassemblyRow, String> hexColumn;
    @FXML
    private TableColumn<DisassemblyRow, String> mnemonicColumn;

    @FXML
    private TextField pcField;
    @FXML
    private TextField spField;
    @FXML
    private TextField afField;
    @FXML
    private TextField afxField;
    @FXML
    private TextField bcField;
    @FXML
    private TextField bcxField;
    @FXML
    private TextField deField;
    @FXML
    private TextField dexField;
    @FXML
    private TextField hlField;
    @FXML
    private TextField hlxField;
    @FXML
    private TextField ixField;
    @FXML
    private TextField iyField;
    @FXML
    private TextField iField;
    @FXML
    private TextField rField;
    @FXML
    private TextField flagsField;
    @FXML
    private Label framesLabel;
    @FXML
    private Label iff1Label;
    @FXML
    private Label iff2Label;
    @FXML
    private Label haltLabel;
    @FXML
    private Label imLabel;
    @FXML
    private Label statusLabel;

    @FXML
    private Button runButton;
    @FXML
    private Button stopButton;
    @FXML
    private Button stepIntoButton;
    @FXML
    private Button stepOverButton;

    private volatile boolean nextStep = false;

    private HardwareProvider hardwareProvider;
    private ResourceBundle resources;
    private final ObservableList<DisassemblyRow> disassemblyRows = FXCollections.observableArrayList();
    private final Z80Disassembler disassembler = new Z80Disassembler();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.resources = resources;
        breakpointColumn.setCellValueFactory(new PropertyValueFactory<>("breakpoint"));
        // Handle clicks on breakpoint column
        breakpointColumn.setCellFactory(column -> {
            TableCell<DisassemblyRow, Boolean> cell = new TableCell<>() {
                private final Circle circle = new Circle(5, Color.RED);

                @Override
                protected void updateItem(Boolean item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null || !item) {
                        setGraphic(null);
                    } else {
                        setGraphic(circle);
                    }
                }
            };
            cell.setOnMouseClicked(event -> {
                if (!cell.isEmpty()) {
                    DisassemblyRow row = cell.getTableRow().getItem();
                    if (row != null) {
                        int address = Integer.parseInt(row.getAddress(), 16);
                        boolean currentState = hardwareProvider.getEmulator().isDebugBreakpoint(address);
                        hardwareProvider.getEmulator().setDebugBreakpoint(address, !currentState);
                        updateDisassembly(); // Refresh to show/hide the red ball
                    }
                }
            });
            return cell;
        });


        pcColumn.setCellValueFactory(new PropertyValueFactory<>("pcIndicator"));
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
        hexColumn.setCellValueFactory(new PropertyValueFactory<>("hex"));
        mnemonicColumn.setCellValueFactory(new PropertyValueFactory<>("mnemonic"));
        disassemblyTable.setItems(disassemblyRows);

        disassemblyTable.setRowFactory(tv -> new TableRow<DisassemblyRow>() {
            @Override
            protected void updateItem(DisassemblyRow item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(PC_PSEUDO_CLASS, item != null && !empty && item.isCurrentPC());
            }
        });
    }

    public void setHardwareProvider(HardwareProvider hardwareProvider) {
        this.hardwareProvider = hardwareProvider;
        this.hardwareProvider.setDebugListener(this);
        updateUI();
    }

    @Override
    public void onStepComplete(HardwareProvider hv, SuspendType suspendType) {
        Platform.runLater(this::updateUI);
        while (!nextStep) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for next step", e);
                Thread.currentThread().interrupt();
            }
        }
        nextStep = false;
    }

    private void updateUI() {
        if (hardwareProvider == null) return;

        CPU cpu = hardwareProvider.getCPU();
        CPUSnapShot snapshot = (CPUSnapShot) cpu.getSnapShot();

        updateRegisters(snapshot);
        updateDisassembly();
        framesLabel.setText(String.valueOf(hardwareProvider.getEmulator().getFrames()));
        iff1Label.setText(snapshot.isFfIFF1() ? "1" : "0");
        iff2Label.setText(snapshot.isFfIFF2() ? "1" : "0");
        haltLabel.setText(snapshot.isHalted() ? "1" : "0");
        imLabel.setText(String.valueOf(snapshot.getModeINT()));

        boolean suspended = hardwareProvider.isDebugSuspended();
        runButton.setDisable(!suspended);
        stopButton.setDisable(suspended);
        stepIntoButton.setDisable(!suspended);
        stepOverButton.setDisable(!suspended);

        String statusKey = suspended ? "debug.status.suspended" : "debug.status.running";
        String statusText = resources.getString("debug.status") + " " + resources.getString(statusKey);
        statusLabel.setText(statusText);
    }

    private void updateRegisters(CPUSnapShot snapshot) {
        pcField.setText(String.format("%04X", snapshot.getRegPC()));
        spField.setText(String.format("%04X", snapshot.getRegSP()));

        afField.setText(String.format("%02X%02X", snapshot.getRegA(), snapshot.getRegF()));
        afxField.setText(String.format("%02X%02X", snapshot.getRegAx(), snapshot.getRegFx()));

        bcField.setText(String.format("%02X%02X", snapshot.getRegB(), snapshot.getRegC()));
        bcxField.setText(String.format("%02X%02X", snapshot.getRegBx(), snapshot.getRegCx()));

        deField.setText(String.format("%02X%02X", snapshot.getRegD(), snapshot.getRegE()));
        dexField.setText(String.format("%02X%02X", snapshot.getRegDx(), snapshot.getRegEx()));

        hlField.setText(String.format("%02X%02X", snapshot.getRegH(), snapshot.getRegL()));
        hlxField.setText(String.format("%02X%02X", snapshot.getRegHx(), snapshot.getRegLx()));

        ixField.setText(String.format("%04X", snapshot.getRegIX()));
        iyField.setText(String.format("%04X", snapshot.getRegIY()));

        iField.setText(String.format("%02X", snapshot.getRegI()));
        rField.setText(String.format("%02X", snapshot.getRegR()));

        flagsField.setText(getFlagsString(snapshot.getRegF()));
    }

    private String getFlagsString(int f) {
        return String.format("S:%d Z:%d 5:%d H:%d 3:%d P:%d N:%d C:%d",
                (f >> 7) & 1, (f >> 6) & 1, (f >> 5) & 1, (f >> 4) & 1,
                (f >> 3) & 1, (f >> 2) & 1, (f >> 1) & 1, f & 1);
    }

    private void updateDisassembly() {
        if (hardwareProvider == null) return;

        Memory memory = hardwareProvider.getMemory();
        int pc = hardwareProvider.getCPU().getRegPC();

        disassemblyRows.clear();
        int currentAddr = backShiftInstruction(memory, pc, 5);
        // Show 20 instructions starting from PC
        for (int i = 0; i < 20; i++) {
            Z80Disassembler.DisassemblyResult result = disassembler.disassemble(memory, currentAddr);
            boolean isPC = currentAddr == pc;
            disassemblyRows.add(new DisassemblyRow(
                    hardwareProvider.getEmulator().isDebugBreakpoint(currentAddr),
                    isPC ? "▶" : "",
                    String.format("%04X", currentAddr),
                    result.getHexBytes(),
                    result.getMnemonic(),
                    isPC
            ));
            currentAddr = (currentAddr + result.getLength()) & 0xFFFF;
        }
    }

    @FXML
    @SneakyThrows
    private void onRun() {
        hardwareProvider.setDebugSuspended(false);
        nextStep = true;
        updateUI();
    }

    @FXML
    private void onStop() {
        hardwareProvider.setDebugSuspended(true);
        updateUI();
    }

    @FXML
    private void onStepInto() {
        hardwareProvider.setDebugSuspended(true);
        nextStep = true;
        updateUI();
    }

    @FXML
    private void onStepOver() {
        // Simple step over for now, same as step into
        // Better implementation would set a breakpoint after the current instruction
        onStepInto();
    }

    @FXML
    private void onReset() {
        hardwareProvider.getEmulator().reset();
        updateUI();
    }

    @FXML
    private void onAddBreakpoint() {
        Dialog<Integer> dialog = new Dialog<>();
        ThemeManager.applyThemeToDialog(dialog);
        dialog.setTitle(resources.getString("debug.addBreakpoint.title"));
        dialog.setHeaderText(resources.getString("debug.addBreakpoint.header"));

        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField decimalField = new TextField();
        decimalField.setPromptText("0-65535");
        TextField hexField = new TextField();
        hexField.setPromptText("0000-FFFF");

        grid.add(new Label(resources.getString("debug.addBreakpoint.decimal")), 0, 0);
        grid.add(decimalField, 1, 0);
        grid.add(new Label(resources.getString("debug.addBreakpoint.hex")), 0, 1);
        grid.add(hexField, 1, 1);

        // Synchronization logic
        decimalField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (decimalField.isFocused()) {
                try {
                    if (newValue.isEmpty()) {
                        hexField.setText("");
                    } else {
                        int val = Integer.parseInt(newValue);
                        if (val >= 0 && val <= 65535) {
                            hexField.setText(String.format("%04X", val));
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        hexField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (hexField.isFocused()) {
                try {
                    if (newValue.isEmpty()) {
                        decimalField.setText("");
                    } else {
                        int val = Integer.parseInt(newValue, 16);
                        if (val >= 0 && val <= 65535) {
                            decimalField.setText(String.valueOf(val));
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(decimalField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                try {
                    return Integer.parseInt(decimalField.getText());
                } catch (NumberFormatException e) {
                    try {
                        return Integer.parseInt(hexField.getText(), 16);
                    } catch (NumberFormatException e2) {
                        return null;
                    }
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(address -> {
            hardwareProvider.getEmulator().setDebugBreakpoint(address, true);
            updateDisassembly();
        });
    }

    @FXML
    public void onClose() {
        hardwareProvider.setDebugSuspended(false);
        hardwareProvider.setDebugListener(null);
        ((Stage) disassemblyTable.getScene().getWindow()).close();
    }

    private int backShiftInstruction(Memory memory, int address, int backStepsInstructions) {
        int result = address;
        Z80Disassembler.DisassemblyResult anchor = disassembler.disassemble(memory, address);
        int ins = 0;
        while (ins++ <= backStepsInstructions && result > 0) {
            result++;
            boolean mactch = false;
            while (!mactch) {
                List<Z80Disassembler.DisassemblyResult> l = disassembler.disassembleBlock(memory, result, ins);
                Z80Disassembler.DisassemblyResult last = l.getLast();
                if (last.getMnemonic().equals(anchor.getMnemonic())) {
                    mactch = true;
                } else {
                    result--;
                    if (result <= 0) break;
                }
            }
        }
        return result;
    }

    public static class DisassemblyRow {
        private final SimpleBooleanProperty breakpoint;
        private final SimpleStringProperty pcIndicator;
        private final SimpleStringProperty address;
        private final SimpleStringProperty hex;
        private final SimpleStringProperty mnemonic;
        @Getter
        private final boolean currentPC;

        public DisassemblyRow(boolean breakpoint, String pcIndicator, String address, String hex, String mnemonic, boolean currentPC) {
            this.breakpoint = new SimpleBooleanProperty(breakpoint);
            this.pcIndicator = new SimpleStringProperty(pcIndicator);
            this.address = new SimpleStringProperty(address);
            this.hex = new SimpleStringProperty(hex);
            this.mnemonic = new SimpleStringProperty(mnemonic);
            this.currentPC = currentPC;
        }

        public boolean isBreakpoint() {
            return breakpoint.get();
        }

        public SimpleBooleanProperty breakpointProperty() {
            return breakpoint;
        }

        public String getPcIndicator() {
            return pcIndicator.get();
        }

        public String getAddress() {
            return address.get();
        }

        public String getHex() {
            return hex.get();
        }

        public String getMnemonic() {
            return mnemonic.get();
        }

    }
}
