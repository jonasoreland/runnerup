package org.runnerup.workout;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParser;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class RelvenPlanKitTest {
  private static final String ASSET = "relven/relven-ai-plan-kit-v2.zip";

  @Test
  public void extractsTheExactGenerationInstructionsFromTheBundledKit() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    String expected = readEntry(context.getAssets().open(ASSET), "INSTRUCTIONS.md");

    String copied = RelvenPlanKit.readInstructions(context);
    assertTrue(copied.startsWith("===== FILE: INSTRUCTIONS.md =====\n"));
    assertTrue(copied.contains(expected));
    assertTrue(expected.contains("schemaVersion: 1"));
    assertTrue(copied.contains("JSON-FORMAT.md"));
    assertTrue(copied.contains("catalog/exercise-library-v1.json"));
    assertTrue(copied.contains("user-data/custom-exercise-collection-v1.json"));
    assertTrue(RelvenPlanKit.ASSET_PATH.endsWith("relven-ai-plan-kit-v2.zip"));
  }

  @Test
  public void createsCompleteKitZipWithoutClosingDestination() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    ByteArrayOutputStream destination = new ByteArrayOutputStream();

    RelvenPlanKit.copyArchive(context, destination);

    try (ZipInputStream archive =
        new ZipInputStream(
            new java.io.ByteArrayInputStream(destination.toByteArray()), StandardCharsets.UTF_8)) {
      assertNotNull(findEntry(archive, "catalog/exercise-library-v1.json"));
      assertNotNull(findEntry(archive, "user-data/custom-exercise-collection-v1.json"));
      assertNotNull(findEntry(archive, "user-data/custom-exercise-inventory.md"));
    }
    destination.write(0);
  }

  @Test
  public void bundledKitIncludesSchemasAndCapabilityMetadata() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    try (ZipInputStream archive =
        new ZipInputStream(context.getAssets().open(ASSET), StandardCharsets.UTF_8)) {
      java.util.Set<String> names = new java.util.HashSet<>();
      ZipEntry entry;
      while ((entry = archive.getNextEntry()) != null) names.add(entry.getName());
      assertTrue(names.contains("relven-ai-plan-kit/capabilities.json"));
      assertTrue(names.contains("relven-ai-plan-kit/schemas/plan-v1.schema.json"));
      assertTrue(names.contains("relven-ai-plan-kit/schemas/plan-v2.schema.json"));
      assertTrue(names.contains("relven-ai-plan-kit/schemas/workout-v3.schema.json"));
      assertTrue(names.contains("relven-ai-plan-kit/schemas/exercise-library-v1.schema.json"));
      assertTrue(names.contains("relven-ai-plan-kit/schemas/exercise-collection-v1.schema.json"));
      assertTrue(names.contains("relven-ai-plan-kit/JSON-FORMAT.md"));
      assertTrue(names.contains("relven-ai-plan-kit/catalog/exercise-library-v1.json"));
      assertTrue(names.contains("relven-ai-plan-kit/catalog/exercise-inventory.md"));
    }
  }

  @Test
  public void cachedShareFileIsACompleteCopyOfTheBundledArchive() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    File shareFile = RelvenPlanKit.createShareFile(context);

    ByteArrayOutputStream expected = new ByteArrayOutputStream();
    RelvenPlanKit.copyArchive(context, expected);
    assertArrayEquals(expected.toByteArray(), java.nio.file.Files.readAllBytes(shareFile.toPath()));
    assertTrue(shareFile.getCanonicalPath().contains("relven-ai-plan-kit"));
  }

  @Test
  public void copiedBundledEntriesKeepTheirStableTimestamps() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    ByteArrayOutputStream copied = new ByteArrayOutputStream();
    RelvenPlanKit.copyArchive(context, copied);

    try (ZipInputStream original =
            new ZipInputStream(context.getAssets().open(ASSET), StandardCharsets.UTF_8);
        ZipInputStream result =
            new ZipInputStream(
                new java.io.ByteArrayInputStream(copied.toByteArray()), StandardCharsets.UTF_8)) {
      ZipEntry originalEntry;
      while ((originalEntry = original.getNextEntry()) != null) {
        ZipEntry copiedEntry = result.getNextEntry();
        assertNotNull(copiedEntry);
        assertEquals(originalEntry.getName(), copiedEntry.getName());
        assertEquals(originalEntry.getTime(), copiedEntry.getTime());
        original.transferTo(java.io.OutputStream.nullOutputStream());
        result.transferTo(java.io.OutputStream.nullOutputStream());
      }
    }
  }

  @Test
  public void shareProviderIsNonExportedAndLimitedToTheKitCacheDirectory() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    String authority = context.getPackageName() + ".relven.ai.plan.kit.provider";
    ProviderInfo provider =
        context.getPackageManager().resolveContentProvider(authority, PackageManager.GET_META_DATA);
    assertNotNull(provider);
    assertFalse(provider.exported);
    assertTrue(provider.grantUriPermissions);
    assertNotNull(provider.metaData);
    int resourceId = provider.metaData.getInt("android.support.FILE_PROVIDER_PATHS");
    assertEquals(org.runnerup.R.xml.relven_plan_kit_file_paths, resourceId);

    int configuredRoots = 0;
    try (android.content.res.XmlResourceParser paths = context.getResources().getXml(resourceId)) {
      int event;
      while ((event = paths.next()) != XmlPullParser.END_DOCUMENT) {
        if (event != XmlPullParser.START_TAG || paths.getDepth() != 2) continue;
        configuredRoots++;
        assertEquals("cache-path", paths.getName());
        assertEquals("relven-ai-plan-kit/", paths.getAttributeValue(null, "path"));
      }
    }
    Assert.assertEquals(1, configuredRoots);
  }

  private static String readEntry(InputStream input, String suffix) throws Exception {
    try (ZipInputStream archive = new ZipInputStream(input, StandardCharsets.UTF_8)) {
      String entry = findEntry(archive, suffix);
      assertNotNull(entry);
      return entry;
    }
  }

  private static String findEntry(ZipInputStream archive, String suffix) throws Exception {
    ZipEntry entry;
    while ((entry = archive.getNextEntry()) != null) {
      if (entry.getName().endsWith("/" + suffix)) {
        return new String(archive.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
    return null;
  }
}
