package org.runnerup.workout;

import android.content.Context;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.runnerup.util.Formatter;
import org.runnerup.util.Formatter.Format;

/** Builds a compact, localized review of workout steps before an imported workout is saved. */
public final class WorkoutImportPreview {
  private static final int MAX_PREVIEW_STEPS = 128;
  private static final int MAX_NESTING = 32;
  private static final int MAX_STEP_NAME_LENGTH = 120;

  private WorkoutImportPreview() {}

  public static String format(Context context, Workout workout) {
    Formatter formatter = new Formatter(context);
    StringBuilder preview = new StringBuilder();
    Deque<StepCursor> pending = new ArrayDeque<>();
    pending.push(new StepCursor(workout.getSteps(), 0));
    int renderedSteps = 0;
    boolean truncated = false;
    while (!pending.isEmpty() && renderedSteps < MAX_PREVIEW_STEPS) {
      StepCursor cursor = pending.peek();
      if (cursor.index >= cursor.steps.size()) {
        pending.pop();
        continue;
      }

      Step step = cursor.steps.get(cursor.index++);
      if (preview.length() > 0) preview.append('\n');
      for (int level = 0; level < cursor.level; level++) preview.append("  ");
      renderedSteps++;

      if (step instanceof RepeatStep repeat) {
        preview.append(
            context.getString(org.runnerup.common.R.string.repeat_times, repeat.getRepeatCount()));
        if (hasText(step.getName())) {
          preview.append(" · ").append(step.getName());
        }
      } else if (hasText(step.getName())) {
        String name = step.getName();
        preview.append(
            name.length() > MAX_STEP_NAME_LENGTH
                ? name.substring(0, MAX_STEP_NAME_LENGTH) + "…"
                : name);
      } else if (step.getIntensity() != null) {
        preview.append(context.getString(step.getIntensity().getTextId()));
      }

      if (step instanceof RepeatStep repeat) {
        if (cursor.level >= MAX_NESTING) {
          truncated = true;
        } else {
          pending.push(new StepCursor(repeat.getSteps(), cursor.level + 1));
        }
        continue;
      }

      Dimension durationType = step.getDurationType();
      if (durationType != null) {
        appendDetail(
            preview, formatter.format(Format.TXT_LONG, durationType, step.getDurationValue()));
      }

      Dimension targetType = step.getTargetType();
      Range targetValue = step.getTargetValue();
      if (targetType != null && targetValue != null) {
        String minimum = formatter.format(Format.TXT_LONG, targetType, targetValue.minValue);
        String maximum = formatter.format(Format.TXT_LONG, targetType, targetValue.maxValue);
        String value = minimum.equals(maximum) ? minimum : minimum + "–" + maximum;
        appendDetail(
            preview,
            context.getString(org.runnerup.common.R.string.Target)
                + " "
                + context.getString(targetType.getTextId())
                + ": "
                + value);
      }
    }
    if (!pending.isEmpty() || truncated) {
      if (preview.length() > 0) preview.append('\n');
      preview.append(
          context.getString(org.runnerup.common.R.string.additional_workout_steps_hidden));
    }
    return preview.toString();
  }

  private static final class StepCursor {
    private final List<Step> steps;
    private final int level;
    private int index;

    private StepCursor(List<Step> steps, int level) {
      this.steps = steps;
      this.level = level;
    }
  }

  private static void appendDetail(StringBuilder preview, String detail) {
    preview.append(" · ").append(detail);
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
