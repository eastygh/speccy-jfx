package spectrum.jfx.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.SneakyThrows;
import spectrum.jfx.debug.DebugListener;
import spectrum.jfx.debug.Z80Disassembler;
import spectrum.jfx.hardware.cpu.CPU;
import spectrum.jfx.hardware.machine.HardwareProvider;
import spectrum.jfx.hardware.memory.Memory;

import java.net.URL;
import java.util.ResourceBundle;

public class DebugController implements Initializable, DebugListener {

    private static final PseudoClass PC_PSEUDO_CLASS = PseudoClass.getPseudoClass("pc-line");

    @FXML
    private TableView<DisassemblyRow> disassemblyTable;
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

    private volatile boolean nextStep = false;

    private HardwareProvider hardwareProvider;
    private final ObservableList<DisassemblyRow> disassemblyRows = FXCollections.observableArrayList();
    private final Z80Disassembler disassembler = new Z80Disassembler();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
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
    public void onStepComplete(HardwareProvider hv) {
        Platform.runLater(this::updateUI);
        while (!nextStep) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        nextStep = false;
    }

    private void updateUI() {
        if (hardwareProvider == null) return;

        updateRegisters();
        updateDisassembly();
        framesLabel.setText(String.valueOf(hardwareProvider.getEmulator().getFrames()));
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
        int currentAddr = pc;
        // Show 20 instructions starting from PC
        for (int i = 0; i < 20; i++) {
            Z80Disassembler.DisassemblyResult result = disassembler.disassemble(memory, currentAddr);
            boolean isPC = currentAddr == pc;
            disassemblyRows.add(new DisassemblyRow(
                    (isPC ? "▶ " : "  ") + String.format("%04X", currentAddr),
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
        hardwareProvider.setDebug(false);
        nextStep = true;
        Thread.sleep(30);
        nextStep = false;
        updateUI();
    }

    @FXML
    private void onStop() {
        hardwareProvider.setDebug(true);
        updateUI();
    }

    @FXML
    private void onStepInto() {
        hardwareProvider.setDebug(true);
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
        hardwareProvider.setDebug(false);
        hardwareProvider.setDebugListener(null);
        ((Stage) disassemblyTable.getScene().getWindow()).close();
    }

    public static class DisassemblyRow {
        private final SimpleStringProperty address;
        private final SimpleStringProperty hex;
        private final SimpleStringProperty mnemonic;
        @Getter
        private final boolean currentPC;

        public DisassemblyRow(String address, String hex, String mnemonic, boolean currentPC) {
            this.address = new SimpleStringProperty(address);
            this.hex = new SimpleStringProperty(hex);
            this.mnemonic = new SimpleStringProperty(mnemonic);
            this.currentPC = currentPC;
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
