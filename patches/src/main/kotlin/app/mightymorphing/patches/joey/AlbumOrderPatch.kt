package app.mightymorphing.patches.joey

import app.mightymorphing.patches.shared.Constants.JOEY
import app.mightymorphing.patches.shared.indexOfReference
import app.mightymorphing.patches.shared.insertAt
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch

/*
 * Joey downloads an album one image at a time, in the order of the post, into a hidden folder.
 * Each file is named after the last part of its address, which for a Reddit gallery is a random
 * id. When the last image is in, Joey copies the folder to the download location in the order
 * File.list() returns, which is whatever order the file system keeps. The saved album then has
 * no order by name, and its order by date depends on the phone.
 *
 * Two edits: the position goes in front of each file name, and the copy follows the positions.
 */

private const val ALBUM_ORDER = "Lapp/mightymorphing/extension/joey/AlbumOrder;"
private const val SERVICE = "Lo/o/joey/Download/DownloadService;"

@Suppress("unused")
val albumDownloadOrderPatch = bytecodePatch(
    name = "Album downloads in order",
    description = "Saves the images of an album or gallery in the order of the post. " +
        "Each file name starts with its position, such as 01_ and 02_.",
    default = false,
) {
    compatibleWith(JOEY)

    extendWith("extensions/extension.mpe")

    execute {
        // The album branch of the download service. q(String) turns an address into a file name
        // and is called nowhere else. Its result lands in v13. In this loop v1 is the position,
        // counted from 1, and v3 is the list of addresses.
        val queue = Fingerprint(
            definingClass = SERVICE,
            name = "y",
            returnType = "V",
            parameters = listOf("Landroid/content/Intent;"),
        ).method
        val naming = queue.indexOfReference("$SERVICE->q(Ljava/lang/String;)Ljava/lang/String;")
        queue.insertAt(
            naming + 2,
            """
            invoke-static {v13, v1, v3}, $ALBUM_ORDER->fileName(Ljava/lang/String;ILjava/util/Collection;)Ljava/lang/String;
            move-result-object v13
            """,
        )

        // The copy to the download location. For a folder it lists the files (v8) and copies each.
        val copy = Fingerprint(
            definingClass = SERVICE,
            name = "l",
            returnType = "Landroid/net/Uri;",
            parameters = listOf(
                "Ljava/lang/String;",
                "Ljava/lang/String;",
                "Landroid/net/Uri;",
                "Landroid/content/Context;",
            ),
        ).method
        val listing = copy.indexOfReference("Ljava/io/File;->list()[Ljava/lang/String;")
        copy.insertAt(
            listing + 2,
            """
            invoke-static {v8}, $ALBUM_ORDER->sorted([Ljava/lang/String;)[Ljava/lang/String;
            move-result-object v8
            """,
        )
    }
}
