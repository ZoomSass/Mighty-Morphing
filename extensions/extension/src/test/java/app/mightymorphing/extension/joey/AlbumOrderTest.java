package app.mightymorphing.extension.joey;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AlbumOrderTest {

    private static List<String> album(int size) {
        return new ArrayList<>(Collections.nCopies(size, "https://i.redd.it/x.jpg"));
    }

    @Test
    public void fileNamePutsThePositionBeforeJoeysName() {
        assertEquals("03_k3j2h1g0f9e8d.jpg", AlbumOrder.fileName("k3j2h1g0f9e8d.jpg", 3, album(12)));
    }

    @Test
    public void fileNamePadsToTwoDigitsInAShortAlbum() {
        assertEquals("01_a.png", AlbumOrder.fileName("a.png", 1, album(2)));
    }

    @Test
    public void fileNamePadsToTheWidthOfTheLastPosition() {
        assertEquals("007_a.png", AlbumOrder.fileName("a.png", 7, album(120)));
        assertEquals("120_a.png", AlbumOrder.fileName("a.png", 120, album(120)));
    }

    @Test
    public void fileNameKeepsTwoImagesWithTheSameNameApart() {
        List<String> album = album(2);
        String first = AlbumOrder.fileName("same.jpg", 1, album);
        String second = AlbumOrder.fileName("same.jpg", 2, album);
        assertEquals("01_same.jpg", first);
        assertEquals("02_same.jpg", second);
    }

    @Test
    public void fileNameIsLeftAloneWithoutAPosition() {
        assertEquals("a.png", AlbumOrder.fileName("a.png", 0, album(3)));
    }

    @Test
    public void fileNameWorksWithoutTheAlbumList() {
        assertEquals("04_a.png", AlbumOrder.fileName("a.png", 4, null));
    }

    @Test
    public void fileNameNumbersAnImageJoeyCouldNotName() {
        assertEquals("02_", AlbumOrder.fileName("", 2, album(3)));
        assertEquals("02_", AlbumOrder.fileName(null, 2, album(3)));
    }

    @Test
    public void sortedPutsAScrambledListingBackInAlbumOrder() {
        String[] listing = {"03_zzz.jpg", "01_mmm.jpg", "04_aaa.jpg", "02_qqq.jpg"};
        assertArrayEquals(
                new String[] {"01_mmm.jpg", "02_qqq.jpg", "03_zzz.jpg", "04_aaa.jpg"},
                AlbumOrder.sorted(listing));
    }

    @Test
    public void sortedComparesPositionsAsNumbers() {
        String[] listing = {"10_a.jpg", "9_b.jpg", "100_c.jpg", "2_d.jpg"};
        assertArrayEquals(
                new String[] {"2_d.jpg", "9_b.jpg", "10_a.jpg","100_c.jpg"},
                AlbumOrder.sorted(listing));
    }

    @Test
    public void sortedPutsUnnumberedNamesLastInNameOrder() {
        String[] listing = {"zebra.jpg", "02_b.jpg", "apple.jpg", "01_a.jpg"};
        assertArrayEquals(
                new String[] {"01_a.jpg", "02_b.jpg", "apple.jpg", "zebra.jpg"},
                AlbumOrder.sorted(listing));
    }

    @Test
    public void sortedPassesAMissingListingThrough() {
        assertNull(AlbumOrder.sorted(null));
    }

    @Test
    public void sortedLeavesTheListingItWasGivenUntouched() {
        String[] listing = {"02_b.jpg", "01_a.jpg"};
        AlbumOrder.sorted(listing);
        assertArrayEquals(new String[] {"02_b.jpg", "01_a.jpg"}, listing);
    }
}
