package io.github.hddq.restoid.util

import io.github.hddq.restoid.util.SftpRepositorySpec.Error
import io.github.hddq.restoid.util.SftpRepositorySpec.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class SftpRepositorySpecTest {

    private fun spec(host: String, port: String = "", user: String = "", path: String = ""): Result =
        SftpRepositorySpec.build(host = host, port = port, user = user, path = path)

    private fun ok(value: String) = Result.Success(value)
    private fun fail(error: Error) = Result.Failure(error)

    @Test
    fun defaultPort_usesScpStyle() {
        assertEquals(ok("sftp:me@nas:/srv/repo"), spec("nas", user = "me", path = "/srv/repo"))
        assertEquals(ok("sftp:me@nas:/srv/repo"), spec("nas", port = "22", user = "me", path = "/srv/repo"))
        assertEquals(ok("sftp:me@nas:repo"), spec("nas", user = "me", path = "repo"))
    }

    @Test
    fun customPort_usesUrlStyle() {
        assertEquals(ok("sftp://me@nas:2222//srv/repo"), spec("nas", port = "2222", user = "me", path = "/srv/repo"))
        assertEquals(ok("sftp://me@nas:2222/repo"), spec("nas", port = "2222", user = "me", path = "repo"))
    }

    @Test
    fun tildePath_isRelativeToHome() {
        assertEquals(ok("sftp:me@nas:backups/repo"), spec("nas", user = "me", path = "~/backups/repo"))
        assertEquals(ok("sftp://me@nas:2222/repo"), spec("nas", port = "2222", user = "me", path = "~/repo"))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals(ok("sftp://me@nas:2222//srv/repo"), spec("  nas ", port = " 2222 ", user = " me ", path = " /srv/repo "))
    }

    @Test
    fun hostField_toleratesPastedSchemeUserAndPort() {
        assertEquals(ok("sftp://me@nas:2222//srv/repo"), spec("sftp://me@nas:2222", path = "/srv/repo"))
        assertEquals(ok("sftp://me@nas:2222//srv/repo"), spec("me@nas:2222", path = "/srv/repo"))
        assertEquals(ok("sftp:me@nas:/srv/repo"), spec("sftp:me@nas", path = "/srv/repo"))
        // Explicit fields win over embedded values.
        assertEquals(ok("sftp://you@nas:3333//srv/repo"), spec("me@nas:2222", port = "3333", user = "you", path = "/srv/repo"))
    }

    @Test
    fun hostField_toleratesFullPastedSpec() {
        assertEquals(ok("sftp://me@nas:2222//srv/repo"), spec("sftp://me@nas:2222//srv/repo"))
        assertEquals(ok("sftp://me@nas:2222/repo"), spec("sftp://me@nas:2222/repo"))
        assertEquals(ok("sftp:me@nas:/srv/repo"), spec("sftp:me@nas:/srv/repo"))
        assertEquals(ok("sftp:me@nas:repo"), spec("me@nas:repo"))
    }

    @Test
    fun ipv6_isBracketedAndUsesUrlStyle() {
        assertEquals(ok("sftp://me@[fd00::1]//srv/repo"), spec("fd00::1", user = "me", path = "/srv/repo"))
        assertEquals(ok("sftp://me@[fd00::1]:2222//srv/repo"), spec("[fd00::1]:2222", user = "me", path = "/srv/repo"))
    }

    @Test
    fun urlStyle_percentEncodesSpecialCharacters() {
        assertEquals(
            ok("sftp://me%40corp@nas:2222//srv/my%20repo%231"),
            spec("nas", port = "2222", user = "me@corp", path = "/srv/my repo#1")
        )
    }

    @Test
    fun validationErrors() {
        assertEquals(fail(Error.MISSING_HOST), spec("", user = "me", path = "/srv/repo"))
        assertEquals(fail(Error.MISSING_USER), spec("nas", path = "/srv/repo"))
        assertEquals(fail(Error.MISSING_PATH), spec("nas", user = "me"))
        assertEquals(fail(Error.MISSING_PATH), spec("nas", user = "me", path = "~"))
        assertEquals(fail(Error.INVALID_PORT), spec("nas", port = "abc", user = "me", path = "/srv/repo"))
        assertEquals(fail(Error.INVALID_PORT), spec("nas", port = "70000", user = "me", path = "/srv/repo"))
        assertEquals(fail(Error.INVALID_PORT), spec("nas", port = "0", user = "me", path = "/srv/repo"))
    }
}
