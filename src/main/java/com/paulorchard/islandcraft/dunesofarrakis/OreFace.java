package com.paulorchard.islandcraft.dunesofarrakis;

import org.joml.Vector3d;
import org.joml.Vector3i;

/** Where a ray from the player's eyes enters a block: the face and its middle. Copied (reduced) from the Equipment mod's BeamMath. */
final class OreFace {

    record Entry(Vector3i normal, Vector3d faceCenter) {
    }

    private OreFace() {
    }

    /** The face of the unit block at {@code block} that the ray enters through; a ray that starts inside it gets the face against its largest component. */
    static Entry entry(Vector3d origin, Vector3d dir, Vector3i block) {
        double near = 0;
        int entryAxis = -1;
        double[] o = {origin.x, origin.y, origin.z};
        double[] d = {dir.x, dir.y, dir.z};
        int[] b = {block.x, block.y, block.z};
        double far = Double.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1e-9) {
                continue;
            }
            double t1 = (b[axis] - o[axis]) / d[axis];
            double t2 = (b[axis] + 1 - o[axis]) / d[axis];
            double enter = Math.min(t1, t2);
            if (enter > near) {
                near = enter;
                entryAxis = axis;
            }
            far = Math.min(far, Math.max(t1, t2));
        }
        if (entryAxis < 0 || near > far) {
            int largest = 0;
            for (int axis = 1; axis < 3; axis++) {
                if (Math.abs(d[axis]) > Math.abs(d[largest])) {
                    largest = axis;
                }
            }
            entryAxis = largest;
        }
        int[] n = {0, 0, 0};
        n[entryAxis] = d[entryAxis] > 0 ? -1 : 1;
        Vector3d center = new Vector3d(block.x + 0.5, block.y + 0.5, block.z + 0.5);
        center.add(n[0] * 0.5, n[1] * 0.5, n[2] * 0.5);
        return new Entry(new Vector3i(n[0], n[1], n[2]), center);
    }

    /** The point {@code clearance} out from the middle of a face, along its normal. */
    static Vector3d offFace(Entry entry, double clearance) {
        return new Vector3d(entry.faceCenter()).fma(clearance, new Vector3d(entry.normal().x, entry.normal().y, entry.normal().z));
    }
}
