package net.bowspleef.ranked.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Reads an integer and nothing else: Jackson would turn 3.5 into 3 and "40" into 40, which would hide a client's
 * mistake. A fraction, a text or a number beyond an int is refused (a 400 through the handler).
 */
public class WholeNumberDeserializer extends StdDeserializer<Integer> {

    public WholeNumberDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return (Integer) context.handleUnexpectedToken(Integer.class, parser);
        }
        long value = parser.getLongValue();
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            return (Integer) context.handleWeirdNumberValue(Integer.class, value, "not a whole number of a usual size");
        }
        return (int) value;
    }
}
