package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** Binary FIT files built from the published record format, independent of the decoder. */
public class FitWorkoutImporterTest {
  @Test
  public void importsCheckedInBinaryWorkoutFixture() throws Exception {
    byte[] bytes;
    try (InputStream input = getClass().getResourceAsStream("/fit/running-intervals.fit")) {
      bytes = input.readAllBytes();
    }
    Workout workout = FitWorkoutImporter.parse(bytes);
    assertEquals(2, workout.getSteps().size());
    assertEquals("Warm up", workout.getSteps().get(0).getName());
    assertEquals(600, workout.getSteps().get(0).getDurationValue(), 0.001);
    assertEquals(400, workout.getSteps().get(1).getDurationValue(), 0.001);

    StringWriter output = new StringWriter();
    WorkoutSerializer.writeJSON(output, workout);
    JSONArray savedSteps =
        new JSONObject(output.toString())
            .getJSONObject("com.garmin.connect.workout.json.UserWorkoutJson")
            .getJSONArray("workoutSteps");
    assertEquals(2, savedSteps.length());
    assertEquals(600000, savedSteps.getJSONObject(0).getInt("endConditionValue"));
    assertEquals(40000, savedSteps.getJSONObject(1).getInt("endConditionValue"));
  }

  @Test
  public void importsWorkoutWithTwelveByteFileHeader() {
    byte[] file = fitWith12ByteHeader(fit(5, 1, 1, step(0, 0, 120_000, 2, 0, 0, 0, 0)));

    Step imported = FitWorkoutImporter.parse(file).getSteps().get(0);

    assertEquals(Dimension.TIME, imported.getDurationType());
    assertEquals(120, imported.getDurationValue(), 0.001);
  }

  @Test
  public void importsBigEndianDefinitionsAndWorkoutStepValues() {
    Step imported = FitWorkoutImporter.parse(fitBigEndian()).getSteps().get(0);

    assertEquals(Dimension.DISTANCE, imported.getDurationType());
    assertEquals(1500, imported.getDurationValue(), 0.001);
    assertEquals(Dimension.SPEED, imported.getTargetType());
    assertEquals(3.2, imported.getTargetValue().minValue, 0.001);
    assertEquals(4.5, imported.getTargetValue().maxValue, 0.001);
  }

  @Test
  public void importsRunningTimeAndAbsoluteSpeed() {
    byte[] file = fit(5, 1, 1, step(0, 0, 120_000, 0, 0, 3000, 4000, 0));
    Workout workout = FitWorkoutImporter.parse(file);
    Step step = workout.getSteps().get(0);
    assertEquals(Dimension.TIME, step.getDurationType());
    assertEquals(120, step.getDurationValue(), 0.001);
    assertEquals(Dimension.SPEED, step.getTargetType());
    assertEquals(3, step.getTargetValue().minValue, 0.001);
    assertEquals(4, step.getTargetValue().maxValue, 0.001);
  }

  @Test
  public void preservesDistanceRecoveryAndRepeatBlock() {
    byte[] file =
        fit(
            5,
            1,
            3,
            step(0, 1, 40_000, 2, 0, 0, 0, 0),
            step(1, 0, 60_000, 2, 0, 0, 0, 4),
            step(2, 6, 0, 2, 3, 0, 0, 0));
    RepeatStep repeat = (RepeatStep) FitWorkoutImporter.parse(file).getSteps().get(0);
    assertEquals(3, repeat.getRepeatCount());
    assertEquals(2, repeat.getSteps().size());
    assertEquals(400, repeat.getSteps().get(0).getDurationValue(), 0.001);
    assertEquals(Intensity.RECOVERY, repeat.getSteps().get(1).getIntensity());
  }

  @Test
  public void decodesAbsoluteHeartRateWithFitOffset() {
    byte[] file = fit(5, 1, 1, step(0, 0, 90_000, 1, 0, 225, 250, 2));
    Step step = FitWorkoutImporter.parse(file).getSteps().get(0);
    assertEquals(Intensity.WARMUP, step.getIntensity());
    assertEquals(Dimension.HR, step.getTargetType());
    assertEquals(125, step.getTargetValue().minValue, 0.001);
    assertEquals(150, step.getTargetValue().maxValue, 0.001);
  }

  @Test
  public void keepsFitRestDistinctFromRecovery() {
    byte[] file = fit(5, 1, 1, step(0, 0, 60_000, 2, 0, 0, 0, 1));
    assertEquals(
        Intensity.RESTING, FitWorkoutImporter.parse(file).getSteps().get(0).getIntensity());
  }

