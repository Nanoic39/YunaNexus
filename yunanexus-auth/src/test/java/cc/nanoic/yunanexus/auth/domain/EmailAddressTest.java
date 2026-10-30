package cc.nanoic.yunanexus.auth.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailAddressTest {
    @Test
    void normalizesOrdinaryAddressesAndAcceptsExistingStorageBoundary() {
        assertEquals("first.last+tag@example.invalid", EmailAddress.normalize(" FIRST.Last+tag@EXAMPLE.INVALID "));
        String boundary = "a".repeat(64) + "@" + "b".repeat(61) + ".x";
        assertEquals(128, boundary.length());
        assertEquals(boundary, EmailAddress.normalize("  " + boundary + "  "));
        assertNull(EmailAddress.normalize("a".repeat(64) + "@" + "b".repeat(62) + ".x"));
    }

    @Test
    void rejectsMalformedAddressAndHeaderInjection() {
        for (String invalid : new String[]{null, "", "a..b@example.invalid", ".a@example.invalid",
                "a.@example.invalid", "a@-example.invalid", "a@example-.invalid", "a@localhost",
                "a@b..invalid", "Display Name <a@example.invalid>", "a\r\nBcc:b@example.invalid",
                "a".repeat(65) + "@example.invalid"}) {
            assertNull(EmailAddress.normalize(invalid), invalid);
        }
    }
}
