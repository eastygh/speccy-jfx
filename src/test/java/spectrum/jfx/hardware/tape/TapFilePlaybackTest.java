package spectrum.jfx.hardware.tape;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import spectrum.jfx.model.TapeFile;
import spectrum.jfx.model.TapeSection;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TapFilePlaybackTest {

    // Дублируем значения констант из TapFilePlayback для читаемости
    private static final int PILOT_PULSE_LENGTH = 2168;
    private static final int PAUSE_PULSE_LENGTH = 3_500_000;
    private static final int SYNC1_PULSE_LENGTH = 667;
    private static final int SYNC2_PULSE_LENGTH = 735;
    private static final int ZERO_PULSE_LENGTH = 855;
    private static final int ONE_PULSE_LENGTH = 1710;

    private TapeFile tapeFile;
    private TapeSection section;

    @BeforeEach
    void setup() {
        tapeFile = mock(TapeFile.class);
        section = mock(TapeSection.class);

// Один блок с типом HEADER и одним байтом данных 0x80 (1000 0000b), чтобы первый бит был '1'
        when(section.getType()).thenReturn(TapeSection.SectionType.PROGRAM);
        when(section.getData()).thenReturn(new byte[]{(byte) 0x80});
        when(tapeFile.getSections()).thenReturn(List.of(section));
    }

    @Test
    void earLevelAt_transitions_and_pulse_lengths() throws Exception {
// initialLevel = true
        TapFilePlayback sut = new TapFilePlayback(true, tapeFile, null);

// Включаем мотор с нулевого времени
        sut.setMotor(true, 0);

// Уменьшаем количество пилот‑импульсов до 2, чтобы быстро дойти до SYNC
        setPrivateLong(sut, "pilotPulseCount", 2L);

        long t = 0;

// t = 0: еще не истек PILOT_PULSE_LENGTH (стартовое окно), уровень должен быть initialLevel (true)
        assertTrue(sut.earLevelAt(t));

// t = PILOT_PULSE_LENGTH: произойдет первый флип уровня и переход из PAUSE в PILOT, длина станет PAUSE
        t += PILOT_PULSE_LENGTH;
        assertFalse(sut.earLevelAt(t));
        assertEquals(TapFilePlayback.TapeState.PILOT, getPrivateEnum(sut, "tapeState", TapFilePlayback.TapeState.class));
        assertEquals(PAUSE_PULSE_LENGTH, getPrivateLong(sut, "currentPulseLength"));

// t += PAUSE: флип уровня, вход в PILOT c установкой длины пилот‑импульса (2168), stage = 1, pilotCount--
        t += PAUSE_PULSE_LENGTH + 5;
        assertTrue(sut.earLevelAt(t));
        assertEquals(PILOT_PULSE_LENGTH, getPrivateLong(sut, "currentPulseLength"));
        assertEquals(1, getPrivateInt(sut, "pulseStage"));
        assertEquals(1L, getPrivateLong(sut, "pilotPulseCount"));

// t += 2168: флип уровня, stage -> 0, длина не меняется
        t += PILOT_PULSE_LENGTH;
        assertFalse(sut.earLevelAt(t));
        assertEquals(0, getPrivateInt(sut, "pulseStage"));
        assertEquals(PILOT_PULSE_LENGTH, getPrivateLong(sut, "currentPulseLength"));

// t += 2168: флип уровня, снова берется длина пилота, pilotCount-- до 0, stage = 1
        t += PILOT_PULSE_LENGTH;
        assertTrue(sut.earLevelAt(t));
        assertEquals(1, getPrivateInt(sut, "pulseStage"));
        assertEquals(0L, getPrivateLong(sut, "pilotPulseCount"));

// t += 2168: флип уровня, stage -> 0
        t += PILOT_PULSE_LENGTH;
        assertFalse(sut.earLevelAt(t));
        assertEquals(0, getPrivateInt(sut, "pulseStage"));

// t += 2168: флип уровня, так как pilotCount == 0, переход PILOT -> SYNC1, текущая длина = 667
        t += PILOT_PULSE_LENGTH;
        assertTrue(sut.earLevelAt(t));
        assertEquals(TapFilePlayback.TapeState.SYNC1, getPrivateEnum(sut, "tapeState", TapFilePlayback.TapeState.class));
        assertEquals(SYNC1_PULSE_LENGTH, getPrivateLong(sut, "currentPulseLength"));

// t += 667: флип уровня, переход SYNC1 -> SYNC2, длина = 735
        t += SYNC1_PULSE_LENGTH + 15;
        assertFalse(sut.earLevelAt(t));
        assertEquals(TapFilePlayback.TapeState.SYNC2, getPrivateEnum(sut, "tapeState", TapFilePlayback.TapeState.class));
        assertEquals(SYNC2_PULSE_LENGTH, getPrivateLong(sut, "currentPulseLength"));

// t += 735: флип уровня, переход SYNC2 -> DATA, длина по первому биту (у нас 1 => 1710)
        t += SYNC2_PULSE_LENGTH + 10;
        assertTrue(sut.earLevelAt(t));
        assertEquals(TapFilePlayback.TapeState.DATA, getPrivateEnum(sut, "tapeState", TapFilePlayback.TapeState.class));

        long firstBitLength = getPrivateLong(sut, "currentPulseLength");
        assertEquals(ONE_PULSE_LENGTH, firstBitLength, "Первый бит должен быть '1' => длина 1710");

// Проверим, что позиция бита продвинулась (с 7 до 6)
        assertEquals(6, getPrivateInt(sut, "bitPosition"));
// И что мы на первом байте
        assertEquals(0, getPrivateInt(sut, "dataPosition"));
    }

    // Helpers for reflection
    private static void setPrivateLong(Object target, String field, long value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.setLong(target, value);
    }

    private static long getPrivateLong(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getLong(target);
    }

    private static int getPrivateInt(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getInt(target);
    }

    private static <E extends Enum<E>> E getPrivateEnum(Object target, String field, Class<E> enumClass) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        Object v = f.get(target);
        return enumClass.cast(v);
    }
}
