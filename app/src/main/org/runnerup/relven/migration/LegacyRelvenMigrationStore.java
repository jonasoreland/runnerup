package org.runnerup.relven.migration;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutSerializer;

/** Stages imported workout files and publishes them with an idempotent private manifest. */
public final class LegacyRelvenMigrationStore {
  private static final int MANIFEST_VERSION = 1;
  private final Context context;
  private final File root;
  private final File stagingDirectory;
  private final File manifest;
  private final AtomicLinker atomicLinker;
  private final ManifestWriter manifestWriter;

  public LegacyRelvenMigrationStore(Context context) {
    this(
        context,
        (source, destination) -> {
          try {
            Os.link(source.getPath(), destination.getPath());
          } catch (ErrnoException e) {
            throw new IOException("Unable to publish staged workout without replacing a file", e);
          }
        },
        LegacyRelvenMigrationStore::writeAtomicManifest);
  }

  LegacyRelvenMigrationStore(Context context, AtomicLinker atomicLinker) {
    this(context, atomicLinker, LegacyRelvenMigrationStore::writeAtomicManifest);
  }

  LegacyRelvenMigrationStore(
      Context context, AtomicLinker atomicLinker, ManifestWriter manifestWriter) {
    this.context = context.getApplicationContext();
    root = new File(context.getFilesDir(), "legacy-migration");
    stagingDirectory = new File(root, "workout-staging");
    manifest = new File(root, "workout-import-manifest-v1.json");
    this.atomicLinker = atomicLinker;
    this.manifestWriter = manifestWriter;
  }

  public StagedWorkout stage(Context ignored, SourceIdentity identity, Workout workout)
      throws IOException {
    ensureDirectory(root);
    ensureDirectory(stagingDirectory);
    String serialized;
    try {
      StringWriter writer = new StringWriter();
      WorkoutSerializer.writeJSON(writer, workout);
      serialized = writer.toString();
    } catch (JSONException e) {
      throw new IOException("Unable to serialize an imported workout", e);
    }
    String contentHash = sha256(serialized.getBytes(StandardCharsets.UTF_8));
    String identityKey = identity.key();
    List<Entry> entries = readEntries();
    Entry existing = find(entries, identityKey);
    if (existing != null) {
      if (!existing.sha256.equals(contentHash)) {
        return new StagedWorkout(identityKey, existing.fileName, null, contentHash, false, true);
      }
      File destination = WorkoutSerializer.getFile(context, existing.fileName);
      if ("imported".equals(existing.status)) {
        if (destination.isFile() && contentHash.equals(sha256(destination))) {
          return new StagedWorkout(identityKey, existing.fileName, null, contentHash, true, false);
        }
        return new StagedWorkout(identityKey, existing.fileName, null, contentHash, false, true);
      }
      File stagedFile = new File(stagingDirectory, existing.stagedName);
      if (!stagedFile.isFile() || !contentHash.equals(sha256(stagedFile))) {
        writeStagedFile(stagedFile, serialized);
      }
      return new StagedWorkout(
          identityKey, existing.fileName, existing.stagedName, contentHash, false, false);
    }

    String fileName = destinationName(identity);
    File destination = WorkoutSerializer.getFile(context, fileName);
    if (destination.exists()) {
      return new StagedWorkout(identityKey, fileName, null, contentHash, false, true);
    }
    String stagedName = identityHash(identityKey) + ".stage";
    File stagedFile = new File(stagingDirectory, stagedName);
    if (stagedFile.exists() && !contentHash.equals(sha256(stagedFile))) {
      throw new IOException("A conflicting staged workout already exists");
    }
    if (!stagedFile.exists()) writeStagedFile(stagedFile, serialized);
    Entry entry = new Entry(identity, fileName, stagedName, contentHash, "prepared");
    entries.add(entry);
    writeEntries(entries);
    return new StagedWorkout(identityKey, fileName, stagedName, contentHash, false, false);
  }

