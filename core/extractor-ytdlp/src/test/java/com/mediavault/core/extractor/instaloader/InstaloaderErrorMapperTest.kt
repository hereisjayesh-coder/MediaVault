package com.mediavault.core.extractor.instaloader

import com.chaquo.python.PyException
import com.mediavault.core.common.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstaloaderErrorMapperTest {

    @Test
    fun `login-required message maps to a clear Unsupported reason, never a raw Python exception`() {
        val error = PyException(
            "instaloader.exceptions.LoginRequiredException: Login required to access this profile.",
        ).toAppError()

        assertTrue(error is AppError.Unsupported)
        assertEquals(
            "This account is private or requires login — MediaVault only downloads from public posts.",
            error.message,
        )
    }

    @Test
    fun `private-profile message maps to the same clear Unsupported reason`() {
        val error = PyException(
            "instaloader.exceptions.PrivateProfileNotFollowedException: Profile someone is private.",
        ).toAppError()

        assertTrue(error is AppError.Unsupported)
        assertEquals(
            "This account is private or requires login — MediaVault only downloads from public posts.",
            error.message,
        )
    }

    @Test
    fun `not-found message maps to a clear Unsupported reason distinguishing it from a login wall`() {
        val error = PyException(
            "instaloader.exceptions.QueryReturnedNotFoundException: JSON Query to graphql/query: returned 404.",
        ).toAppError()

        assertTrue(error is AppError.Unsupported)
        assertEquals("This post couldn't be found. It may have been deleted or the link is incorrect.", error.message)
    }

    @Test
    fun `an unrecognized URL shape maps to the same generic unsupported wording yt-dlp uses`() {
        val error = PyException("ValueError: Not a recognized Instagram post URL.").toAppError()

        assertTrue(error is AppError.Unsupported)
        assertEquals("This link isn't from a source MediaVault's extractor recognizes yet.", error.message)
    }

    @Test
    fun `a connection failure maps to Network, never Unknown`() {
        val error = PyException(
            "instaloader.exceptions.ConnectionException: Failed to establish a new connection.",
        ).toAppError()

        assertTrue(error is AppError.Network)
    }

    @Test
    fun `a timed-out request maps to Timeout, not the generic Network error`() {
        val error = PyException("requests.exceptions.ConnectTimeout: timed out").toAppError()

        assertTrue(error is AppError.Timeout)
    }

    @Test
    fun `a rate-limit response maps to a Source error explaining the temporary block`() {
        val error = PyException("instaloader.exceptions.TooManyRequestsException: 429 Too Many Requests").toAppError()

        assertTrue(error is AppError.Source)
    }

    @Test
    fun `an unrecognized exception falls back to a cleaned Unknown error, never a raw traceback`() {
        val error = PyException("SomeBrandNewException: something entirely new broke").toAppError()

        assertTrue(error is AppError.Unknown)
        assertEquals("something entirely new broke", error.message)
    }
}
