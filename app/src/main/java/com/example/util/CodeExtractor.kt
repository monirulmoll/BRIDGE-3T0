package com.example.util

import java.security.MessageDigest

object CodeExtractor {

    private val FENCED_CODE_REGEX = Regex("```(?:[a-zA-Z0-9_+#.-]+)?\\s*\\n?([\\s\\S]*?)```")

    /**
     * Extracts ONLY pure code from text containing fenced code blocks.
     * Excludes explanations, markdown fences, headings, and conversational chatter.
     * Returns null if no valid code is found.
     */
    fun extractExecutableCode(text: String): String? {
        if (text.isBlank()) return null

        // 1. Check for fenced code blocks ```code```
        val matches = FENCED_CODE_REGEX.findAll(text).toList()
        if (matches.isNotEmpty()) {
            val codeCandidates = matches.map { match ->
                cleanCodeSnippet(match.groupValues[1])
            }.filter { it.isNotBlank() }

            if (codeCandidates.isNotEmpty()) {
                // Return the latest or largest clean code block
                return codeCandidates.last()
            }
        }

        // 2. Check if the block itself is an un-fenced pure command snippet
        val trimmed = text.trim()
        if (isPureTerminalCommand(trimmed)) {
            return cleanCodeSnippet(trimmed)
        }

        return null
    }

    /**
     * Cleans code by removing markdown artifacts, leading/trailing fences, and carriage returns
     */
    fun cleanCodeSnippet(rawCode: String): String {
        return rawCode
            .replace("\r\n", "\n")
            .lines()
            // Remove lingering markdown backticks if any
            .filter { line ->
                val t = line.trim()
                !t.startsWith("```") && !t.endsWith("```")
            }
            .joinToString("\n")
            .trim()
    }

    /**
     * Detects if an un-fenced block of text is purely a shell command or script
     */
    private fun isPureTerminalCommand(text: String): Boolean {
        if (text.length < 3 || text.length > 2000) return false
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return false

        // Check for conversational markers - if present, it is NOT pure code
        val conversationalMarkers = listOf(
            "here is", "sure!", "i have", "let me", "you can", "to fix this",
            "try running", "note:", "make sure", "output:", "hope this helps",
            "karein", "yeh command", "chalao"
        )
        val lower = text.lowercase()
        if (conversationalMarkers.any { lower.contains(it) }) {
            return false
        }

        val firstLine = lines.first()
        val shellCommandStarters = listOf(
            "pkg ", "apt ", "curl ", "wget ", "bash ", "sh ", "python ",
            "pip ", "git ", "npm ", "node ", "chmod ", "chown ", "mkdir ",
            "cd ", "ls ", "cat ", "echo ", "export ", "sed ", "awk ", "grep ",
            "tar ", "unzip ", "touch ", "rm ", "cp ", "mv ", "clear",
            "find ", "sudo ", "nano ", "vim ", "#!/bin/", "./"
        )

        return shellCommandStarters.any { firstLine.startsWith(it) }
    }

    fun computeHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.trim().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
