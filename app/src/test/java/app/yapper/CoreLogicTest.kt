package app.yapper

import app.yapper.data.ChannelNames
import app.yapper.data.PasswordHasher
import app.yapper.data.SearchQuery
import app.yapper.data.Usernames
import app.yapper.data.friendshipPairKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {
    @Test
    fun passwordRoundTrip() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("yapper", salt)
        assertTrue(PasswordHasher.verify("yapper", salt, hash))
        assertFalse(PasswordHasher.verify("nope", salt, hash))
        assertFalse(PasswordHasher.verify("yapper", salt, "abcd"))
    }

    @Test
    fun differentSaltsDoNotMatch() {
        val first = PasswordHasher.newSalt()
        val second = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("yapper", first)
        assertFalse(PasswordHasher.verify("yapper", second, hash))
    }

    @Test
    fun usernames() {
        assertNull(Usernames.validate("Nova"))
        assertEquals("nova", Usernames.normalize(" Nova "))
        assertTrue(Usernames.validate("a") != null)
        assertTrue(Usernames.validate("has space") != null)
    }

    @Test
    fun channelSlugs() {
        assertEquals("late-night", ChannelNames.slug(" Late   Night! "))
        assertEquals("", ChannelNames.slug("!!!"))
    }

    @Test
    fun friendPairKeyIgnoresOrder() {
        assertEquals(friendshipPairKey(4, 9), friendshipPairKey(9, 4))
        assertEquals("4:9", friendshipPairKey(9, 4))
    }

    @Test
    fun searchStripsLikeWildcards() {
        assertEquals("%comma%", SearchQuery.toLikePattern("  comma%_ "))
    }
}
