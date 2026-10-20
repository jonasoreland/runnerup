package org.runnerup.workout;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.runnerup.common.util.Constants;

/** A bounded FIT Workout reader for running steps executable by the existing workout engine. */
public final class FitWorkoutImporter {
  private static final int MAX_FILE_BYTES = 1_048_576;
  private static final int MAX_RECORDS = 4_096;
  private static final int MAX_STEPS = 256;
  private static final int FILE_ID = 0;
  private static final int WORKOUT = 26;
  private static final int WORKOUT_STEP = 27;

  private FitWorkoutImporter() {}

  public static boolean isFit(byte[] bytes) {
    return bytes.length >= 12
        && bytes[8] == '.'
        && bytes[9] == 'F'
        && bytes[10] == 'I'
        && bytes[11] == 'T';
  }

  public static Workout parse(byte[] bytes) {
    if (bytes.length < 14 || bytes.length > MAX_FILE_BYTES || !isFit(bytes)) {
      throw invalid("header");
    }
    int headerSize = unsigned(bytes[0]);
    if (headerSize != 12 && headerSize != 14) throw unsupported("header size");
    long dataSize = scalar(bytes, 4, 4, false);
    if (dataSize > MAX_FILE_BYTES || headerSize + dataSize + 2 != bytes.length) {
      throw invalid("record length");
    }
    if (headerSize == 14) {
      int stored = (int) scalar(bytes, 12, 2, false);
      if (stored != 0 && stored != crc(bytes, 0, 12)) throw invalid("header CRC");
    }
    int end = headerSize + (int) dataSize;
    if (crc(bytes, 0, end) != scalar(bytes, end, 2, false)) throw invalid("file CRC");

    Definition[] definitions = new Definition[16];
    List<IndexedStep> steps = new ArrayList<>();
    int position = headerSize;
    int recordCount = 0;
    int fileType = -1;
    int expectedSteps = -1;
    boolean firstData = true;
    while (position < end) {
      if (++recordCount > MAX_RECORDS) throw unsupported("too many records");
      int record = unsigned(bytes[position++]);
      boolean compressed = (record & 0x80) != 0;
      int local = compressed ? ((record >> 5) & 3) : (record & 15);
      if (!compressed && (record & 0x40) != 0) {
        require(position + 5 <= end, "definition");
        int architecture = unsigned(bytes[position + 1]);
        if (architecture > 1) throw invalid("architecture");
        boolean bigEndian = architecture == 1;
        int global = (int) scalar(bytes, position + 2, 2, bigEndian);
        int count = unsigned(bytes[position + 4]);
        position += 5;
        require(position + count * 3 <= end, "field definitions");
        List<Field> fields = new ArrayList<>();
        for (int i = 0; i < count; i++) {
          fields.add(
              new Field(
                  unsigned(bytes[position]),
                  unsigned(bytes[position + 1]),
                  unsigned(bytes[position + 2])));
          position += 3;
        }
        int developerSize = 0;
        if ((record & 0x20) != 0) {
          require(position < end, "developer field count");
          int developerCount = unsigned(bytes[position++]);
          require(position + developerCount * 3 <= end, "developer definitions");
          for (int i = 0; i < developerCount; i++) {
            developerSize += unsigned(bytes[position + 1]);
            position += 3;
          }
        }
        definitions[local] = new Definition(global, bigEndian, fields, developerSize);
        continue;
      }
      Definition definition = definitions[local];
      if (definition == null) throw invalid("data without definition");
      if (firstData && definition.global != FILE_ID) {
        throw invalid("first message must be File ID");
      }
      if (!firstData
          && definition.global != FILE_ID
          && definition.global != WORKOUT
          && definition.global != WORKOUT_STEP) {
        for (Field field : definition.fields) {
          require(position + field.size <= end, "field data");
          position += field.size;
        }
        require(position + definition.developerSize <= end, "developer data");
        position += definition.developerSize;
        continue;
      }
      Map<Integer, Object> fields = new HashMap<>();
      for (Field field : definition.fields) {
        require(position + field.size <= end, "field data");
        Object value = readField(bytes, position, field, definition.bigEndian);
        if (value != null) fields.put(field.number, value);
        position += field.size;
      }
      require(position + definition.developerSize <= end, "developer data");
      position += definition.developerSize;
      if (firstData) {
        if (definition.global != FILE_ID) throw invalid("first message must be File ID");
        fileType = integer(fields, 0, "file type");
        if (fileType == 4) throw unsupported("FIT Activity file; select a FIT Workout file");
        if (fileType != 5) throw unsupported("FIT file type " + fileType);
        firstData = false;
      } else if (definition.global == FILE_ID) {
        throw unsupported("chained FIT files");
      } else if (definition.global == WORKOUT) {
        if (expectedSteps != -1) throw invalid("duplicate Workout message");
        int sport = integer(fields, 4, "sport");
        if (sport != 1) throw unsupported("sport " + sport + "; only running is executable");
        expectedSteps = integer(fields, 6, "workout step count");
        if (expectedSteps < 1 || expectedSteps > MAX_STEPS) throw unsupported("step count");
      } else if (definition.global == WORKOUT_STEP) {
        if (expectedSteps < 0) throw invalid("Workout Step before Workout");
        if (definition.developerSize > 0) throw unsupported("Workout Step developer fields");
        if (steps.size() >= expectedSteps) throw invalid("extra Workout Step");
        int index = integer(fields, 254, "step index");
        if (index != steps.size()) throw unsupported("nonsequential step index " + index);
        steps.add(new IndexedStep(index, fields));
      }
    }
    if (fileType != 5 || expectedSteps < 1 || steps.size() != expectedSteps) {
      throw invalid("missing Workout or Workout Step messages");
    }
    Workout workout = new Workout();
    workout.sport = Constants.DB.ACTIVITY.SPORT_RUNNING;
    workout.setWorkoutType(Constants.WORKOUT_TYPE.ADVANCED);
    List<IndexedStep> assembled = new ArrayList<>();
    for (IndexedStep source : steps) {
      Map<Integer, Object> fields = source.fields;
      int durationType = integer(fields, 1, "duration type");
      if (durationType == 6) {
        int start = integer(fields, 2, "repeat start index");
        int repetitions = integer(fields, 4, "repeat count");
        if (repetitions < 1 || repetitions > 100) throw unsupported("repeat count");
        if (integer(fields, 3, "repeat target type") != 2) {
          throw unsupported("repeat target type");
        }
        if ((fields.containsKey(5) && number(fields, 5, "repeat target low") != 0)
            || (fields.containsKey(6) && number(fields, 6, "repeat target high") != 0)) {
          throw unsupported("repeat target range");
        }
        for (int field : new int[] {8, 9, 10, 11, 12, 13, 19, 20, 21, 22}) {
          if (fields.containsKey(field)) throw unsupported("repeat field " + field);
        }
        int startPosition = -1;
        for (int i = 0; i < assembled.size(); i++) {
          if (assembled.get(i).index == start) startPosition = i;
        }
        if (startPosition < 0) throw unsupported("repeat start index " + start);
        RepeatStep repeat = new RepeatStep();
        repeat.setRepeatCount(repetitions);
        for (int i = startPosition; i < assembled.size(); i++) {
          if (assembled.get(i).step instanceof RepeatStep) throw unsupported("nested repeat");
          repeat.getSteps().add(assembled.get(i).step);
        }
        assembled.subList(startPosition, assembled.size()).clear();
        assembled.add(new IndexedStep(source.index, repeat));
      } else {
        assembled.add(new IndexedStep(source.index, convertStep(fields, durationType)));
      }
    }
    for (IndexedStep step : assembled) workout.addStep(step.step);
    return workout;
  }