  public StoreResult publish(List<StagedWorkout> stagedWorkouts) {
    StoreResult result = new StoreResult();
    Set<String> seen = new HashSet<>();
    List<Entry> entries;
    try {
      entries = readEntries();
    } catch (IOException e) {
      result.failed++;
      return result;
    }
    for (StagedWorkout staged : stagedWorkouts) {
      if (!seen.add(staged.identityKey)) continue;
      if (staged.conflict) {
        result.conflicting++;
        continue;
      }
      Entry entry = find(entries, staged.identityKey);
      if (entry == null || !entry.sha256.equals(staged.sha256)) {
        result.failed++;
        continue;
      }
      File destination = WorkoutSerializer.getFile(context, entry.fileName);
      if (staged.alreadyImported) {
        try {
          if (destination.isFile() && entry.sha256.equals(sha256(destination))) {
            result.alreadyImported++;
          } else {
            result.conflicting++;
          }
        } catch (IOException e) {
          result.failed++;
        }
        continue;
      }
      if (destination.exists()) {
        try {
          if (!entry.sha256.equals(sha256(destination))) {
            result.conflicting++;
            continue;
          }
        } catch (IOException e) {
          result.failed++;
          continue;
        }
        boolean newlyPublished = "prepared".equals(entry.status);
        entry.status = "imported";
        try {
          writeEntries(entries);
          deleteStaged(entry);
          if (newlyPublished) result.imported++;
          else result.alreadyImported++;
        } catch (IOException e) {
          result.failed++;
        }
        continue;
      }
      File stagedFile = new File(stagingDirectory, entry.stagedName);
      boolean stagedHashMatches;
      try {
        stagedHashMatches = stagedFile.isFile() && entry.sha256.equals(sha256(stagedFile));
      } catch (IOException e) {
        stagedHashMatches = false;
      }
      if (!stagedHashMatches) {
        result.failed++;
        continue;
      }
      try {
        atomicLinker.createLink(stagedFile, destination);
      } catch (IOException e) {
        if (destination.exists()) result.conflicting++;
        else result.failed++;
        continue;
      }
      entry.status = "imported";
      try {
        writeEntries(entries);
        deleteStaged(entry);
        result.imported++;
      } catch (IOException e) {
        // The prepared entry and published file are reconciled by hash on the next run.
        result.failed++;
      }
    }
    return result;
  }

  public File manifestFile() {
    return manifest;
  }

  interface AtomicLinker {
    void createLink(File source, File destination) throws IOException;
  }

  interface ManifestWriter {
    void write(File destination, byte[] contents) throws IOException;
  }

  String destinationName(SourceIdentity identity) {
    String label = identity.sourceName.trim().replaceAll("[^A-Za-z0-9._ -]", " ");
    label = label.replaceAll("\\s+", " ").trim();
    if (label.isEmpty()) label = identity.workoutId;
    if (label.length() > 48) label = label.substring(0, 48).trim();
    return "Relven - " + label + " - legacy-" + identityHash(identity.key()) + ".json";
  }

  private void writeStagedFile(File file, String content) throws IOException {
    File temporary = new File(stagingDirectory, file.getName() + ".pending");
    if (temporary.exists() && !temporary.delete())
      throw new IOException("Unable to clear staged workout");
    try (FileOutputStream output = new FileOutputStream(temporary)) {
      output.write(content.getBytes(StandardCharsets.UTF_8));
      output.getFD().sync();
    }
    if (file.exists() && !file.delete()) throw new IOException("Unable to replace staged workout");
    if (!temporary.renameTo(file)) throw new IOException("Unable to finalize staged workout");
  }

