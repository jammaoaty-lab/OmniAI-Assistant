package com.omniai.assistant;

import android.util.Base64;

import com.omniai.assistant.user.UserManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UserManagerTokenTest {

    private UserManager userManager;

    @Before
    public void setUp() throws Exception {
        resetSingleton();
        UserManager.init(RuntimeEnvironment.getApplication());
        userManager = UserManager.getInstance();
    }

    @After
    public void tearDown() throws Exception {
        resetSingleton();
    }

    private void resetSingleton() throws Exception {
        Field instanceField = UserManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    public void testIsTokenExpired() {
        userManager.saveToken("access_token", "refresh_token", 1);
        assertTrue("Token with past expiry should be expired", userManager.isTokenExpired());
    }

    @Test
    public void testIsTokenNotExpired() {
        long futureExpiry = System.currentTimeMillis() / 1000 + 3600;
        userManager.saveToken("access_token", "refresh_token", futureExpiry);
        assertFalse("Token with future expiry should not be expired", userManager.isTokenExpired());
    }

    @Test
    public void testIsLoggedIn() throws Exception {
        userManager.clearToken();
        Field currentUserField = UserManager.class.getDeclaredField("currentUser");
        currentUserField.setAccessible(true);
        currentUserField.set(userManager, null);
        assertFalse("Should not be logged in with null token and null user", userManager.isLoggedIn());
    }

    @Test
    public void testParseJwtExpiry() throws Exception {
        String payload = "{\"exp\":9999999999}";
        String base64Payload = Base64.encodeToString(payload.getBytes("UTF-8"),
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        String jwt = "header." + base64Payload + ".signature";

        Method parseMethod = UserManager.class.getDeclaredMethod("parseJwtExpiry", String.class);
        parseMethod.setAccessible(true);
        long expiry = (long) parseMethod.invoke(userManager, jwt);

        assertEquals("Parsed expiry should match JWT exp claim", 9999999999L, expiry);
    }
}
