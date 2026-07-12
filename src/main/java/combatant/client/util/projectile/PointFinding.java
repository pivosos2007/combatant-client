/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.projectile;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import combatant.client.util.raycast.RaycastUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

enum PointFinding {
    ;

    static Vec3 findVisiblePointFromVirtualEye(Vec3 virtualEyes, AABB box, double rangeToTest) {
        for (Vec3 preferred : preferredPoints(box)) {
            Vec3 visible = tryVisiblePoint(virtualEyes, box, rangeToTest, preferred);
            if (visible != null) {
                return visible;
            }
        }

        List<Vec3> points = projectPointsOnBox(virtualEyes, box, 128);
        if (points == null) {
            return null;
        }

        Vec3 center = box.getCenter();
        points.sort(Comparator.comparingDouble(point -> point.distanceToSqr(center)));

        for (Vec3 spot : points) {
            Vec3 visible = tryVisiblePoint(virtualEyes, box, rangeToTest, spot);
            if (visible != null) {
                return visible;
            }
        }

        return null;
    }

    private static List<Vec3> preferredPoints(AABB box) {
        Vec3 center = box.getCenter();
        return List.of(
                center,
                new Vec3(box.minX, center.y, center.z),
                new Vec3(box.maxX, center.y, center.z),
                new Vec3(center.x, box.minY, center.z),
                new Vec3(center.x, box.maxY, center.z),
                new Vec3(center.x, center.y, box.minZ),
                new Vec3(center.x, center.y, box.maxZ)
        );
    }

    private static Vec3 tryVisiblePoint(Vec3 virtualEyes, AABB box, double rangeToTest, Vec3 spot) {
        Vec3 vecFromEyes = spot.subtract(virtualEyes);
        Vec3 raycastTarget = virtualEyes.add(vecFromEyes.scale(2.0));
        Vec3 spotOnBox = firstHit(box, virtualEyes, raycastTarget);
        if (spotOnBox == null) {
            return null;
        }

        Vec3 rayStart = spotOnBox.subtract(withLength(vecFromEyes, rangeToTest));
        return RaycastUtil.hasLineOfSightPoint(rayStart, spotOnBox) ? spotOnBox : null;
    }

    static List<Vec3> projectPointsOnBox(Vec3 virtualEye, AABB targetBox, int maxPoints) {
        List<Vec3> list = new ArrayList<>();
        boolean success = projectPointsOnBox(virtualEye, targetBox, maxPoints, list::add);
        return success ? list : null;
    }

    static boolean projectPointsOnBox(Vec3 virtualEye, AABB targetBox, int maxPoints, Consumer<Vec3> consumer) {
        if (targetBox.contains(virtualEye)) {
            return false;
        }

        ProjectileLine playerToBoxLine = new ProjectileLine(virtualEye, targetBox.getCenter().subtract(virtualEye));
        Vec3[] edgePoints = edgePoints(targetBox);

        Vec3 targetFrameOrigin = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Vec3 edgePoint : edgePoints) {
            Vec3 nearest = playerToBoxLine.getNearestPointTo(edgePoint);
            double distance = nearest.distanceToSqr(virtualEye);
            if (distance < bestDistance) {
                bestDistance = distance;
                targetFrameOrigin = nearest;
            }
        }
        if (targetFrameOrigin == null) {
            return false;
        }
        targetFrameOrigin = targetFrameOrigin.lerp(virtualEye, 0.1);

        ProjectilePlane plane = new ProjectilePlane(targetFrameOrigin, playerToBoxLine.direction());
        MatrixPair matrices = getRotationMatricesForVec(plane.normalVec());

        List<Vector3f> projectedAndRotatedPoints = new ArrayList<>(edgePoints.length);
        for (Vec3 edgePoint : edgePoints) {
            Vec3 intersection = plane.intersection(ProjectileLine.fromPoints(virtualEye, edgePoint));
            if (intersection == null) {
                continue;
            }
            Vector3f rotated = toVector3f(intersection.subtract(targetFrameOrigin));
            rotated.mul(matrices.backMatrix());
            projectedAndRotatedPoints.add(rotated);
        }

        float minZ = 0.0f;
        float maxZ = 0.0f;
        float minY = 0.0f;
        float maxY = 0.0f;
        for (Vector3f point : projectedAndRotatedPoints) {
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }

        Vec3 posVec = toVec3d(new Vector3f(0.0f, minY, minZ).mul(matrices.toMatrix())).add(targetFrameOrigin);
        Vec3 dirVecY = toVec3d(new Vector3f(0.0f, maxY - minY, 0.0f).mul(matrices.toMatrix()));
        Vec3 dirVecZ = toVec3d(new Vector3f(0.0f, 0.0f, maxZ - minZ).mul(matrices.toMatrix()));

