package io.github.hddq.restoid.util

/**
 * Builds a restic SFTP repository specification from individual connection fields
 * (host, port, username, path), so users never have to know restic's URL syntax.
 *
 * Input is normalized leniently: a pasted `sftp://` / `sftp:` prefix is stripped,
 * `user@host` and `host:port` typed into the host field are split out, bare IPv6
 * addresses are bracketed, and a leading `~/` in the path is treated as relative
 * to the remote user's home directory.
 *
 * Output format:
 * - Default port (blank or 22) with a regular host: `sftp:user@host:/abs/path` or `sftp:user@host:rel/path`
 * - Custom port or IPv6 host: `sftp://user@host:port//abs/path` or `sftp://user@host:port/rel/path`
 */
object SftpRepositorySpec {

    enum class Error { MISSING_HOST, MISSING_USER, MISSING_PATH, INVALID_PORT }

    sealed interface Result {
        data class Success(val spec: String) : Result
        data class Failure(val error: Error) : Result
    }

    private const val DEFAULT_PORT = 22

    fun build(host: String, port: String, user: String, path: String): Result {
        var normalizedUser = user.trim().removeSuffix("@")
        var normalizedPort = port.trim()
        var normalizedHost = host.trim()
        var normalizedPath = path.trim()

        // Strip any pasted scheme prefix.
        normalizedHost = normalizedHost
            .removePrefixIgnoreCase("sftp://")
            .removePrefixIgnoreCase("ssh://")
            .removePrefixIgnoreCase("sftp:")

        // A pasted path after the host: "host:/srv/repo" (scp-style, path is verbatim),
        // or "host:2222//srv/repo" / "host/repo" (URL-style, first slash is a separator).
        val slashIndex = normalizedHost.indexOf('/')
        if (slashIndex >= 0) {
            val trailing = normalizedHost.substring(slashIndex)
            var authority = normalizedHost.substring(0, slashIndex)
            val trailingPath = if (authority.endsWith(":") && !authority.endsWith("]:")) {
                authority = authority.removeSuffix(":")
                trailing
            } else {
                trailing.removePrefix("/")
            }
            normalizedHost = authority
            if (normalizedPath.isEmpty()) normalizedPath = trailingPath
        }

        // "user@host" typed into the host field.
        val atIndex = normalizedHost.lastIndexOf('@')
        if (atIndex >= 0) {
            val embeddedUser = normalizedHost.substring(0, atIndex)
            normalizedHost = normalizedHost.substring(atIndex + 1)
            if (normalizedUser.isEmpty()) normalizedUser = embeddedUser
        }

        // "host:port" / "[v6]:port" typed into the host field, or a bare IPv6 address.
        if (normalizedHost.startsWith("[")) {
            val closing = normalizedHost.indexOf(']')
            if (closing > 0) {
                val afterBracket = normalizedHost.substring(closing + 1)
                normalizedHost = normalizedHost.substring(0, closing + 1)
                val embeddedPort = afterBracket.removePrefix(":")
                if (embeddedPort.isNotEmpty() && normalizedPort.isEmpty()) normalizedPort = embeddedPort
            }
        } else {
            val colonCount = normalizedHost.count { it == ':' }
            if (colonCount == 1) {
                val afterColon = normalizedHost.substringAfter(':')
                normalizedHost = normalizedHost.substringBefore(':')
                if (afterColon.isNotEmpty()) {
                    // scp-style "host:relative/repo" vs "host:2222"
                    if (afterColon.all(Char::isDigit)) {
                        if (normalizedPort.isEmpty()) normalizedPort = afterColon
                    } else if (normalizedPath.isEmpty()) {
                        normalizedPath = afterColon
                    }
                }
            } else if (colonCount > 1) {
                normalizedHost = "[$normalizedHost]"
            }
        }

        if (normalizedHost.isEmpty() || normalizedHost == "[]") return Result.Failure(Error.MISSING_HOST)
        if (normalizedUser.isEmpty()) return Result.Failure(Error.MISSING_USER)

        val portNumber = if (normalizedPort.isEmpty()) {
            null
        } else {
            normalizedPort.toIntOrNull()?.takeIf { normalizedPort.all(Char::isDigit) && it in 1..65535 }
                ?: return Result.Failure(Error.INVALID_PORT)
        }

        // "~/repo" and "~" mean relative to the remote home directory.
        normalizedPath = when {
            normalizedPath == "~" -> ""
            normalizedPath.startsWith("~/") -> normalizedPath.removePrefix("~/").trimStart('/')
            else -> normalizedPath
        }
        if (normalizedPath.isEmpty() || normalizedPath == "/") return Result.Failure(Error.MISSING_PATH)

        val isIpv6 = normalizedHost.startsWith("[")
        val useUrlForm = isIpv6 || (portNumber != null && portNumber != DEFAULT_PORT)

        val spec = if (useUrlForm) {
            val portPart = portNumber?.let { ":$it" }.orEmpty()
            // Absolute paths need a double slash after the authority: sftp://host:port//srv/repo
            "sftp://${percentEncode(normalizedUser, USER_SAFE_CHARS)}@$normalizedHost$portPart/" +
                percentEncode(normalizedPath, PATH_SAFE_CHARS)
        } else {
            "sftp:$normalizedUser@$normalizedHost:$normalizedPath"
        }
        return Result.Success(spec)
    }

    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val USER_SAFE_CHARS = "$UNRESERVED!$&'()*+,;="
    private const val PATH_SAFE_CHARS = "$UNRESERVED!$&'()*+,;=:@/"

    private fun percentEncode(value: String, safeChars: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val char = (byte.toInt() and 0xFF).toChar()
            if (byte >= 0 && safeChars.indexOf(char) >= 0) {
                append(char)
            } else {
                append('%')
                append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}
