package spectrum.jfx.debug;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parser for Spectrum BASIC tokens and statements.
 * AI Generated
 */
public final class SpectrumBasicParser {

    private static final String[] TOKENS = {
            "RND", "INKEY$", "PI", "FN", "POINT", "SCREEN$", "ATTR", "AT", "TAB", "VAL$", "CODE", "VAL",
            "LEN", "SIN", "COS", "TAN", "ASN", "ACS", "ATN", "LN", "EXP", "INT", "SQR", "SGN", "ABS",
            "PEEK", "IN", "USR", "STR$", "CHR$", "NOT", "BIN", "OR", "AND", "<=", ">=", "<>", "LINE", "THEN",
            "TO", "STEP", "DEF FN", "CAT", "FORMAT", "MOVE", "ERASE", "OPEN #", "CLOSE #", "MERGE",
            "VERIFY", "BEEP", "CIRCLE", "INK", "PAPER", "FLASH", "BRIGHT", "INVERSE", "OVER", "OUT",
            "LPRINT", "LLIST", "STOP", "READ", "DATA", "RESTORE", "NEW", "BORDER", "CONTINUE", "DIM",
            "REM", "FOR", "GO TO", "GO SUB", "INPUT", "LOAD", "LIST", "LET", "PAUSE", "NEXT", "POKE",
            "PRINT", "PLOT", "RUN", "SAVE", "RANDOMIZE", "IF", "CLS", "DRAW", "CLEAR", "RETURN", "COPY"
    };

    public static List<String> parse(byte[] data) {
        List<String> out = new ArrayList<>();
        int p = 0;

        while (p + 4 <= data.length) {
            int line = ((data[p] & 0xFF) << 8) | (data[p + 1] & 0xFF);
            int len = (data[p + 2] & 0xFF) | ((data[p + 3] & 0xFF) << 8);
            p += 4;
            if (line == 0 || p + len > data.length) break;
            out.add(line + " " + decodeLine(Arrays.copyOfRange(data, p, p + len)));
            p += len;
        }
        return out;
    }

    private static String decodeLine(byte[] body) {
        StringBuilder s = new StringBuilder();
        boolean inString = false, inRem = false, inData = false;

        for (int i = 0; i < body.length; i++) {
            int b = body[i] & 0xFF;
            if (b == 0x0D) break;

            if (inRem) {
                s.append((char) b);
                continue;
            }

            if (b == '"') {
                inString = !inString;
                s.append('"');
                continue;
            }

            // -------- ASCII NUMBER --------
            if (!inString && b >= '0' && b <= '9') {
                int start = i;
                while (i < body.length && body[i] >= '0' && body[i] <= '9') i++;
                s.append(new String(body, start, i - start));
                if (i < body.length && body[i] == 0x0E) i += 6;
                i--;
                continue;
            }

            // -------- TOKENS --------
            if (!inString && !inData && b >= 0xA5) {
                String t = TOKENS[b - 0xA5];

                if (t.equals("VAL") && i + 1 < body.length && body[i + 1] == '"') {
                    int p = i + 2;
                    while (p < body.length && body[p] != '"') p++;
                    s.append(new String(body, i + 2, p - (i + 2)));
                    i = p;
                    if (i + 1 < body.length && body[i + 1] == 0x0E) i += 6;
                    continue;
                }

                if (t.equals("BIN") && i + 1 < body.length && body[i + 1] == 0x0E) {
                    double v = decodeNumber(body, i + 2);
                    s.append(format(v));
                    i += 6;
                    continue;
                }

                s.append(t);
                if (t.equals("REM")) inRem = true;
                if (t.equals("DATA")) inData = true;
                s.append(' ');
                continue;
            }

            // -------- NUMBER TAIL (IGNORE) --------
            if (!inString && b == 0x0E) {
                i += 5;
                continue;
            }

            s.append((char) b);
        }
        return s.toString().replaceAll("\\s+", " ").trim();
    }

    private static double decodeNumber(byte[] d, int p) {
        int e = d[p] & 0xFF;
        if (e == 0) return 0;
        long m =
                ((long) (d[p + 1] & 0xFF) << 24) |
                        ((long) (d[p + 2] & 0xFF) << 16) |
                        ((long) (d[p + 3] & 0xFF) << 8) |
                        ((long) (d[p + 4] & 0xFF));
        boolean neg = (m & 0x80000000L) != 0;
        m &= 0x7FFFFFFFL;
        double v = (m / (double) (1L << 31)) * Math.pow(2, e - 128);
        return neg ? -v : v;
    }

    private static String format(double d) {
        if (Math.floor(d) == d) return Long.toString((long) d);
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }
}
