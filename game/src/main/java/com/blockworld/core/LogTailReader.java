package com.blockworld.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Reads a bounded UTF-8 tail so large diagnostic logs cannot exhaust launcher memory. */
public final class LogTailReader {
    private static final long MAX_BYTES = 1_048_576L;

    private LogTailReader() { }

    public static String readTail(Path file, int maxChars) throws IOException {
        Objects.requireNonNull(file, "file");
        if (maxChars < 1) throw new IllegalArgumentException("Maximum character count must be positive.");
        long byteLimit = Math.min(MAX_BYTES, Math.max(4096L, (long) maxChars * 4L));
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            long start = Math.max(0L, size - byteLimit);
            channel.position(start);
            int capacity = (int) (size - start);
            ByteBuffer buffer = ByteBuffer.allocate(capacity);
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer);
                if (read < 0) break;
                if (read == 0) break;
            }
            buffer.flip();
            String result = StandardCharsets.UTF_8.decode(buffer).toString();
            boolean truncated = start > 0L;
            if (truncated) {
                int firstLineBreak = result.indexOf('\\n');
                result = firstLineBreak >= 0 ? result.substring(firstLineBreak + 1) : "";
            }
            if (result.length() > maxChars) {
                result = result.substring(result.length() - maxChars);
            }
            return truncated ? "[Показан конец журнала; начало опущено]\\n" + result : result;
        }
    }
}
