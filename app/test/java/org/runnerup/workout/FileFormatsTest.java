package org.runnerup.workout;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FileFormatsTest {

    @Test
    public void nullConstructor() {
        FileFormats formats = new FileFormats();
        assertNotNull(formats.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullContains() {
        FileFormats formats = new FileFormats();
        formats.contains(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullAdd() {
        FileFormats formats = new FileFormats();
        formats.add(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullRemove() {
        FileFormats formats = new FileFormats();
        formats.remove(null);
    }

    @Test
    public void defaultNotEmpty() {
        FileFormats formats = FileFormats.DEFAULT_FORMATS;
        boolean notEmpty = false;
        for (FileFormats.Format f: FileFormats.ALL_FORMATS) {
            notEmpty = notEmpty || formats.contains(f);
        }
        assertTrue(notEmpty);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void defaultNoRemove() {
        FileFormats formats = FileFormats.DEFAULT_FORMATS;
        formats.remove(FileFormats.TCX);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void defaultNoAdd() {
        FileFormats formats = FileFormats.DEFAULT_FORMATS;
        formats.add(FileFormats.GPX);
    }


    @Test
    public void addOnce() {
        FileFormats formats;
        for (FileFormats.Format f: FileFormats.ALL_FORMATS) {
            formats = new FileFormats();
            formats.add(f);
            assertEquals(formats.toString(), f.getValue());
        }
    }

    @Test
    public void addAll() {
        FileFormats formats = new FileFormats();
        for (FileFormats.Format f: FileFormats.ALL_FORMATS) {
            assertFalse(formats.contains(f));
        }
        for (FileFormats.Format f: FileFormats.ALL_FORMATS) {
            formats.add(f);
        }
        for (FileFormats.Format f: FileFormats.ALL_FORMATS) {
            assertTrue(formats.contains(f));
        }
    }

    @Test
    public void addTwice() {
        FileFormats formats = new FileFormats();
        for (FileFormats.Format f: FileFormats.ALL_FORMATS) {
            assertTrue(formats.add(f));
        }
        String v1 = formats.toString();
        for (FileFormats.Format f: FileFormats.ALL_FORMATS) {
            assertFalse(formats.add(f));
        }
        String v2 = formats.toString();
        assertEquals(v1, v2);
    }

    @Test
    public void removeOnce() {
        FileFormats formats = new FileFormats();
        String v1 = formats.toString();
        for (FileFormats.Format f: FileFormats.ALL_FORMATS) {
            assertTrue(formats.add(f));
        }
        for (FileFormats.Format f: FileFormats.ALL_FORMATS) {
            assertTrue(formats.remove(f));
        }
        String v2 = formats.toString();
        assertEquals(v1, v2);
    }

    @Test
    public void removeTwice() {
        FileFormats formats = new FileFormats();
        String v1 = formats.toString();
        for (FileFormats.Format f: FileFormats.ALL_FORMATS) {
            assertTrue(formats.add(f));
        }
        for (FileFormats.Format f: FileFormats.ALL_FORMATS) {
            assertTrue(formats.remove(f));
            assertFalse(formats.remove(f));
        }
        String v2 = formats.toString();
        assertEquals(v1, v2);
    }

    @Test
    public void legacyCompat() {
        FileFormats formats;
        // one format
        formats = new FileFormats("gpx,");
        assertTrue(formats.contains(FileFormats.GPX));
        assertTrue(formats.add(FileFormats.TCX));
        assertTrue(formats.remove(FileFormats.GPX));
        assertFalse(formats.contains(FileFormats.GPX));
        assertTrue(formats.contains(FileFormats.TCX));

        // both formats
        formats = new FileFormats("gpx,tcx,");
        String v1 = formats.toString();
        assertTrue(formats.contains(FileFormats.TCX));
        assertFalse(formats.add(FileFormats.TCX));
        String v2 = formats.toString();
        assertEquals(v1, v2);
        assertTrue(formats.remove(FileFormats.TCX));
        assertFalse(formats.contains(FileFormats.TCX));
        assertEquals(formats.toString(), FileFormats.GPX.getValue());
    }

    @Test
    public void futureProof() {
        // playing with longer list
        FileFormats formats;
        formats = new FileFormats("gpx,a,b,c,d,");
        assertTrue(formats.contains(FileFormats.GPX));
        assertTrue(formats.add(FileFormats.TCX));
        assertTrue(formats.remove(FileFormats.GPX));
        assertFalse(formats.contains(FileFormats.GPX));
        assertTrue(formats.contains(FileFormats.TCX));
        assertTrue(formats.add(FileFormats.GPX));
        assertTrue(formats.contains(FileFormats.GPX));
        assertTrue(formats.remove(FileFormats.TCX));
        assertFalse(formats.contains(FileFormats.TCX));
        assertTrue(formats.remove(FileFormats.GPX));
        assertFalse(formats.contains(FileFormats.GPX));
        assertEquals(formats.toString(), ("a,b,c,d"));
    }
}
