package spectrum.jfx.snapshot.mapper;

import org.junit.jupiter.api.Test;
import snapshots.Z80State;
import spectrum.jfx.snapshot.CPUSnapShot;
import z80core.Z80.IntMode;

import static org.junit.jupiter.api.Assertions.*;

class CPUSnapShotMapperTest {

    @Test
    void testToZ80State() {
        CPUSnapShot snapshot = CPUSnapShot.builder()
                .regA(0x01).regB(0x02).regC(0x03).regD(0x04).regE(0x05).regH(0x06).regL(0x07)
                .regF(0x08).flagQ(true)
                .regAx(0x11).regFx(0x12)
                .regBx(0x13).regCx(0x14).regDx(0x15).regEx(0x16).regHx(0x17).regLx(0x18)
                .regPC(0x1234).regIX(0x5678).regIY(0x9ABC).regSP(0xFFFE)
                .regI(0x3F).regR(0x7F)
                .ffIFF1(true).ffIFF2(false).pendingEI(true)
                .activeNMI(false).activeINT(true).modeINT(1)
                .halted(true).memptr(0x4321)
                .build();

        Z80State state = CPUSnapShotMapper.toZ80State(snapshot);

        assertNotNull(state);
        assertEquals(0x01, state.getRegA());
        assertEquals(0x02, state.getRegB());
        assertEquals(0x03, state.getRegC());
        assertEquals(0x04, state.getRegD());
        assertEquals(0x05, state.getRegE());
        assertEquals(0x06, state.getRegH());
        assertEquals(0x07, state.getRegL());
        assertEquals(0x08, state.getRegF());
        assertTrue(state.isFlagQ());
        assertEquals(0x11, state.getRegAx());
        assertEquals(0x12, state.getRegFx());
        assertEquals(0x13, state.getRegBx());
        assertEquals(0x14, state.getRegCx());
        assertEquals(0x15, state.getRegDx());
        assertEquals(0x16, state.getRegEx());
        assertEquals(0x17, state.getRegHx());
        assertEquals(0x18, state.getRegLx());
        assertEquals(0x1234, state.getRegPC());
        assertEquals(0x5678, state.getRegIX());
        assertEquals(0x9ABC, state.getRegIY());
        assertEquals(0xFFFE, state.getRegSP());
        assertEquals(0x3F, state.getRegI());
        assertEquals(0x7F, state.getRegR());
        assertTrue(state.isIFF1());
        assertFalse(state.isIFF2());
        assertTrue(state.isPendingEI());
        assertFalse(state.isNMI());
        assertTrue(state.isINTLine());
        assertEquals(IntMode.IM1, state.getIM());
        assertTrue(state.isHalted());
        assertEquals(0x4321, state.getMemPtr());
    }

    @Test
    void testToCPUSnapShot() {
        Z80State state = new Z80State();
        state.setRegA(0x01);
        state.setRegB(0x02);
        state.setRegC(0x03);
        state.setRegD(0x04);
        state.setRegE(0x05);
        state.setRegH(0x06);
        state.setRegL(0x07);
        state.setRegF(0x08);
        state.setFlagQ(true);
        state.setRegAx(0x11);
        state.setRegFx(0x12);
        state.setRegBx(0x13);
        state.setRegCx(0x14);
        state.setRegDx(0x15);
        state.setRegEx(0x16);
        state.setRegHx(0x17);
        state.setRegLx(0x18);
        state.setRegPC(0x1234);
        state.setRegIX(0x5678);
        state.setRegIY(0x9ABC);
        state.setRegSP(0xFFFE);
        state.setRegI(0x3F);
        state.setRegR(0x7F);
        state.setIFF1(true);
        state.setIFF2(false);
        state.setPendingEI(true);
        state.setNMI(false);
        state.setINTLine(true);
        state.setIM(IntMode.IM2);
        state.setHalted(true);
        state.setMemPtr(0x4321);

        CPUSnapShot snapshot = CPUSnapShotMapper.toCPUSnapShot(state);

        assertNotNull(snapshot);
        assertEquals(0x01, snapshot.getRegA());
        assertEquals(0x02, snapshot.getRegB());
        assertEquals(0x03, snapshot.getRegC());
        assertEquals(0x04, snapshot.getRegD());
        assertEquals(0x05, snapshot.getRegE());
        assertEquals(0x06, snapshot.getRegH());
        assertEquals(0x07, snapshot.getRegL());
        assertEquals(0x08, snapshot.getRegF());
        assertTrue(snapshot.isFlagQ());
        assertEquals(0x11, snapshot.getRegAx());
        assertEquals(0x12, snapshot.getRegFx());
        assertEquals(0x13, snapshot.getRegBx());
        assertEquals(0x14, snapshot.getRegCx());
        assertEquals(0x15, snapshot.getRegDx());
        assertEquals(0x16, snapshot.getRegEx());
        assertEquals(0x17, snapshot.getRegHx());
        assertEquals(0x18, snapshot.getRegLx());
        assertEquals(0x1234, snapshot.getRegPC());
        assertEquals(0x5678, snapshot.getRegIX());
        assertEquals(0x9ABC, snapshot.getRegIY());
        assertEquals(0xFFFE, snapshot.getRegSP());
        assertEquals(0x3F, snapshot.getRegI());
        assertEquals(0x7F, snapshot.getRegR());
        assertTrue(snapshot.isFfIFF1());
        assertFalse(snapshot.isFfIFF2());
        assertTrue(snapshot.isPendingEI());
        assertFalse(snapshot.isActiveNMI());
        assertTrue(snapshot.isActiveINT());
        assertEquals(2, snapshot.getModeINT());
        assertTrue(snapshot.isHalted());
        assertEquals(0x4321, snapshot.getMemptr());
    }
}
