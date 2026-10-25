package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import org.junit.Test;

public class StrengthResultProtocolTest {
  private static final String SESSION = "99f7f1de-73ad-4a52-8d22-3df6a5b8b75a";
  private static final String RESULT = "fef77b0a-7012-4dd7-a374-cf7a1dd6f083";

  @Test
  public void resultRoundTripPreservesConfirmedAndUnknownMeasurements() throws Exception {
    StrengthResultProtocol.Result original =
        new StrengthResultProtocol.Result(
            SESSION,
            RESULT,
            0,
            "push-up",
            "Armhävning",
            2,
            14,
            null,
            1_761_341_220_000L,
            1_761_341_250_000L,
            30,
            null,
            null,
            8.0,
            1,
            "Rent utförande");

    StrengthResultProtocol.Result restored =
        StrengthResultProtocol.decodeResult(StrengthResultProtocol.encodeResult(original));

    assertEquals(original, restored);
  }

  @Test
  public void acknowledgementRoundTripPreservesOutcome() throws Exception {
    StrengthResultProtocol.Acknowledgement original =
        new StrengthResultProtocol.Acknowledgement(
            SESSION, RESULT, StrengthResultProtocol.AckOutcome.DUPLICATE);

    assertEquals(
        original,
        StrengthResultProtocol.decodeAcknowledgement(
            StrengthResultProtocol.encodeAcknowledgement(original)));
  }

  @Test
  public void rejectsInvalidIdentityAndOversizedOptionalText() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StrengthResultProtocol.Result("bad", RESULT, 0, "step", "Push-up", 1, 10));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StrengthResultProtocol.Result(
                SESSION,
                RESULT,
                0,
                "push-up",
                "Armhävning",
                1,
                10,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "x".repeat(2_000)));
  }

  @Test
  public void stableResultIdentityDistinguishesRepeatedExpandedExercisePositions() {
    assertNotEquals(
        StrengthResultProtocol.stableResultId(SESSION, 0, "repeat-step", 1),
        StrengthResultProtocol.stableResultId(SESSION, 1, "repeat-step", 1));
  }

  @Test
  public void rejectsMalformedAndUnsupportedMessages() throws Exception {
    byte[] encoded = StrengthResultProtocol.encodeResult(sample());
    byte[] unsupported = encoded.clone();
    unsupported[4] = (byte) (StrengthResultProtocol.CURRENT_VERSION + 1);

    assertThrows(
        IOException.class, () -> StrengthResultProtocol.decodeResult(new byte[] {1, 2, 3}));
    assertThrows(IOException.class, () -> StrengthResultProtocol.decodeResult(unsupported));
  }

  @Test
  public void rejectsNonCompletedAndEmptyPerformanceResults() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> sample().withStatus(StrengthResultProtocol.ResultStatus.SKIPPED));
    assertThrows(
        IllegalArgumentException.class, () -> sample().withMeasurements(null, null, null, null));
  }

  private static StrengthResultProtocol.Result sample() {
    return new StrengthResultProtocol.Result(SESSION, RESULT, 0, "push-up", "Armhävning", 1, 10);
  }
}