  private List<Entry> readEntries() throws IOException {
    List<Entry> entries = new ArrayList<>();
    if (!manifest.exists()) return entries;
    try (FileInputStream input = new FileInputStream(manifest);
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) {
        if (output.size() + count > 16 * 1024 * 1024) {
          throw new IOException("Legacy workout manifest is too large");
        }
        output.write(buffer, 0, count);
      }
      JSONObject rootJson = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
      if (rootJson.getInt("schemaVersion") != MANIFEST_VERSION) {
        throw new IOException("Legacy workout manifest version is unsupported");
      }
      JSONArray records = rootJson.getJSONArray("records");
      Set<String> identities = new HashSet<>();
      for (int index = 0; index < records.length(); index++) {
        Entry entry = Entry.parse(records.getJSONObject(index));
        if (!identities.add(entry.identity.key())) {
          throw new IOException("Legacy workout manifest has duplicate identities");
        }
        if (!entry.fileName.equals(destinationName(entry.identity))) {
          throw new IOException("Legacy workout manifest contains an invalid file name");
        }
        entries.add(entry);
      }
    } catch (JSONException e) {
      throw new IOException("Legacy workout manifest is malformed", e);
    }
    return entries;
  }

  private void writeEntries(List<Entry> entries) throws IOException {
    ensureDirectory(root);
    JSONArray records = new JSONArray();
    try {
      for (Entry entry : entries) records.put(entry.toJson());
      JSONObject json =
          new JSONObject().put("schemaVersion", MANIFEST_VERSION).put("records", records);
      manifestWriter.write(manifest, json.toString().getBytes(StandardCharsets.UTF_8));
    } catch (JSONException e) {
      throw new IOException("Unable to serialize legacy workout manifest", e);
    }
  }

  private static void writeAtomicManifest(File file, byte[] contents) throws IOException {
    AtomicFile atomicFile = new AtomicFile(file);
    FileOutputStream output = null;
    try {
      output = atomicFile.startWrite();
      output.write(contents);
      output.getFD().sync();
      atomicFile.finishWrite(output);
    } catch (IOException e) {
      if (output != null) atomicFile.failWrite(output);
      throw e;
    }
  }

  private void deleteStaged(Entry entry) {
    File stagedFile = new File(stagingDirectory, entry.stagedName);
    if (stagedFile.exists()) stagedFile.delete();
  }

  private static Entry find(List<Entry> entries, String identityKey) {
    for (Entry entry : entries) if (entry.identity.key().equals(identityKey)) return entry;
    return null;
  }

  private static void ensureDirectory(File directory) throws IOException {
    if (!directory.exists() && !directory.mkdirs()) {
      throw new IOException("Unable to create legacy workout migration directory");
    }
    if (!directory.isDirectory()) {
      throw new IOException("Legacy workout migration path is not a directory");
    }
  }

  private static String sha256(File file) throws IOException {
    try (FileInputStream input = new FileInputStream(file)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
      return hex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static String sha256(byte[] content) {
    try {
      return hex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder value = new StringBuilder(bytes.length * 2);
    for (byte item : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
    return value.toString();
  }

  private static String identityHash(String key) {
    return sha256(key.getBytes(StandardCharsets.UTF_8));
  }

  public static final class SourceIdentity {
    public final String serverOrigin;
    public final String ownerUserId;
    public final String workoutId;
    public final int revision;
    public final String sourceName;

    public SourceIdentity(
        String serverOrigin,
        String ownerUserId,
        String workoutId,
        int revision,
        String sourceName) {
      if (isBlank(serverOrigin) || isBlank(ownerUserId) || isBlank(workoutId) || revision < 0) {
        throw new IllegalArgumentException("Legacy workout identity is incomplete");
      }
      this.serverOrigin = serverOrigin;
      this.ownerUserId = ownerUserId;
      this.workoutId = workoutId;
      this.revision = revision;
      this.sourceName = sourceName == null ? "" : sourceName;
    }

    private String key() {
      return component(serverOrigin) + component(ownerUserId) + component(workoutId) + revision;
    }

    private JSONObject toJson() throws JSONException {
      return new JSONObject()
          .put("serverOrigin", serverOrigin)
          .put("ownerUserId", ownerUserId)
          .put("workoutId", workoutId)
          .put("revision", revision);
    }

    private static SourceIdentity fromJson(JSONObject json, String sourceName)
        throws JSONException {
      return new SourceIdentity(
          json.getString("serverOrigin"),
          json.getString("ownerUserId"),
          json.getString("workoutId"),
          json.getInt("revision"),
          sourceName);
    }
  }

  public final class StagedWorkout {
    private final String identityKey;
    private final String fileName;
    private final String stagedName;
    private final String sha256;
    private final boolean alreadyImported;
    private final boolean conflict;

    private StagedWorkout(
        String identityKey,
        String fileName,
        String stagedName,
        String sha256,
        boolean alreadyImported,
        boolean conflict) {
      this.identityKey = identityKey;
      this.fileName = fileName;
      this.stagedName = stagedName;
      this.sha256 = sha256;
      this.alreadyImported = alreadyImported;
      this.conflict = conflict;
    }

    public File getStagedFile() {
      return stagedName == null ? null : new File(stagingDirectory, stagedName);
    }

    public File getDestinationFile() {
      return WorkoutSerializer.getFile(context, fileName);
    }
  }

  public static final class StoreResult {
    private int imported;
    private int alreadyImported;
    private int conflicting;
    private int failed;

    public int getImportedCount() {
      return imported;
    }

    public int getAlreadyImportedCount() {
      return alreadyImported;
    }

    public int getConflictingCount() {
      return conflicting;
    }

    public int getFailedCount() {
      return failed;
    }
  }

  private static final class Entry {
    final SourceIdentity identity;
    final String fileName;
    final String stagedName;
    final String sha256;
    String status;

    Entry(
        SourceIdentity identity, String fileName, String stagedName, String sha256, String status) {
      this.identity = identity;
      this.fileName = fileName;
      this.stagedName = stagedName;
      this.sha256 = sha256;
      this.status = status;
    }

    JSONObject toJson() throws JSONException {
      return new JSONObject()
          .put("identity", identity.toJson())
          .put("displayName", identity.sourceName)
          .put("fileName", fileName)
          .put("stagedName", stagedName)
          .put("sha256", sha256)
          .put("status", status);
    }

    static Entry parse(JSONObject json) throws JSONException, IOException {
      String displayName = json.optString("displayName", "");
      SourceIdentity identity =
          SourceIdentity.fromJson(json.getJSONObject("identity"), displayName);
      String fileName = json.getString("fileName");
      String stagedName = json.getString("stagedName");
      String hash = json.getString("sha256");
      String status = json.getString("status");
      if (stagedName.contains("/")
          || stagedName.contains("\\")
          || !hash.matches("[a-f0-9]{64}")
          || !("prepared".equals(status) || "imported".equals(status))) {
        throw new IOException("Legacy workout manifest entry is invalid");
      }
      return new Entry(identity, fileName, stagedName, hash, status);
    }
  }

  private static String component(String value) {
    return value.length() + ":" + value;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
