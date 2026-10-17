package combatant.client.features.module.modules.visuals.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import combatant.client.util.schematic.SchematicDiff;
import org.junit.jupiter.api.Test;

class SchematicFaceCullingTest {

    private static int faces(int[] x, int[] y, int[] z, byte[] s, byte[] out) {
        new SchematicFaceCulling().compute(x, y, z, s, x.length, out);
        int total = 0;
        for (byte m : out) total += Integer.bitCount(m & 0xFF);
        return total;
    }

    @Test
    void solidCubeKeepsOnlyShell() {
        int n = 10, count = n * n * n;
        int[] x = new int[count], y = new int[count], z = new int[count];
        byte[] s = new byte[count], out = new byte[count];
        int i = 0;
        for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) for (int c = 0; c < n; c++) {
            x[i] = a; y[i] = b; z[i] = c; s[i++] = SchematicDiff.WRONG;
        }
        int after = faces(x, y, z, s, out);
        int before = count * 6;
        assertEquals(6000, before);
        assertEquals(600, after);
        System.out.printf("10x10x10 solid cube: faces %d -> %d, vertices %d -> %d%n", before, after, before * 4, after * 4);
    }

    @Test
    void differentStatusKeepsSharedFace() {
        byte[] out = new byte[2];
        int total = faces(new int[] {0, 1}, new int[] {0, 0}, new int[] {0, 0},
                new byte[] {SchematicDiff.MISSING, SchematicDiff.WRONG}, out);
        assertEquals(12, total);
    }

    @Test
    void missingNeighboursKeepAllFacesBecauseTheyAreDrawnInset() {
        byte[] out = new byte[2];
        int total = faces(new int[] {0, 1}, new int[] {0, 0}, new int[] {0, 0},
                new byte[] {SchematicDiff.MISSING, SchematicDiff.MISSING}, out);
        assertEquals(12, total);
        assertEquals(SchematicFaceCulling.ALL_FACES, out[0] & 0xFF);
        assertEquals(SchematicFaceCulling.ALL_FACES, out[1] & 0xFF);
    }

    @Test
    void fullSizeCellKeepsItsFaceTouchingAMissingCell() {
        byte[] out = new byte[2];
        int total = faces(new int[] {0, 1}, new int[] {0, 0}, new int[] {0, 0},
                new byte[] {SchematicDiff.CORRECT, SchematicDiff.MISSING}, out);
        assertEquals(12, total);
    }

    @Test
    void sameStatusNeighboursDropSharedFaceAndNegativeCoordsWork() {
        byte[] out = new byte[2];
        int total = faces(new int[] {-1, 0}, new int[] {-5, -5}, new int[] {-3, -3},
                new byte[] {SchematicDiff.WRONG, SchematicDiff.WRONG}, out);
        assertEquals(10, total);
        assertEquals(SchematicFaceCulling.ALL_FACES & ~SchematicFaceCulling.FACE_EAST, out[0] & 0xFF);
        assertEquals(SchematicFaceCulling.ALL_FACES & ~SchematicFaceCulling.FACE_WEST, out[1] & 0xFF);
    }
}