  @Test
  public void importsOpenStepWhenDurationFieldIsOmitted() {
    byte[] file = fitOpenStepWithoutDuration();
    Workout workout = FitWorkoutImporter.parse(file);
    assertEquals(null, workout.getSteps().get(0).getDurationType());
  }

  @Test
  public void rejectsFilesWithTooManyRecords() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> FitWorkoutImporter.parse(fitWithUnknownMessages(4_100)));
    assertTrue(error.getMessage().contains("too many records"));
  }

  @Test
  public void defaultsOmittedOptionalIntensityToActive() {
    byte[] file = fitWithoutIntensity(step(0, 0, 60_000, 2, 0, 0, 0, 0));
    assertEquals(Intensity.ACTIVE, FitWorkoutImporter.parse(file).getSteps().get(0).getIntensity());
  }

  @Test
  public void rejectsActivityEvenWhenItContainsWorkoutMessages() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> FitWorkoutImporter.parse(fit(4, 1, 1, step(0, 0, 60_000, 2, 0, 0, 0, 0))));
    assertTrue(error.getMessage().contains("Activity"));
  }

  @Test
  public void rejectsTargetZoneWithoutKnownBoundaries() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> FitWorkoutImporter.parse(fit(5, 1, 1, step(0, 0, 60_000, 1, 2, 0, 0, 0))));
    assertTrue(error.getMessage().contains("zone"));
  }

  @Test
  public void rejectsRepeatWithExtraTargetRange() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                FitWorkoutImporter.parse(
                    fit(
                        5,
                        1,
                        2,
                        step(0, 0, 60_000, 2, 0, 0, 0, 0),
                        step(1, 6, 0, 2, 3, 1000, 2000, 0))));
    assertTrue(error.getMessage().contains("repeat target"));
  }

  @Test
  public void rejectsCorruptCrcAndTruncation() {
    byte[] file = fit(5, 1, 1, step(0, 0, 60_000, 2, 0, 0, 0, 0));
    file[file.length - 3] ^= 1;
    assertThrows(IllegalArgumentException.class, () -> FitWorkoutImporter.parse(file));
    assertThrows(
        IllegalArgumentException.class,
        () -> FitWorkoutImporter.parse(new byte[] {14, 0x20, 0, 0}));
  }

  private static int[] step(
      int index,
      int durationType,
      int durationValue,
      int targetType,
      int targetValue,
      int low,
      int high,
      int intensity) {
    return new int[] {
      index, durationType, durationValue, targetType, targetValue, low, high, intensity
    };
  }

  private static byte[] fit(int fileType, int sport, int count, int[]... steps) {
    return fitWithIntensity(true, fileType, sport, count, steps);
  }

  private static byte[] fitWithoutIntensity(int[]... steps) {
    return fitWithIntensity(false, 5, 1, steps.length, steps);
  }

  private static byte[] fitWithIntensity(
      boolean includeIntensity, int fileType, int sport, int count, int[]... steps) {
    ByteArrayOutputStream records = new ByteArrayOutputStream();
    definition(records, 0, 0, new int[][] {{0, 1, 0}});
    records.write(0);
    records.write(fileType);
    definition(records, 1, 26, new int[][] {{4, 1, 0}, {6, 2, 0x84}});
    records.write(1);
    records.write(sport);
    little(records, count, 2);
    int[][] stepFields =
        includeIntensity
            ? new int[][] {
              {254, 2, 0x84},
              {1, 1, 0},
              {2, 4, 0x86},
              {3, 1, 0},
              {4, 4, 0x86},
              {5, 4, 0x86},
              {6, 4, 0x86},
              {7, 1, 0}
            }
            : new int[][] {
              {254, 2, 0x84},
              {1, 1, 0},
              {2, 4, 0x86},
              {3, 1, 0},
              {4, 4, 0x86},
              {5, 4, 0x86},
              {6, 4, 0x86}
            };
    definition(records, 2, 27, stepFields);
    for (int[] step : steps) {
      records.write(2);
      little(records, step[0], 2);
      records.write(step[1]);
      little(records, step[2], 4);
      records.write(step[3]);
      little(records, step[4], 4);
      little(records, step[5], 4);
      little(records, step[6], 4);
      if (includeIntensity) records.write(step[7]);
    }
    ByteArrayOutputStream file = new ByteArrayOutputStream();
    file.write(14);
    file.write(0x20);
    little(file, 0, 2);
    little(file, records.size(), 4);
    file.write('.');
    file.write('F');
    file.write('I');
    file.write('T');
    little(file, crc(file.toByteArray()), 2);
    file.writeBytes(records.toByteArray());
    little(file, crc(file.toByteArray()), 2);
    return file.toByteArray();
  }

  private static byte[] fitOpenStepWithoutDuration() {
    ByteArrayOutputStream records = new ByteArrayOutputStream();
    definition(records, 0, 0, new int[][] {{0, 1, 0}});
    records.write(0);
    records.write(5);
    definition(records, 1, 26, new int[][] {{4, 1, 0}, {6, 2, 0x84}});
    records.write(1);
    records.write(1);
    little(records, 1, 2);
    definition(
        records,
        2,
        27,
        new int[][] {
          {254, 2, 0x84}, {1, 1, 0}, {3, 1, 0}, {4, 4, 0x86}, {5, 4, 0x86}, {6, 4, 0x86}
        });
    records.write(2);
    little(records, 0, 2);
    records.write(5); // FIT open duration
    records.write(2); // FIT open target
    little(records, 0, 4);
    little(records, 0, 4);
    little(records, 0, 4);
    return file(records.toByteArray());
  }

  private static byte[] fitBigEndian() {
    ByteArrayOutputStream records = new ByteArrayOutputStream();
    definition(records, 0, 0, true, new int[][] {{0, 1, 0}});
    records.write(0);
    records.write(5);
    definition(records, 1, 26, true, new int[][] {{4, 1, 0}, {6, 2, 0x84}});
    records.write(1);
    records.write(1);
    big(records, 1, 2);
    definition(
        records,
        2,
        27,
        true,
        new int[][] {
          {254, 2, 0x84},
          {1, 1, 0},
          {2, 4, 0x86},
          {3, 1, 0},
          {4, 4, 0x86},
          {5, 4, 0x86},
          {6, 4, 0x86},
          {7, 1, 0}
        });
    records.write(2);
    big(records, 0, 2);
    records.write(1);
    big(records, 150_000, 4);
    records.write(0);
    big(records, 0, 4);
    big(records, 3200, 4);
    big(records, 4500, 4);
    records.write(0);
    return file(records.toByteArray());
  }

  private static byte[] fitWith12ByteHeader(byte[] original) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    output.write(12);
    output.write(original[1]);
    output.write(original[2]);
    output.write(original[3]);
    output.writeBytes(java.util.Arrays.copyOfRange(original, 4, 12));
    output.writeBytes(java.util.Arrays.copyOfRange(original, 14, original.length - 2));
    little(output, crc(output.toByteArray()), 2);
    return output.toByteArray();
  }

  private static byte[] fitWithUnknownMessages(int count) {
    ByteArrayOutputStream records = new ByteArrayOutputStream();
    definition(records, 0, 0, new int[][] {{0, 1, 0}});
    records.write(0);
    records.write(5);
    definition(records, 1, 99, new int[][] {});
    for (int i = 0; i < count; i++) records.write(1);
    return file(records.toByteArray());
  }

  private static byte[] file(byte[] records) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    output.write(14);
    output.write(0x20);
    little(output, 0, 2);
    little(output, records.length, 4);
    output.write('.');
    output.write('F');
    output.write('I');
    output.write('T');
    little(output, crc(output.toByteArray()), 2);
    output.writeBytes(records);
    little(output, crc(output.toByteArray()), 2);
    return output.toByteArray();
  }

  private static void definition(
      ByteArrayOutputStream output, int local, int global, int[][] fields) {
    definition(output, local, global, false, fields);
  }

  private static void definition(
      ByteArrayOutputStream output, int local, int global, boolean bigEndian, int[][] fields) {
    output.write(0x40 | local);
    output.write(0);
    output.write(bigEndian ? 1 : 0);
    if (bigEndian) {
      big(output, global, 2);
    } else {
      little(output, global, 2);
    }
    output.write(fields.length);
    for (int[] field : fields) {
      output.write(field[0]);
      output.write(field[1]);
      output.write(field[2]);
    }
  }

  private static void little(ByteArrayOutputStream output, int value, int length) {
    for (int i = 0; i < length; i++) output.write(value >>> (i * 8));
  }

  private static void big(ByteArrayOutputStream output, int value, int length) {
    for (int i = length - 1; i >= 0; i--) output.write(value >>> (i * 8));
  }

  private static int crc(byte[] data) {
    int crc = 0;
    for (byte item : data) {
      crc ^= item & 0xff;
      for (int bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) == 1 ? 0xa001 : 0);
    }
    return crc & 0xffff;
  }
}
