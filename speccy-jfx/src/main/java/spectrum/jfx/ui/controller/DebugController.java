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
    private TextField bcField;
    @FXML
    private TextField deField;
    @FXML
    private TextField hlField;
    @FXML
    private TextField ixField;
    @FXML
    private TextField iyField;
    @FXML
    private TextField flagsField;
    @FXML
    private Label framesLabel;
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

        updateRegisters();
        updateDisassembly();
        framesLabel.setText(String.valueOf(hardwareProvider.getEmulator().getFrames()));

        boolean suspended = hardwareProvider.isDebugSuspended();
        runButton.setDisable(!suspended);
        stopButton.setDisable(suspended);
        stepIntoButton.setDisable(!suspended);
        stepOverButton.setDisable(!suspended);

        String statusKey = suspended ? "debug.status.suspended" : "debug.status.running";
        String statusText = resources.getString("debug.status") + " " + resources.getString(statusKey);
        statusLabel.setText(statusText);
    }

    private void updateRegisters() {
        CPU cpu = hardwareProvider.getCPU();
        pcField.setText(String.format("%04X", cpu.getRegPC()));
        spField.setText(String.format("%04X", cpu.getRegSP()));
        afField.setText(String.format("%02X%02X", cpu.getRegA(), cpu.getFlags()));
        bcField.setText(String.format("%04X", cpu.getRegBC()));
        deField.setText(String.format("%04X", cpu.getRegDE()));
        hlField.setText(String.format("%04X", cpu.getRegHL()));
        ixField.setText(String.format("%04X", cpu.getRegIX()));
        iyField.setText(String.format("%04X", cpu.getRegIY()));

        flagsField.setText(getFlagsString(cpu.getFlags()));
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
