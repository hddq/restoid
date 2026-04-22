package io.github.hddq.restoid.util

private val ENV_NAME_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
private val RESTIC_OPTION_NAME_REGEX = Regex("^[A-Za-z0-9_.-]+$")

fun isValidEnvironmentVariableName(name: String): Boolean {
    return ENV_NAME_REGEX.matches(name)
}

fun isValidResticOptionName(name: String): Boolean {
    return RESTIC_OPTION_NAME_REGEX.matches(name)
}

fun shellQuote(value: String): String {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}

fun buildShellEnvironmentPrefix(environmentVariables: Map<String, String>): String {
    if (environmentVariables.isEmpty()) return ""

    return environmentVariables.entries
        .sortedBy { it.key }
        .joinToString(" ") { (name, value) ->
            "$name=${shellQuote(value)}"
        }
}

fun buildResticOptionFlags(options: Map<String, String>): String {
    if (options.isEmpty()) return ""

    return options.entries
        .sortedBy { it.key }
        .joinToString(" ") { (name, value) ->
            "-o ${shellQuote("$name=$value")}"
        }
}