  private static Step convertStep(Map<Integer, Object> fields, int durationType) {
    Step step = new Step();
    String name = (String) fields.get(0);
    if (name != null && !name.isBlank()) step.setName(name);
    String notes = (String) fields.get(8);
    if (notes != null && !notes.isBlank()) throw unsupported("step notes");
    if (fields.containsKey(9) && integer(fields, 9, "equipment") != 0) {
      throw unsupported("step equipment");
    }
    for (int field : new int[] {10, 11, 12, 13, 19, 20, 21, 22}) {
      if (fields.containsKey(field)) throw unsupported("step field " + field);
    }
    int intensity = fields.containsKey(7) ? integer(fields, 7, "intensity") : 0;
    switch (intensity) {
      case 0, 5 -> step.setIntensity(Intensity.ACTIVE);
      case 1 -> step.setIntensity(Intensity.RESTING);
      case 4 -> step.setIntensity(Intensity.RECOVERY);
      case 2 -> step.setIntensity(Intensity.WARMUP);
      case 3 -> step.setIntensity(Intensity.COOLDOWN);
      default -> throw unsupported("intensity");
    }
    long duration =
        durationType == 0 || durationType == 1 ? number(fields, 2, "duration value") : 0;
    switch (durationType) {
      case 0 -> {
        if (duration == 0) throw invalid("zero time duration");
        step.setDurationType(Dimension.TIME);
        step.setDurationValue(duration / 1000.0);
      }
      case 1 -> {
        if (duration == 0) throw invalid("zero distance duration");
        step.setDurationType(Dimension.DISTANCE);
        step.setDurationValue(duration / 100.0);
      }
      case 5 -> {
        if (fields.containsKey(2) && number(fields, 2, "duration value") != 0) {
          throw unsupported("open duration value");
        }
        step.setDurationType(null);
      }
      default -> throw unsupported("duration type " + durationType);
    }
    int targetType = integer(fields, 3, "target type");
    long zone = fields.containsKey(4) ? number(fields, 4, "target zone") : 0;
    long low = fields.containsKey(5) ? number(fields, 5, "target low") : 0;
    long high = fields.containsKey(6) ? number(fields, 6, "target high") : 0;
    if (targetType == 2) {
      if (zone != 0 || low != 0 || high != 0) throw unsupported("open target values");
    } else if (targetType == 0 || targetType == 1) {
      if (zone != 0) throw unsupported("target zone without local zone boundaries");
      if (low <= 0 || high < low) throw invalid("target range");
      if (targetType == 0) {
        step.setTargetType(Dimension.SPEED);
        step.setTargetValue(low / 1000.0, high / 1000.0);
      } else {
        if (low <= 100 || high <= 100) throw unsupported("relative heart-rate target");
        long minBpm = low - 100;
        long maxBpm = high - 100;
        if (minBpm < 30 || maxBpm > 240) throw unsupported("heart-rate target range");
        step.setTargetType(Dimension.HR);
        step.setTargetValue(minBpm, maxBpm);
      }
    } else {
      throw unsupported("target type " + targetType);
    }
    return step;
  }

