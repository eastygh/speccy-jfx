package spectrum.jfx.hardware.disk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spectrum.jfx.hardware.disk.wd1793.WD1793Impl;
import spectrum.hardware.machine.MachineSettings;

import static org.junit.jupiter.api.Assertions.*;

public class WD1793ImplTest {

    private WD1793Impl wd1793;
    private byte[] dummyDisk;

    private long currentTime = 0;

    private void advanceTime(long delta) {
        currentTime += delta;
        wd1793.ticks(currentTime, (int) delta);
    }

    @BeforeEach
    void setUp() {
        wd1793 = new WD1793Impl(MachineSettings.builder().build());
        wd1793.setActive(true);
        currentTime = 0;
        // TRD disk: 80 tracks * 2 sides * 16 sectors * 256 bytes = 655360 bytes
        dummyDisk = new byte[655360];
        // Fill sector 9 of track 0, side 0 with some data
        // Offset = ((0 * 2 + 0) * 16 + (9 - 1)) * 256 = 8 * 256 = 2048
        for (int i = 0; i < 256; i++) {
            dummyDisk[2048 + i] = (byte) i;
        }
        wd1793.getDrive(0).loadRawData(dummyDisk);
    }

    @Test
    void testRestoreCommand() {
        wd1793.outPort(0xFF, 0x08); // Select drive A, side 1, Motor ON (бит 3=1)
        wd1793.outPort(0x1F, 0x00); // Restore command
        
        int status = wd1793.inPort(0x1F);
        assertTrue((status & 0x01) != 0, "Busy bit should be set");
        assertFalse((status & 0x80) != 0, "Not Ready bit should be 0 when Motor is ON and Disk is loaded");
        
        advanceTime(10000); // Advance time
        
        status = wd1793.inPort(0x1F);
        assertFalse((status & 0x01) != 0, "Busy bit should be cleared after completion");
        assertEquals(0, wd1793.inPort(0x3F), "Track register should be 0");
    }

    @Test
    void testReadSector9Track0() {
        wd1793.outPort(0xFF, 0x18); // Drive A, Side 0 (бит 4=1), Motor ON (бит 3=1)
        wd1793.outPort(0x3F, 0x00); // Track 0
        wd1793.outPort(0x5F, 0x09); // Sector 9
        
        wd1793.outPort(0x1F, 0x80); // Read Sector command
        
        // Wait for SEARCHING -> TRANSFERRING
        advanceTime(35000); // Задержка поиска теперь 30000
        
        int status = wd1793.inPort(0x1F);
        assertTrue((status & 0x02) != 0, "DRQ bit should be set. Status was: " + Integer.toHexString(status));
        
        int sysPort = wd1793.inPort(0xFF);
        assertTrue((sysPort & 0x40) != 0, "DRQ in system port should be 1 when DRQ is 1");

        for (int i = 0; i < 256; i++) {
            int data = wd1793.inPort(0x7F);
            assertEquals(i & 0xFF, data, "Data mismatch at byte " + i);
            if (i < 255) {
                // Between bytes, DRQ should be temporarily cleared and then set again after ticks
                status = wd1793.inPort(0x1F);
                assertFalse((status & 0x02) != 0, "DRQ should be cleared after read");
                advanceTime(200); // Tick to get next byte
                status = wd1793.inPort(0x1F);
                assertTrue((status & 0x02) != 0, "DRQ should be set again for next byte at " + i);
            }
        }
        
        status = wd1793.inPort(0x1F);
        assertFalse((status & 0x01) != 0, "Busy should be cleared after sector read");
    }

    @Test
    void testStatusTypePersistence() {
        wd1793.outPort(0xFF, 0x18); // Motor ON, Side 0
        wd1793.outPort(0x3F, 0x00); // Track 0
        wd1793.outPort(0x5F, 0x01); // Sector 1
        
        wd1793.outPort(0x1F, 0x80); // Read Sector (Type II)
        advanceTime(35000);
        
        // Read all 256 bytes
        for (int i = 0; i < 256; i++) {
            wd1793.inPort(0x7F);
            advanceTime(200);
        }
        
        // Now command is finished. Status should be Type II still.
        // In Type II, bit 2 is LOST DATA.
        // If we incorrectly switched to Type I, bit 2 would be TRACK0 (which is 1 on track 0).
        int status = wd1793.inPort(0x1F);
        assertFalse((status & 0x04) != 0, "Bit 2 should be 0 (No Lost Data) if status is Type II. If it's 1, it means we switched to Type I and see TRACK0");
        
        // Now send Force Interrupt to switch to Type I
        wd1793.outPort(0x1F, 0xD0);
        status = wd1793.inPort(0x1F);
        assertTrue((status & 0x04) != 0, "Now bit 2 should be 1 (TRACK0) because Force Interrupt switches to Type I");
    }

    @Test
    void testIntrqResetOnStatusRead() {
        wd1793.outPort(0xFF, 0x08); // Motor ON
        wd1793.outPort(0x1F, 0x03); // Restore command (Type I)

        advanceTime(10000); // Wait for completion

        // Check system port (Bit 7 should be 1 - active INTRQ)
        int sysPort = wd1793.inPort(0xFF);
        assertTrue((sysPort & 0x80) != 0, "INTRQ should be active in system port (bit 7 = 1)");

        // Read status register
        wd1793.inPort(0x1F);

        // Now INTRQ should be inactive (Bit 7 should be 0)
        sysPort = wd1793.inPort(0xFF);
        assertFalse((sysPort & 0x80) != 0, "INTRQ should be reset after status read (bit 7 = 0)");
    }

