package com.hsdc.copilot.cases;

import java.util.UUID;

/** Input of one chat turn. The photo bytes live only for the duration of the turn. */
public record TurnInput(String text, byte[] photoJpeg, UUID clientMessageId) {
}
