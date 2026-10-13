package minigame;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Decorative motion only. The marker and the falling balls always move.
 * This follows the Windows "Animation effects" setting, the same idea as
 * reduced motion on the web game. -Dminigame.reduceMotion=true forces it off.
 */
final class Motion {
    /** Windows flag: are client-area animations turned on? */
    private static final int SPI_GETCLIENTAREAANIMATION = 0x1042;
    private static final boolean REDUCE = detect();

    private Motion() {
    }

    static boolean reduce() {
        return REDUCE;
    }

    private static boolean detect() {
        String override = System.getProperty("minigame.reduceMotion");
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        String os = System.getProperty("os.name", "");
        if (!os.toLowerCase().contains("win")) {
            return false;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment symbol = SymbolLookup.libraryLookup("user32", arena)
                    .find("SystemParametersInfoW")
                    .orElse(null);
            if (symbol == null) {
                return false;
            }
            MethodHandle call = Linker.nativeLinker().downcallHandle(
                    symbol,
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT));
            MemorySegment flag = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) call.invoke(SPI_GETCLIENTAREAANIMATION, 0, flag, 0);
            if (ok == 0) {
                return false;
            }
            // Zero means animation effects are switched off.
            return flag.get(ValueLayout.JAVA_INT, 0) == 0;
        } catch (Throwable error) {
            return false;
        }
    }
}
