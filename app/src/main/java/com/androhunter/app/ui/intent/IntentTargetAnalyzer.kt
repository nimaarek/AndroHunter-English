package com.androhunter.app.ui.intent

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.iface.reference.StringReference
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import java.io.File
import java.util.zip.ZipFile

data class IntentTargetComponent(
    val type: String,
    val className: String,
    val exported: Boolean,
    val extraKeys: List<String>,
    val actionLiterals: List<String>,
    val uriHints: List<String>
)

data class IntentTargetModel(
    val packageName: String,
    val components: List<IntentTargetComponent>,
    val dexFilesScanned: Int,
    val warnings: List<String>
) {
    val extraKeyCount: Int
        get() = components.sumOf { it.extraKeys.size }

    val actionLiteralCount: Int
        get() = components.sumOf { it.actionLiterals.size }

    val uriHintCount: Int
        get() = components.sumOf { it.uriHints.size }
}

object IntentTargetAnalyzer {
    private val extraGetterNames = setOf(
        "getStringExtra",
        "getCharSequenceExtra",
        "getByteExtra",
        "getShortExtra",
        "getIntExtra",
        "getLongExtra",
        "getFloatExtra",
        "getDoubleExtra",
        "getBooleanExtra",
        "getStringArrayExtra",
        "getCharSequenceArrayExtra",
        "getIntegerArrayListExtra",
        "getStringArrayListExtra",
        "getBundleExtra",
        "getParcelableExtra",
        "getParcelableArrayExtra",
        "getParcelableArrayListExtra",
        "getSerializableExtra"
    )

    private val extraKeyPattern = Regex("""^[A-Za-z0-9_.:$-]{1,128}$""")
    private val uriPattern = Regex("""(?i)^[a-z][a-z0-9+.-]*://[^\s"'<>]{1,160}$""")
    private val actionPattern = Regex("""^[A-Za-z][A-Za-z0-9_.-]{2,180}$""")
    private val dexEntryPattern = Regex("""classes(\d*)\.dex""")

    suspend fun analyze(context: Context, packageName: String): IntentTargetModel =
        withContext(Dispatchers.IO) {
            val warnings = mutableListOf<String>()
            val componentBuilders = linkedMapOf<String, ComponentBuilder>()
            val packageInfo = try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_ACTIVITIES or
                        PackageManager.GET_RECEIVERS or
                        PackageManager.GET_SERVICES
                )
            } catch (e: Exception) {
                return@withContext IntentTargetModel(
                    packageName = packageName,
                    components = emptyList(),
                    dexFilesScanned = 0,
                    warnings = listOf("Unable to read target package metadata: ${e.message ?: e.javaClass.simpleName}")
                )
            }

            packageInfo.activities?.forEach {
                componentBuilders[it.name] = ComponentBuilder("Activity", it.name, it.exported)
            }
            packageInfo.receivers?.forEach {
                componentBuilders[it.name] = ComponentBuilder("Receiver", it.name, it.exported)
            }
            packageInfo.services?.forEach {
                componentBuilders[it.name] = ComponentBuilder("Service", it.name, it.exported)
            }

            if (componentBuilders.isEmpty()) {
                warnings += "No activities, receivers, or services were returned by PackageManager."
            }

            val apkPath = try {
                context.packageManager.getApplicationInfo(packageName, 0).sourceDir
            } catch (e: Exception) {
                warnings += "Unable to locate target APK: ${e.message ?: e.javaClass.simpleName}"
                ""
            }

            var dexFilesScanned = 0
            if (apkPath.isNotBlank()) {
                try {
                    val apkFile = File(apkPath)
                    val dexEntries = ZipFile(apkFile).use { zip ->
                        val names = mutableListOf<String>()
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (!entry.isDirectory && dexEntryPattern.matches(entry.name)) {
                                names += entry.name
                            }
                        }
                        names.sortedWith(compareBy<String> {
                            val suffix = it.removePrefix("classes").removeSuffix(".dex")
                            suffix.toIntOrNull() ?: 1
                        }.thenBy { it })
                    }

                    if (dexEntries.isEmpty()) {
                        warnings += "No DEX files were found in the target APK."
                    }

                    dexEntries.forEach { entryName ->
                        try {
                            val dexFile = DexFileFactory.loadDexEntry(
                                apkFile,
                                entryName,
                                Opcodes.getDefault(),
                                false
                            )
                            dexFilesScanned++
                            dexFile.classes.forEach { classDef ->
                                val className = classDef.type
                                    .removePrefix("L")
                                    .removeSuffix(";")
                                    .replace('/', '.')
                                val builder = componentBuilders[className] ?: return@forEach
                                classDef.methods.forEach { method ->
                                    val instructions = method.implementation?.instructions?.toList()
                                        ?: return@forEach
                                    val recentStrings = ArrayDeque<String>()

                                    instructions.forEach { instruction ->
                                        val reference = (instruction as? ReferenceInstruction)?.reference

                                        if (instruction.opcode.name.startsWith("const-string") &&
                                            reference is StringReference
                                        ) {
                                            val value = reference.string
                                            if (value.isNotBlank()) {
                                                recentStrings.addLast(value)
                                                while (recentStrings.size > 10) recentStrings.removeFirst()
                                                if (uriPattern.matches(value)) builder.uriHints += value
                                            }
                                        }

                                        if (reference is MethodReference) {
                                            val owner = reference.definingClass
                                            val methodName = reference.name
                                            val candidate = recentStrings.toList().asReversed()
                                                .firstOrNull { extraKeyPattern.matches(it) }

                                            if (owner == "Landroid/content/Intent;" &&
                                                methodName in extraGetterNames &&
                                                candidate != null
                                            ) {
                                                builder.extraKeys += candidate
                                            }

                                            if (owner == "Landroid/content/Intent;" &&
                                                methodName in setOf("setAction", "addAction")
                                            ) {
                                                recentStrings.toList().asReversed()
                                                    .firstOrNull { actionPattern.matches(it) }
                                                    ?.let { builder.actionLiterals += it }
                                            }

                                            if (owner == "Landroid/net/Uri;" && methodName == "parse") {
                                                recentStrings.toList().asReversed()
                                                    .firstOrNull { uriPattern.matches(it) || it.startsWith("/") }
                                                    ?.let { builder.uriHints += it }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            warnings += "Unable to analyze $entryName: ${e.message ?: e.javaClass.simpleName}"
                        }
                    }
                } catch (e: Exception) {
                    warnings += "Unable to open target APK DEX data: ${e.message ?: e.javaClass.simpleName}"
                }
            }

            val components = componentBuilders.values.map { builder ->
                IntentTargetComponent(
                    type = builder.type,
                    className = builder.className,
                    exported = builder.exported,
                    extraKeys = builder.extraKeys.toList().sorted(),
                    actionLiterals = builder.actionLiterals.toList().sorted(),
                    uriHints = builder.uriHints.toList().sorted()
                )
            }

            if (dexFilesScanned > 0 && components.none {
                    it.extraKeys.isNotEmpty() || it.actionLiterals.isNotEmpty() || it.uriHints.isNotEmpty()
                }
            ) {
                warnings += "No input hints were identified. Reflection, obfuscation, or dynamic input handling may hide them."
            }

            IntentTargetModel(packageName, components, dexFilesScanned, warnings.distinct())
        }

    private data class ComponentBuilder(
        val type: String,
        val className: String,
        val exported: Boolean,
        val extraKeys: LinkedHashSet<String> = linkedSetOf(),
        val actionLiterals: LinkedHashSet<String> = linkedSetOf(),
        val uriHints: LinkedHashSet<String> = linkedSetOf()
    )
}