        PlaneSection planeSection = new PlaneSection(posVec, dirVecY, dirVecZ);
        planeSection.castPointsOnUniformly(maxPoints, point -> {
            Vec3 pointExtended = point.lerp(virtualEye, -100.0);
            Vec3 pos = firstHit(targetBox, virtualEye, pointExtended);
            if (pos != null) {
                consumer.accept(pos);
            }
        });

        return true;
    }

    private static MatrixPair getRotationMatricesForVec(Vec3 vec) {
        double hypotenuse = Math.hypot(vec.x, vec.z);
        float yawAtan = (float) Math.atan2(vec.z, vec.x);
        float pitchAtan = (float) Math.atan2(vec.y, hypotenuse);

        Matrix3f toMatrix = new Matrix3f().rotateY(-yawAtan).mul(new Matrix3f().rotateZ(pitchAtan));
        Matrix3f backMatrix = new Matrix3f().rotateZ(-pitchAtan).mul(new Matrix3f().rotateY(yawAtan));
        return new MatrixPair(toMatrix, backMatrix);
    }

    private static Vec3[] edgePoints(AABB box) {
        return new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ)
        };
    }

    private static Vec3 withLength(Vec3 vec, double length) {
        double currentLength = vec.length();
        if (currentLength < 1.0E-8) {
            return Vec3.ZERO;
        }
        return vec.scale(length / currentLength);
    }

    private static Vec3 firstHit(AABB box, Vec3 from, Vec3 to) {
        return box.clip(from, to).orElse(null);
    }

    private static Vector3f toVector3f(Vec3 vec) {
        return new Vector3f((float) vec.x, (float) vec.y, (float) vec.z);
    }

    private static Vec3 toVec3d(Vector3f vec) {
        return new Vec3(vec.x, vec.y, vec.z);
    }

    private record MatrixPair(Matrix3f toMatrix, Matrix3f backMatrix) {
    }

    private record StepPair(double dz, double dy) {
    }

    private record PlaneSection(Vec3 originPoint, Vec3 dirVec1, Vec3 dirVec2) {
        void castPointsOnUniformly(int maxPoints, Consumer<Vec3> consumer) {
            StepPair step = getFairStepSide(maxPoints);
            for (double y = 0.0; y <= 1.0 + 1.0E-9; y += step.dy()) {
                for (double z = 0.0; z <= 1.0 + 1.0E-9; z += step.dz()) {
                    consumer.accept(originPoint.add(dirVec1.scale(y)).add(dirVec2.scale(z)));
                }
            }
        }

        StepPair getFairStepSide(int pointCount) {
            boolean vec1Zero = dirVec1.lengthSqr() < 1.0E-8;
            boolean vec2Zero = dirVec2.lengthSqr() < 1.0E-8;
            if (!vec1Zero && !vec2Zero) {
                double aspectRatio = dirVec2.length() / dirVec1.length();
                double dz = Math.sqrt(1.0 / (aspectRatio * pointCount));
                double dy = Math.sqrt(aspectRatio / pointCount);
                return new StepPair(dz, dy);
            }
            if (vec1Zero && vec2Zero) {
                return new StepPair(1.0, 1.0);
            }
            if (vec1Zero) {
                return new StepPair(1.0, 2.0 / pointCount);
            }
            return new StepPair(2.0 / pointCount, 1.0);
        }
    }

    private record ProjectileLine(Vec3 position, Vec3 direction) {
        private ProjectileLine {
            if (direction.lengthSqr() < 1.0E-12) {
                throw new IllegalArgumentException("Direction should not be zero");
            }
        }

        static ProjectileLine fromPoints(Vec3 begin, Vec3 end) {
            return new ProjectileLine(begin, end.subtract(begin));
        }

        Vec3 getNearestPointTo(Vec3 point) {
            ProjectilePlane plane = new ProjectilePlane(point, direction);
            Vec3 intersection = plane.intersection(this);
            return intersection != null ? intersection : point;
        }

        Vec3 getPosition(double phi) {
            return position.add(direction.scale(phi));
        }
    }

    private record ProjectilePlane(Vec3 pos, Vec3 normalVec) {
        private ProjectilePlane(Vec3 pos, Vec3 normalVec) {
            this.pos = pos;
            this.normalVec = normalVec.normalize();
        }

        Vec3 intersection(ProjectileLine line) {
            double d = pos.dot(normalVec);
            double e = line.direction().dot(normalVec);
            if (Math.abs(e) < 1.0E-12) {
                return null;
            }

            double phi = (d - line.position.dot(normalVec)) / e;
            return line.getPosition(phi);
        }
    }
}
