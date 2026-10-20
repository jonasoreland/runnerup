package org.runnerup.workout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded file reading and a concise confirmation summary for a parsed plan package. */
public final class RelvenPlanImport {
  private static final int MAX_PACKAGE_BYTES = 4_194_304;
  private static final int MAX_PREVIEW_SESSIONS = 7;

  private RelvenPlanImport() {}

  public static String read(InputStream input) throws IOException {
    if (input == null) throw new IOException("Could not open plan file");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int length;
    while ((length = input.read(buffer)) != -1) {
      if (output.size() > MAX_PACKAGE_BYTES - length) {
        throw new IOException("Plan package is larger than 4 MiB");
      }
      output.write(buffer, 0, length);
    }
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(output.toByteArray()))
          .toString();
    } catch (CharacterCodingException error) {
      throw new IOException("Plan package is not valid UTF-8", error);
    }
  }

  public static String preview(RelvenPlanImporter.Plan plan) {
    List<RelvenPlanImporter.Session> sessions = new ArrayList<>(plan.days());
    sessions.sort(Comparator.comparing(RelvenPlanImporter.Session::date));
    String firstDate = sessions.get(0).date();
    String lastDate = sessions.get(sessions.size() - 1).date();
    StringBuilder summary =
        new StringBuilder()
            .append(plan.name())
            .append("\n")
            .append(firstDate)
            .append("–")
            .append(lastDate)
            .append("\n")
            .append(plan.workoutDays())
            .append(plan.workoutDays() == 1 ? " workout day" : " workout days")
            .append(" · ")
            .append(plan.restDays())
            .append(plan.restDays() == 1 ? " rest day" : " rest days")
            .append("\n\n");

    int count = Math.min(sessions.size(), MAX_PREVIEW_SESSIONS);
    for (int i = 0; i < count; i++) {
      RelvenPlanImporter.Session session = sessions.get(i);
      summary.append(session.date()).append(" · ");
      if (session.kind().equals("rest")) summary.append("Rest day");
      else summary.append("Workout (").append(session.workoutId()).append(")");
      summary.append("\n");
    }
    if (sessions.size() > MAX_PREVIEW_SESSIONS) {
      summary.append("… plus ").append(sessions.size() - MAX_PREVIEW_SESSIONS).append(" more\n");
    }
    summary.append(
        "\nThese dates are one-time. Saving does not activate the plan or change your schedule.");
    return summary.toString();
  }
}
