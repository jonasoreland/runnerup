package org.runnerup;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResourceTest {
    @Test
    public void TestResourceAccess() {
        assertTrue(getClass().getResource("runnerup.db") == null);
        assertTrue(getClass().getResource("/runnerup.db").getPath().endsWith("runnerup.db"));
        assertTrue(getClass().getClassLoader().getResource("runnerup.db").getPath().endsWith("runnerup.db"));
    }
}
