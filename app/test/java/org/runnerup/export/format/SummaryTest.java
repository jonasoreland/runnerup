/*
 * Copyright (C) 2021 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.export.format;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.runnerup.util.Formatter;

import java.io.File;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
public class SummaryTest {
    SQLiteDatabase db;
    Formatter formatter;
    Summary sum;

    @Before
    public void setup() {
        File f = new File(getClass().getResource("/runnerup.db").getFile());
        db = SQLiteDatabase.openDatabase(f.getPath(), null, 0);
        formatter = new Formatter(ApplicationProvider.getApplicationContext());
        sum = new Summary(db, formatter);
    }

    @After
    public void teardown() {
        db.close();
    }

    @Test
    public void TestSummary() throws IOException {
        String str = sum.Summary(5);
        assertEquals("RUNNING: 2.29mi at Sun, 8 Aug 2021 05:02 PM EDT for 25m8s. Pace: 11:00/mi", str);
        str = sum.Summary(6);
        assertEquals("RUNNING: 2.33mi at Tue, 10 Aug 2021 01:43 PM EDT for 25m51s. Pace: 11:07/mi", str);
    }
}
