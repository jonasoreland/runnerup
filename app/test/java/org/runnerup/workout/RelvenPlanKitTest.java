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
  private static final String ASSET = "relven/relven-ai-plan-kit-v1.zip";

  @Test
  public void extractsTheExactGenerationInstructionsFromTheBundledKit() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    String expected = readEntry(context.getAssets().open(ASSET), "INSTRUCTIONS.md");

    assertEquals(expected, RelvenPlanKit.readInstructions(context));
    assertTrue(expected.contains("schemaVersion: 1"));
    assertTrue(expected.contains("Do not create"));
  }

  @Test
  public void copiesTheCompleteBundledZipWithoutClosingDestination() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    byte[] expected;
    try (InputStream input = context.getAssets().open(ASSET)) {
      expected = input.readAllBytes();
    }
    ByteArrayOutputStream destination = new ByteArrayOutputStream();

    RelvenPlanKit.copyArchive(context, destination);

    assertArrayEquals(expected, destination.toByteArray());
    destination.write(0);
  }

  @Test
  public void bundledKitIncludesSchemasAndCapabilityMetadata() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    try (ZipInputStream archive =
        new ZipInputStream(context.getAssets().open(ASSET), StandardCharsets.UTF_8)) {
      assertNotNull(findEntry(archive, "capabilities.json"));
      assertNotNull(findEntry(archive, "schemas/plan-v1.schema.json"));
      assertNotNull(findEntry(archive, "schemas/plan-v2.schema.json"));
      assertNotNull(findEntry(archive, "schemas/workout-v3.schema.json"));
    }
  }

  @Test
  public void cachedShareFileIsACompleteCopyOfTheBundledArchive() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    byte[] expected;
    try (InputStream input = context.getAssets().open(ASSET)) {
      expected = input.readAllBytes();
    }

    File shareFile = RelvenPlanKit.createShareFile(context);

    assertArrayEquals(expected, java.nio.file.Files.readAllBytes(shareFile.toPath()));
    assertTrue(shareFile.getCanonicalPath().contains("relven-ai-plan-kit"));
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
