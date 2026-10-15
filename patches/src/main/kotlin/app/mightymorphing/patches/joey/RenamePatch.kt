package app.mightymorphing.patches.joey

import app.mightymorphing.patches.shared.Constants.JOEY
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val ORIGINAL_PACKAGE = "o.o.joey"
private const val RENAMED_PACKAGE = "o.o.joeyx"

@Suppress("unused")
val renameToJoeyXPatch = resourcePatch(
    name = "Rename to JoeyX",
    description = "Changes the package to o.o.joeyx and the launcher name to JoeyX. Leave this off to keep o.o.joey and combine these patches with other Joey patches. Morphe will not see the renamed APK as Joey.",
    default = false,
) {
    compatibleWith(JOEY)

    execute {
        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement
            val packageName = root.getAttribute("package")
            if (packageName != ORIGINAL_PACKAGE) {
                error("Expected package $ORIGINAL_PACKAGE and found $packageName")
            }
            root.setAttribute("package", RENAMED_PACKAGE)

            val elements = manifest.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as Element
                val attributes = element.attributes
                for (attributeIndex in 0 until attributes.length) {
                    val attribute = attributes.item(attributeIndex)
                    val attributeName = attribute.nodeName.substringAfter(':')
                    val value = attribute.nodeValue ?: continue
                    val isAuthority = attributeName == "authorities" && value.startsWith("$ORIGINAL_PACKAGE.")
                    val isPermission = (element.tagName == "permission" || element.tagName == "uses-permission") &&
                        attributeName == "name" &&
                        value.startsWith("$ORIGINAL_PACKAGE.")
                    if (isAuthority || isPermission) {
                        attribute.nodeValue = RENAMED_PACKAGE + value.removePrefix(ORIGINAL_PACKAGE)
                    }
                }
            }
        }

        document("res/values/strings.xml").use { strings ->
            val nodes = strings.getElementsByTagName("string")
            var found = false
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as Element
                if (node.getAttribute("name") == "app_name") {
                    node.textContent = "JoeyX"
                    found = true
                }
            }
            if (!found) {
                error("Joey string app_name was not in res/values/strings.xml")
            }
        }
    }
}
