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
import spectrum.hardware.debug.DebugListener;
import spectrum.hardware.debug.SuspendType;
import spectrum.hardware.debug.Z80Disassembler;
import spectrum.hardware.cpu.CPU;
import spectrum.hardware.machine.HardwareProvider;
import spectrum.hardware.memory.Memory;
import spectrum.hardware.machine.Machine;
import spectrum.hardware.snapshot.CPUSnapShot;
import spectrum.jfx.ui.theme.ThemeManager;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

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
    private Label hexViewLabel;

    @FXML
    private TableView<MemoryRow> hexTableView;
    @FXML
    private TableColumn<MemoryRow, String> hexAddressColumn;
    @FXML
    private TableColumn<MemoryRow, String> hexDataColumn;
    @FXML
    private TableColumn<MemoryRow, String> hexAsciiColumn;

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

    private ResourceBundle resources;
    private final ObservableList<DisassemblyRow> disassemblyRows = FXCollections.observableArrayList();
    private final ObservableList<MemoryRow> hexRows = FXCollections.observableArrayList();
    private final Z80Disassembler disassembler = new Z80Disassembler();
    private final AtomicReference<HardwareProvider> hardwareProviderRef = new AtomicReference<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.resources = resources;
        breakpointColumn.setCellValueFactory(new PropertyValueFactory<>("breakpoint"));
        // Handle clicks on the breakpoint column
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
                        boolean currentState = getHardwareProvider().getDebugManager().isBreakpoint(address);
                        if (currentState) {
                            getHardwareProvider().getDebugManager().removeBreakpoint(address);
                        } else {
                            getHardwareProvider().getDebugManager().addBreakpoint(address);
                        }
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

        hexAddressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
        hexDataColumn.setCellValueFactory(new PropertyValueFactory<>("hex"));
        hexAsciiColumn.setCellValueFactory(new PropertyValueFactory<>("ascii"));
        hexTableView.setItems(hexRows);

        setupRegisterClick(pcField, "PC");
        setupRegisterClick(spField, "SP");
        setupRegisterClick(afField, "AF");
        setupRegisterClick(afxField, "AF'");
        setupRegisterClick(bcField, "BC");
        setupRegisterClick(bcxField, "BC'");
        setupRegisterClick(deField, "DE");
        setupRegisterClick(dexField, "DE'");
        setupRegisterClick(hlField, "HL");
        setupRegisterClick(hlxField, "HL'");
        setupRegisterClick(ixField, "IX");
        setupRegisterClick(iyField, "IY");
        setupRegisterClick(iField, "I");
        setupRegisterClick(rField, "R");
    }

    private void setupRegisterClick(TextField field, String regName) {
        field.setOnMouseClicked(event -> {
            try {
                String text = field.getText();
                if (text != null && !text.isEmpty()) {
                    int address;
                    if (text.length() > 4) {
                        address = Integer.parseInt(text.substring(0, 4), 16);
                    } else {
                        address = Integer.parseInt(text, 16);
                    }
                    updateHexView(address, regName);
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        });
    }

    private void updateHexView(int centerAddress, String regName) {
        if (getHardwareProvider() == null) return;
        if (regName != null) {
            hexViewLabel.setText("Hex View - " + regName);
        } else {
            hexViewLabel.setText("Hex View");
        }
        Memory memory = getHardwareProvider().getMemory();
        hexRows.clear();

        int centerRowStart = centerAddress & 0xFFF0;
        int startAddress = (centerRowStart - 32) & 0xFFFF;

        for (int i = 0; i < 5; i++) {
            int rowAddr = (startAddress + i * 16) & 0xFFFF;
            byte[] data = memory.getBlock(rowAddr, 16);

            StringBuilder hexSb = new StringBuilder();
            StringBuilder asciiSb = new StringBuilder();

            for (byte b : data) {
                hexSb.append(String.format("%02X ", b));
                char c = (char) (b & 0xFF);
                if (c >= 32 && c <= 126) {
                    asciiSb.append(c);
                } else {
                    asciiSb.append('.');
                }
            }

            hexRows.add(new MemoryRow(
                    String.format("%04X", rowAddr),
                    hexSb.toString().trim(),
                    asciiSb.toString()
            ));
        }
    }

    @Getter
    public static class MemoryRow {
        private final String address;
        private final String hex;
        private final String ascii;

        public MemoryRow(String address, String hex, String ascii) {
            this.address = address;
            this.hex = hex;
            this.ascii = ascii;
        }
    }

    public void setHardwareProvider(HardwareProvider hardwareProvider) {
        hardwareProviderRef.set(getHardwareProvider());
        getHardwareProvider().setDebugListener(this);
        updateUI();
    }

    @Override
    public void onStepComplete(final HardwareProvider hv, SuspendType suspendType) {
        Platform.runLater(() -> {
            setHardwareProvider(hv);
            updateUI();
        });
    }

    @Override
    public void onResumed() {
        Platform.runLater(this::updateUI);
    }

    private void updateUI() {
        if (getHardwareProvider() == null) return;

        CPU cpu = getHardwareProvider().getCPU();
        CPUSnapShot snapshot = (CPUSnapShot) cpu.getSnapShot();

        updateRegisters(snapshot);
        updateDisassembly();
        framesLabel.setText(String.valueOf(getHardwareProvider().getEmulator().getFrames()));
        iff1Label.setText(snapshot.isFfIFF1() ? "1" : "0");
        iff2Label.setText(snapshot.isFfIFF2() ? "1" : "0");
        haltLabel.setText(snapshot.isHalted() ? "1" : "0");
        imLabel.setText(String.valueOf(snapshot.getModeINT()));

        boolean suspended = getHardwareProvider().getDebugManager().isPaused();
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
        if (getHardwareProvider() == null) return;

        Memory memory = getHardwareProvider().getMemory();
        int pc = getHardwareProvider().getCPU().getRegPC();

        // Update hex view with PC address if it's the first update or just to keep it fresh
        if (hexRows.isEmpty()) {
            updateHexView(pc, "PC");
        }

        disassemblyRows.clear();
        int currentAddr = backShiftInstruction(memory, pc, 5);
        // Show 20 instructions starting from PC
        for (int i = 0; i < 20; i++) {
            Z80Disassembler.DisassemblyResult result = disassembler.disassemble(memory, currentAddr);
            boolean isPC = currentAddr == pc;
            disassemblyRows.add(new DisassemblyRow(
                    getHardwareProvider().getDebugManager().isBreakpoint(currentAddr),
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
        getHardwareProvider().getDebugManager().resume();
        updateUI();
    }

    @FXML
    private void onStop() {
        getHardwareProvider().getDebugManager().pause();
        updateUI();
    }

    @FXML
    private void onStepInto() {
        getHardwareProvider().getDebugManager().stepInto();
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
        if (getHardwareProvider().getDebugManager().isPaused()) {
            onRun();
        }
        getHardwareProvider().getEmulator().reset();
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
                } catch (NumberFormatException ignored) {
                }
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
                } catch (NumberFormatException ignored) {
                }
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
            getHardwareProvider().getDebugManager().addBreakpoint(address);
            updateDisassembly();
        });
    }

    @FXML
    public void onClose() {
        getHardwareProvider().getDebugManager().resume();
        getHardwareProvider().setDebugListener(null);
        ((Stage) disassemblyTable.getScene().getWindow()).close();
    }

    private HardwareProvider getHardwareProvider() {
        HardwareProvider hp = hardwareProviderRef.get();
        if (hp == null) {
            hardwareProviderRef.set(Machine.getHardwareProvider());
        }
        return hardwareProviderRef.get();
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
