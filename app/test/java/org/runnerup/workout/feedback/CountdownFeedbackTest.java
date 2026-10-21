package org.runnerup.workout.feedback;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import java.util.HashMap;
import org.junit.Test;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;

public class CountdownFeedbackTest {
  @Test
  public void forwardsFormattedCountdownAndVisibilityToDisplay() {
    CountdownDisplay display = mock(CountdownDisplay.class);
    Formatter formatter = mock(Formatter.class);
    Workout workout = mock(Workout.class);
    Context context = mock(Context.class);
    when(workout.getRemaining(Scope.STEP, Dimension.TIME)).thenReturn(5.0, 0.0);
    when(formatter.formatRemaining(Formatter.Format.TXT_SHORT, Dimension.TIME, 5.0))
        .thenReturn("5 sec");

    HashMap<String, Object> bindValues = new HashMap<>();
    bindValues.put(Workout.KEY_COUNTER_VIEW, display);
    bindValues.put(Workout.KEY_FORMATTER, formatter);
    CountdownFeedback feedback = new CountdownFeedback(Scope.STEP, Dimension.TIME);
    feedback.onBind(workout, bindValues);

    feedback.onStart(workout);
    feedback.emit(workout, context);
    feedback.emit(workout, context);
    feedback.onEnd(workout);

    verify(display).show("");
    verify(display).show("5 sec");
    verify(display).hide();
    verify(display).remove();
  }
}
