package com.omniai.assistant;

import com.omniai.assistant.credits.CreditsManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CreditsManagerTest {

    private CreditsManager manager;

    @Before
    public void setUp() throws Exception {
        resetSingleton();
        manager = CreditsManager.getInstance();
    }

    @After
    public void tearDown() throws Exception {
        resetSingleton();
    }

    private void resetSingleton() throws Exception {
        Field instanceField = CreditsManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    public void testAddCredits() {
        assertEquals(0, manager.getCredits());
        manager.addCredits(100, "REWARD", "Test reward");
        assertEquals(100, manager.getCredits());
        manager.addCredits(50, "REWARD", "Another reward");
        assertEquals(150, manager.getCredits());
    }

    @Test
    public void testDeductCredits() {
        manager.addCredits(100, "REWARD", "Test reward");
        boolean result = manager.deductCredits(30, "CONSUME", "Test consume");
        assertTrue("Deducting valid amount should succeed", result);
        assertEquals(70, manager.getCredits());
    }

    @Test
    public void testDeductInsufficientCredits() {
        manager.addCredits(50, "REWARD", "Test reward");
        boolean result = manager.deductCredits(100, "CONSUME", "Test consume");
        assertFalse("Deducting more than balance should return false", result);
        assertEquals("Balance should remain unchanged", 50, manager.getCredits());
    }

    @Test
    public void testHasSufficientCredits() {
        manager.addCredits(100, "REWARD", "Test reward");
        assertTrue("Should have sufficient credits for 50", manager.hasSufficientCredits(50));
        assertTrue("Should have sufficient credits for 100", manager.hasSufficientCredits(100));
        assertFalse("Should not have sufficient credits for 150", manager.hasSufficientCredits(150));
        assertFalse("Should not have sufficient credits for 0 when balance is 0", CreditsManager.getInstance().hasSufficientCredits(1));
    }

    @Test
    public void testCheckAndDeduct() {
        manager.addCredits(100, "REWARD", "Test reward");
        boolean result = manager.checkAndDeduct(CreditsManager.CreditsFeature.ADVANCED_TEXT_MODEL);
        assertTrue("Check and deduct should succeed with sufficient credits", result);
        assertEquals(90, manager.getCredits());

        result = manager.checkAndDeduct(CreditsManager.CreditsFeature.ADVANCED_VISION_MODEL);
        assertTrue("Check and deduct for vision model should succeed", result);
        assertEquals(70, manager.getCredits());
    }

    @Test
    public void testGetRechargePlans() {
        assertEquals("Should return 4 recharge plans", 4, manager.getRechargePlans().size());
    }
}