  private static Object readField(byte[] bytes, int position, Field field, boolean bigEndian) {
    if (field.base == 7) {
      String value = new String(bytes, position, field.size, StandardCharsets.UTF_8);
      int end = value.indexOf('\0');
      return end < 0 ? value : value.substring(0, end);
    }
    int length =
        switch (field.base) {
          case 0, 1, 2, 10, 13 -> 1;
          case 0x83, 0x84, 0x8b -> 2;
          case 0x85, 0x86, 0x8c, 0x88 -> 4;
          default -> 0;
        };
    if (length == 0 || field.size != length) return null;
    long value = scalar(bytes, position, length, bigEndian);
    long invalid =
        switch (field.base) {
          case 0, 2 -> 0xffL;
          case 0x84 -> 0xffffL;
          case 0x86 -> 0xffffffffL;
          default -> -1L;
        };
    return value == invalid ? null : value;
  }

  private static int integer(Map<Integer, Object> fields, int number, String label) {
    long value = number(fields, number, label);
    if (value > Integer.MAX_VALUE) throw unsupported(label);
    return (int) value;
  }

  private static long number(Map<Integer, Object> fields, int number, String label) {
    Object value = fields.get(number);
    if (!(value instanceof Long)) throw invalid("missing " + label);
    return (Long) value;
  }

  private static long scalar(byte[] bytes, int start, int length, boolean bigEndian) {
    long value = 0;
    for (int i = 0; i < length; i++) {
      int shift = (bigEndian ? length - i - 1 : i) * 8;
      value |= (long) unsigned(bytes[start + i]) << shift;
    }
    return value;
  }

  private static int crc(byte[] bytes, int start, int end) {
    int value = 0;
    for (int i = start; i < end; i++) {
      value ^= unsigned(bytes[i]);
      for (int bit = 0; bit < 8; bit++) value = (value >>> 1) ^ ((value & 1) == 1 ? 0xa001 : 0);
    }
    return value & 0xffff;
  }

  private static void require(boolean condition, String label) {
    if (!condition) throw invalid(label);
  }

  private static int unsigned(byte value) {
    return value & 0xff;
  }

  private static IllegalArgumentException invalid(String reason) {
    return new IllegalArgumentException("Invalid FIT Workout " + reason);
  }

  private static IllegalArgumentException unsupported(String reason) {
    return new IllegalArgumentException("Unsupported FIT Workout " + reason);
  }

  private record Field(int number, int size, int base) {}

  private record Definition(int global, boolean bigEndian, List<Field> fields, int developerSize) {}

  private static final class IndexedStep {
    final int index;
    final Map<Integer, Object> fields;
    final Step step;

    IndexedStep(int index, Map<Integer, Object> fields) {
      this.index = index;
      this.fields = fields;
      this.step = null;
    }

    IndexedStep(int index, Step step) {
      this.index = index;
      this.fields = null;
      this.step = step;
    }
  }
}
