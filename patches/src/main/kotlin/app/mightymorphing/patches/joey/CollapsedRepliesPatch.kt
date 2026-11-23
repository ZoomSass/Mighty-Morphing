package app.mightymorphing.patches.joey

import app.mightymorphing.patches.shared.Constants.JOEY
import app.mightymorphing.patches.shared.insertAt
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private const val MORE = "Lapp/mightymorphing/extension/joey/MoreComments;"

@Suppress("unused")
val collapsedRepliesPatch = bytecodePatch(
    name = "Fix collapsed replies",
    description = "[v1.6.1 test build: Extra logging is included to help trace comments that won't load. Everything logged can be seen in Joey Settings - Morphe Settings - Report a problem. Nothing is uploaded automatically; the upload link has to be sent to the dev by you.] Loads replies that stay hidden when Reddit will not open them. Based on the Patcheddit fix.",
) {
    compatibleWith(JOEY)

    dependsOn(problemReportPatch)

    execute {
        val request = MoreChildrenRequestFingerprint.method
        request.insertAt(
            0,
            """
            invoke-static {p0, p1}, $MORE->rewrite(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v0
            if-eqz v0, :collapsed_replies_joey
            check-cast v0, Lnet/dean/jraw/http/m;
            return-object v0
            """,
            ExternalLabel("collapsed_replies_joey", request.instructions[0]),
        )
    }
}