    @Test
    void testReadAddress() {
        wd1793.outPort(0xFF, 0x18); // Drive A, Side 0, Motor ON
        wd1793.outPort(0x3F, 0x05); // Track 5
        wd1793.outPort(0x5F, 0x0A); // Sector 10
        
        wd1793.outPort(0x1F, 0xC0); // Read Address command
        
        advanceTime(20000); // Задержка теперь 15000
        
        assertTrue((wd1793.inPort(0x1F) & 0x02) != 0, "DRQ should be set");
        assertEquals(5, wd1793.inPort(0x7F), "Track mismatch");
        
        advanceTime(200);
        assertEquals(0, wd1793.inPort(0x7F), "Side mismatch (0x10 is Side 0)");
        
        advanceTime(200);
        assertEquals(10, wd1793.inPort(0x7F), "Sector mismatch");
        
        advanceTime(200);
        assertEquals(1, wd1793.inPort(0x7F), "Length mismatch");
        
        advanceTime(200);
        wd1793.inPort(0x7F); // CRC 1
        advanceTime(200);
        wd1793.inPort(0x7F); // CRC 2
        
        assertFalse((wd1793.inPort(0x1F) & 0x01) != 0, "Busy should be cleared");
    }

    @Test
    void testSystemReset() {
        wd1793.outPort(0x3F, 10); // Set track to 10
        assertEquals(10, wd1793.inPort(0x3F));
        
        // Pulse Reset (Bit 2 = 0)
        wd1793.outPort(0xFF, 0x38); // 0x38 = 0011 1000 (Bit 2 = 0)
        assertEquals(0, wd1793.inPort(0x3F), "Track should be reset to 0");
        assertFalse((wd1793.inPort(0x1F) & 0x01) != 0, "Busy should be 0 during reset");
        
        // Release Reset (Bit 2 = 1)
        wd1793.outPort(0xFF, 0x3C); // 0x3C = 0011 1100 (Bit 2 = 1)
        assertTrue((wd1793.inPort(0x1F) & 0x01) != 0, "Busy should be 1 after reset (Restore command)");
        
        advanceTime(10000);
        assertFalse((wd1793.inPort(0x1F) & 0x01) != 0, "Busy should be 0 after Restore completes");
    }

    @Test
    void testNotReadyWhenMotorOff() {
        wd1793.outPort(0xFF, 0x00); // Motor OFF
        int status = wd1793.inPort(0x1F);
        assertTrue((status & 0x80) != 0, "Should be NOT READY when motor is OFF");
        
        wd1793.outPort(0xFF, 0x08); // Motor ON
        status = wd1793.inPort(0x1F);
        assertFalse((status & 0x80) != 0, "Should be READY when motor is ON and disk inserted");
    }

    @Test
    void testWriteTrackCommand() {
        wd1793.outPort(0xFF, 0x18); // Motor ON, Side 0
        wd1793.outPort(0x1F, 0xF8); // Write Track command

        advanceTime(35000); // Wait for SEARCHING -> TRANSFERRING (задержка 30000)

        int status = wd1793.inPort(0x1F);
        assertTrue((status & 0x01) != 0, "Busy should be set");
        assertTrue((status & 0x02) != 0, "DRQ should be set");

        // Write a few bytes
        for (int i = 0; i < 10; i++) {
            wd1793.outPort(0x7F, 0x4E); // GAP byte
            status = wd1793.inPort(0x1F);
            assertFalse((status & 0x02) != 0, "DRQ should be cleared immediately after write at " + i);

            advanceTime(200);
            status = wd1793.inPort(0x1F);
            assertTrue((status & 0x02) != 0, "DRQ should be set again after delay at " + i);
        }
    }

    @Test
    void testReadAddressUpdatesSectorRegister() {
        wd1793.outPort(0xFF, 0x18); // Drive A, Side 0, Motor ON
        wd1793.outPort(0x3F, 0x05); // Track 5
        wd1793.outPort(0x5F, 0x0A); // Sector 10
        
        wd1793.outPort(0x1F, 0xC0); // Read Address command
        advanceTime(20000);
        
        // Read 6 bytes
        for (int i = 0; i < 6; i++) {
            wd1793.inPort(0x7F);
            advanceTime(200);
        }
        
        assertEquals(5, wd1793.inPort(0x5F), "Sector register should be updated with track number (5) after Read Address");
    }

    @Test
    void testStepCommands() {
        wd1793.outPort(0xFF, 0x08); // Motor ON
        wd1793.outPort(0x3F, 10);   // Track 10
        
        // Step Out (towards 0) with update=1 (0x60 | 0x10 = 0x70)
        wd1793.outPort(0x1F, 0x70);
        advanceTime(10000);
        assertEquals(9, wd1793.inPort(0x3F), "Track should be 9 after Step Out with update");
        
        // Step (same direction) with update=1 (0x20 | 0x10 = 0x30)
        wd1793.outPort(0x1F, 0x30);
        advanceTime(10000);
        assertEquals(8, wd1793.inPort(0x3F), "Track should be 8 after Step with update (continuing Out)");
        
        // Step In (towards center) with update=1 (0x40 | 0x10 = 0x50)
        wd1793.outPort(0x1F, 0x50);
        advanceTime(10000);
        assertEquals(9, wd1793.inPort(0x3F), "Track should be 9 after Step In with update");
        
        // Step (same direction) with update=0 (0x20)
        wd1793.outPort(0x1F, 0x20);
        advanceTime(10000);
        assertEquals(9, wd1793.inPort(0x3F), "Track should STILL be 9 after Step without update");
        
        // But physical track should have moved to 10. Let's verify by checking TRACK0 bit later or just trust the logic.
        // Actually we can check S_TRACK0 if we go to 0.
    }
}
