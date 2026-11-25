package app.mightymorphing.extension.joey;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

/**
 * Keeps a downloaded album in the order of the post.
 * Joey downloads the images in order, but names each one after its address and then copies the
 * folder in whatever order the file system lists it. The position goes in front of the name,
 * and the copy follows the positions.
 */
public final class AlbumOrder {
    private static final int MIN_WIDTH = 2;

    private static final Comparator<String> BY_POSITION = (a, b) -> {
        long first = position(a);
        long second = position(b);
        if (first != second) return first < second ? -1 : 1;
        return a.compareTo(b);
    };

    private AlbumOrder() {
    }

    /**
     * Joey is naming the file for one image of an album.
     * [position] starts at 1. [album] is every image in the album, used for the padding only.
     */
    public static String fileName(String joeyName, int position, Collection<?> album) {
        if (position <= 0) return joeyName;
        int width = Math.max(MIN_WIDTH, String.valueOf(album == null ? 0 : album.size()).length());
        String number = String.format(Locale.US, "%0" + width + "d", position);
        return number + "_" + (joeyName == null ? "" : joeyName);
    }

    /** Joey is about to copy a downloaded folder. Returns the listing in album order. */
    public static String[] sorted(String[] listing) {
        if (listing == null) return null;
        String[] ordered = listing.clone();
        Arrays.sort(ordered, BY_POSITION);
        return ordered;
    }

    /** The number in front of the first underscore. Names without one sort last. */
    private static long position(String name) {
        int split = name.indexOf('_');
        if (split <= 0 || split > 18) return Long.MAX_VALUE;
        long value = 0;
        for (int i = 0; i < split; i++) {
            char digit = name.charAt(i);
            if (digit < '0' || digit > '9') return Long.MAX_VALUE;
            value = value * 10 + (digit - '0');
        }
        return value;
    }
}
