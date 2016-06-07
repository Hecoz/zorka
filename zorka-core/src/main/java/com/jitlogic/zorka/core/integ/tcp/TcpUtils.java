/**
 * Copyright 2014 Daniel Makoto Iguchi <daniel.iguchi@gmail.com>
 * Copyright 2012-2015 Rafal Lewczuk <rafal.lewczuk@jitlogic.com>
 * <p/>
 * This is free software. You can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p/>
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License along with this software. If
 * not, see <http://www.gnu.org/licenses/>.
 */
package com.jitlogic.zorka.core.integ.tcp;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

public final class TcpUtils {

    private static final CharsetDecoder UTF8_DECODER;

    static {
        UTF8_DECODER = Charset.forName("UTF-8").newDecoder();
        UTF8_DECODER.onMalformedInput(CodingErrorAction.IGNORE);
        UTF8_DECODER.onUnmappableCharacter(CodingErrorAction.IGNORE);
    }

    /**
     * Creates a Zabbbix message with a "ZBXD\x01" + message.len()[8 byte] + message
     *
     * @param msg
     * @return
     */
    public static byte[] encode(String msg) {
        return msg.getBytes();
    }

    /**
     * Creates an Agent Data String Message
     *
     * @param result
     * @return
     */
    public static String createAgentData(BulkResult result) {
        return result.toString();
    }

    public static String removeInvalidCharacters(Object text) throws CharacterCodingException {
        if (text == null) {
            return null;
        }
        return UTF8_DECODER.decode(ByteBuffer.wrap(text.toString().replaceAll("\"", "'").replaceAll(
                "[\n\r]", "").getBytes())).toString();
    }

    public static String ellipsize(String text, int maxsize) {
        if (text == null) {
            return null;
        }
        if (text.length() > maxsize && maxsize > 4) {
            int half = maxsize / 2;
            return text.substring(0, half) + "...." + text.substring(text.length() - half);
        }
        return text;
    }

}
