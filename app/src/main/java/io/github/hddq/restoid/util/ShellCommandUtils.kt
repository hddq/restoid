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

fun buildCustomFileRestoreCommand(sourcePath: String, targetPath: String): String {
    val cleanTargetPath = targetPath.trimEnd('/')
    val quotedSource = shellQuote(sourcePath)
    val quotedTarget = shellQuote(cleanTargetPath)
    return "rm -rf $quotedTarget && cp -a $quotedSource $quotedTarget"
}

fun buildCustomDirectoryRestoreCommand(sourcePath: String, targetPath: String): String {
    val cleanTargetPath = targetPath.trimEnd('/')
    val quotedSource = shellQuote(sourcePath)
    val quotedTarget = shellQuote(cleanTargetPath)
    val quotedSourceContents = shellQuote("${sourcePath.trimEnd('/')}/.")
    val quotedTargetDir = shellQuote("$cleanTargetPath/")

    val cleanupScript = buildString {
        append("dst_base=\"\$1\"; shift; ")
        append("for rel do ")
        append("target=\"\$dst_base/\$rel\"; ")
        append("if [ -L \"\$target\" ]; then ")
        append("rm -rf \"\$target\"; ")
        append("elif [ -d \"\$rel\" ]; then ")
        append("if [ -e \"\$target\" ] && [ ! -d \"\$target\" ]; then ")
        append("rm -rf \"\$target\"; ")
        append("fi; ")
        append("else ")
        append("rm -rf \"\$target\"; ")
        append("fi; ")
        append("done")
    }

    return "if [ -L $quotedTarget ] || [ ! -d $quotedTarget ]; then " +
        "rm -rf $quotedTarget && mkdir -p $quotedTarget; " +
        "fi && " +
        "(cd $quotedSource && find . -mindepth 1 -exec sh -c '$cleanupScript' sh $quotedTarget {} +) && " +
        "cp -a $quotedSourceContents $quotedTargetDir"
}

