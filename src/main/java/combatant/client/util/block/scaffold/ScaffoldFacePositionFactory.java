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

package combatant.client.util.block.scaffold;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.EnumValue;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ScaffoldFacePositionFactory {
    private static final float YAW_TOLERANCE = 5.0f;
    private final LocalPlayer player;
    private final Vec3 eyePos;
    private final Rotation baseRotation;
    private final Mode mode;
    private final ScaffoldLine optimalLine;
    private final double randomNumber;
    public ScaffoldFacePositionFactory(
            LocalPlayer player,
            Mode mode,
            ScaffoldLine optimalLine,
            double randomNumber
    ) {
        this(player, player != null ? player.getEyePosition() : Vec3.ZERO, mode, optimalLine, randomNumber);
    }

    public ScaffoldFacePositionFactory(
            LocalPlayer player,
            Vec3 eyePos,
            Mode mode,
            ScaffoldLine optimalLine,
            double randomNumber
    ) {
        this.player = player;
        this.eyePos = eyePos != null ? eyePos : (player != null ? player.getEyePosition() : Vec3.ZERO);
        this.baseRotation = RotationManager.INSTANCE.getServerRotation();
        this.mode = mode;
        this.optimalLine = optimalLine;
        this.randomNumber = randomNumber;
    }

    private static void addUnique(List<Vec3> points, Vec3 point, double eps) {
        for (Vec3 existing : points) {
            if (existing.distanceToSqr(point) <= eps * eps) {
                return;
            }
        }
        points.add(point);
    }

    private static LineSegment toSegment(List<Vec3> points) {
        if (points.size() < 2) {
            return null;
        }
        return new LineSegment(points.get(0), points.get(1));
    }

    private static double lerp(double start, double end, double delta) {
        return start + (end - start) * delta;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public Vec3 producePoint(Face face) {
        return switch (mode) {
            case CENTER -> face.center();
            case RANDOM -> trimFace(face).random(randomNumber);
            case STABILIZED -> chooseStabilizedPoint(face);
            case NEAREST_ROTATION -> aimAtNearestPointToRotationLine(trimFace(face));
            case REVERSE_YAW -> chooseYawPoint(face, 180.0f);
            case DIAGONAL_YAW -> chooseYawPoint(face, 75.0f);
            case ANGLE_YAW -> chooseYawPoint(face, 45.0f);
            case EDGE_POINT -> chooseEdgePoint(face);
        };
    }

    private Vec3 chooseYawPoint(Face face, float angle) {
        Face trimmedFace = trimFace(face);
        if (!hasMovementInput()) {
            return aimAtNearestPointToRotationLine(trimmedFace);
        }

        YawCandidate positive = findYawCandidate(trimmedFace, Mth.wrapDegrees(player.getYRot() + angle));
        YawCandidate negative = findYawCandidate(trimmedFace, Mth.wrapDegrees(player.getYRot() - angle));
        YawCandidate best = null;

        boolean positiveOk = positive != null && positive.diff <= YAW_TOLERANCE;
        boolean negativeOk = negative != null && negative.diff <= YAW_TOLERANCE;

        if (positiveOk && negativeOk) {
            best = positive.diff < negative.diff ? positive : negative;
        } else if (positiveOk) {
            best = positive;
        } else if (negativeOk) {
            best = negative;
        }

        if (best != null && !isYawCandidateConsistent(best.point)) {
            best = null;
        }

        return best != null ? best.point : aimAtNearestPointToRotationLine(trimmedFace);
    }

    private boolean isYawCandidateConsistent(Vec3 point) {
        if (point == null) {
            return false;
        }

        Rotation reference = RotationManager.INSTANCE.getCurrentRotation();
        if (reference == null) {
            reference = baseRotation;
        }
        if (reference == null) {
            return true;
        }

        Rotation candidateRotation = Rotation.lookingAt(point, eyePos).normalize();
        return reference.angleTo(candidateRotation) <= 100.0f;
    }

    private boolean hasMovementInput() {
        if (player == null || player.input == null) {
            return false;
        }
        return Math.abs(player.input.getMoveVector().x) > 1.0E-4f
                || Math.abs(player.input.getMoveVector().y) > 1.0E-4f;
    }

    private YawCandidate findYawCandidate(Face face, float targetYaw) {
        LineSegment segment = intersectFaceWithYawPlane(face, targetYaw);
        if (segment == null) {
            return null;
        }

        Vec3 point = closestPointToYaw(segment, targetYaw);
        float diff = yawDifference(point, targetYaw);
        return new YawCandidate(point, diff);
    }

    private LineSegment intersectFaceWithYawPlane(Face face, float targetYaw) {
        double yawRad = Math.toRadians(targetYaw);
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);
        double a = -dirZ;
        double b = dirX;
        double c = -(a * eyePos.x + b * eyePos.z);
        double eps = 1.0E-7;

        if (Math.abs(face.maxY - face.minY) < eps) {
            List<Vec3> points = new ArrayList<>(4);
            collectHorizontalPlanePoint(points, face.minX, face, a, b, c, true, eps);
            collectHorizontalPlanePoint(points, face.maxX, face, a, b, c, true, eps);
            collectHorizontalPlanePoint(points, face.minZ, face, a, b, c, false, eps);
            collectHorizontalPlanePoint(points, face.maxZ, face, a, b, c, false, eps);
            return toSegment(points);
        }

        if (Math.abs(face.maxX - face.minX) < eps) {
            if (Math.abs(b) < eps) {
                return null;
            }
            double z = -(a * face.minX + c) / b;
            if (z < face.minZ - eps || z > face.maxZ + eps) {
                return null;
            }
            return new LineSegment(
                    new Vec3(face.minX, face.minY, z),
                    new Vec3(face.minX, face.maxY, z)
            );
        }

        if (Math.abs(face.maxZ - face.minZ) < eps) {
            if (Math.abs(a) < eps) {
                return null;
            }
            double x = -(b * face.minZ + c) / a;
            if (x < face.minX - eps || x > face.maxX + eps) {
                return null;
            }
            return new LineSegment(
                    new Vec3(x, face.minY, face.minZ),
                    new Vec3(x, face.maxY, face.minZ)
            );
        }

        return null;
    }

    private void collectHorizontalPlanePoint(
            List<Vec3> points,
            double fixed,
            Face face,
            double a,
            double b,
            double c,
            boolean fixedX,
            double eps
    ) {
        if (fixedX) {
            if (Math.abs(b) < eps) {
                return;
            }
            double z = -(a * fixed + c) / b;
            if (z >= face.minZ - eps && z <= face.maxZ + eps) {
                addUnique(points, new Vec3(fixed, face.minY, z), eps);
            }
            return;
        }

        if (Math.abs(a) < eps) {
            return;
        }
        double x = -(b * fixed + c) / a;
        if (x >= face.minX - eps && x <= face.maxX + eps) {
            addUnique(points, new Vec3(x, face.minY, fixed), eps);
        }
    }

    private Vec3 closestPointToYaw(LineSegment segment, float targetYaw) {
        float startYaw = pointYaw(segment.start);
        float endYaw = pointYaw(segment.end);
        float yawDiff = Mth.wrapDegrees(endYaw - startYaw);
        float targetYawDiff = Mth.wrapDegrees(targetYaw - startYaw);
        double t = Math.abs(yawDiff) > 1.0E-4f ? targetYawDiff / yawDiff : 0.0;
        t = Mth.clamp(t, 0.0, 1.0);
        return new Vec3(
                lerp(segment.start.x, segment.end.x, t),
                lerp(segment.start.y, segment.end.y, t),
                lerp(segment.start.z, segment.end.z, t)
        );
    }

    private float pointYaw(Vec3 point) {
        return Rotation.lookingAt(point, eyePos).normalize().yaw();
    }

    private float yawDifference(Vec3 point, float targetYaw) {
        return Math.abs(Mth.wrapDegrees(pointYaw(point) - targetYaw));
    }

    private Face trimFace(Face face) {
        return face.trim(0.15);
    }

    private Vec3 aimAtNearestPointToRotationLine(Face face) {
        Rotation rotation = baseRotation != null ? baseRotation : Rotation.ZERO;
        Vec3 direction = rotation.directionVector();
        double eps = 1.0E-7;

        if (Math.abs(face.maxX - face.minX) < eps) {
            double t = Math.abs(direction.x) > eps ? (face.minX - eyePos.x) / direction.x : 0.0;
            double y = eyePos.y + direction.y * t;
            double z = eyePos.z + direction.z * t;
            return new Vec3(face.minX, clamp(y, face.minY, face.maxY), clamp(z, face.minZ, face.maxZ));
        }

        if (Math.abs(face.maxY - face.minY) < eps) {
            double t = Math.abs(direction.y) > eps ? (face.minY - eyePos.y) / direction.y : 0.0;
            double x = eyePos.x + direction.x * t;
            double z = eyePos.z + direction.z * t;
            return new Vec3(clamp(x, face.minX, face.maxX), face.minY, clamp(z, face.minZ, face.maxZ));
        }

        if (Math.abs(face.maxZ - face.minZ) < eps) {
            double t = Math.abs(direction.z) > eps ? (face.minZ - eyePos.z) / direction.z : 0.0;
            double x = eyePos.x + direction.x * t;
            double y = eyePos.y + direction.y * t;
            return new Vec3(clamp(x, face.minX, face.maxX), clamp(y, face.minY, face.maxY), face.minZ);
        }

        return face.center();
    }

    private Vec3 chooseStabilizedPoint(Face face) {
        Face trimmedFace = trimFace(face);
        Face targetFace = getStabilizedTargetFace(trimmedFace);
        return aimAtNearestPointToRotationLine(targetFace != null ? targetFace : trimmedFace);
    }

    private Face getStabilizedTargetFace(Face face) {
        if (optimalLine == null || player == null) {
            return null;
        }

        Vec3 nearestPointToOptimalLine = optimalLine.getNearestPointTo(player.position());
        Vec3 directionToOptimalLine = player.position().subtract(nearestPointToOptimalLine);
        if (directionToOptimalLine.lengthSqr() < 1.0E-7) {
            return face;
        }

        Vec3 collisionWithFacePlane = intersectFacePlane(new ScaffoldLine(eyePos, optimalLine.direction()), face);
        if (collisionWithFacePlane == null) {
            return null;
        }

        Vec3 b = player.position().add(directionToOptimalLine.normalize().scale(2.0));
        AABB cropBox = new AABB(
                Math.min(collisionWithFacePlane.x, b.x),
                player.getY() - 2.0,
                Math.min(collisionWithFacePlane.z, b.z),
                Math.max(collisionWithFacePlane.x, b.x),
                player.getY() + 1.0,
                Math.max(collisionWithFacePlane.z, b.z)
        );

        Face clampedFace = face.clamp(cropBox);
        return clampedFace.area() < 1.0E-4 ? null : clampedFace;
    }

    private Vec3 intersectFacePlane(ScaffoldLine line, Face face) {
        if (line == null || face == null) {
            return null;
        }

        Vec3 origin = line.position();
        Vec3 direction = line.direction();
        double eps = 1.0E-7;

        if (Math.abs(face.maxX - face.minX) < eps) {
            if (Math.abs(direction.x) < eps) return null;
            double t = (face.minX - origin.x) / direction.x;
            return new Vec3(face.minX, origin.y + direction.y * t, origin.z + direction.z * t);
        }
        if (Math.abs(face.maxY - face.minY) < eps) {
            if (Math.abs(direction.y) < eps) return null;
            double t = (face.minY - origin.y) / direction.y;
            return new Vec3(origin.x + direction.x * t, face.minY, origin.z + direction.z * t);
        }
        if (Math.abs(face.maxZ - face.minZ) < eps) {
            if (Math.abs(direction.z) < eps) return null;
            double t = (face.minZ - origin.z) / direction.z;
            return new Vec3(origin.x + direction.x * t, origin.y + direction.y * t, face.minZ);
        }

        return null;
    }

    private Vec3 chooseEdgePoint(Face face) {
        Face trimmedFace = trimFace(face);
        if (!hasMovementInput()) {
            return aimAtNearestPointToRotationLine(trimmedFace);
        }

        return trimmedFace.edgePoints().stream()
                .max(Comparator.comparingDouble(this::edgeDistanceScore))
                .orElseGet(trimmedFace::center);
    }

    private double edgeDistanceScore(Vec3 point) {
        if (player == null) {
            return 0.0;
        }
        Vec3 localPlayerPos = player.position().subtract(player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ());
        return point.distanceToSqr(localPlayerPos);
    }

    public enum Mode implements EnumValue.IdProvider {
        CENTER("center"),
        RANDOM("random"),
        STABILIZED("stabilized"),
        NEAREST_ROTATION("nearest_rotation"),
        REVERSE_YAW("reverse_yaw"),
        DIAGONAL_YAW("diagonal_yaw"),
        ANGLE_YAW("angle_yaw"),
        EDGE_POINT("edge_point");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public static final class Face {
        private final double minX;
        private final double maxX;
        private final double minY;
        private final double maxY;
        private final double minZ;
        private final double maxZ;

        public Face(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        public static Face fromBox(AABB box, Direction direction) {
            return switch (direction) {
                case UP -> new Face(box.minX, box.maxX, box.maxY, box.maxY, box.minZ, box.maxZ);
                case DOWN -> new Face(box.minX, box.maxX, box.minY, box.minY, box.minZ, box.maxZ);
                case NORTH -> new Face(box.minX, box.maxX, box.minY, box.maxY, box.minZ, box.minZ);
                case SOUTH -> new Face(box.minX, box.maxX, box.minY, box.maxY, box.maxZ, box.maxZ);
                case EAST -> new Face(box.maxX, box.maxX, box.minY, box.maxY, box.minZ, box.maxZ);
                case WEST -> new Face(box.minX, box.minX, box.minY, box.maxY, box.minZ, box.maxZ);
            };
        }

        private static double lerp(double min, double max, double delta) {
            return min + (max - min) * delta;
        }

        public Face trim(double factor) {
            double offsetX = (maxX - minX) * factor;
            double offsetY = (maxY - minY) * factor;
            double offsetZ = (maxZ - minZ) * factor;

            double newMinX = minX + offsetX;
            double newMaxX = maxX - offsetX;
            double newMinY = minY + offsetY;
            double newMaxY = maxY - offsetY;
            double newMinZ = minZ + offsetZ;
            double newMaxZ = maxZ - offsetZ;

            if (newMinX > newMaxX) newMinX = newMaxX = (minX + maxX) * 0.5;
            if (newMinY > newMaxY) newMinY = newMaxY = (minY + maxY) * 0.5;
            if (newMinZ > newMaxZ) newMinZ = newMaxZ = (minZ + maxZ) * 0.5;

            return new Face(newMinX, newMaxX, newMinY, newMaxY, newMinZ, newMaxZ);
        }

        public Face truncateMinY(double minAllowedY) {
            double y = Math.max(minY, minAllowedY);
            if (y > maxY) return null;
            return new Face(minX, maxX, y, maxY, minZ, maxZ);
        }

        public Vec3 center() {
            return new Vec3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
        }

        public Face clamp(AABB box) {
            double clampedMinX = ScaffoldFacePositionFactory.clamp(minX, box.minX, box.maxX);
            double clampedMaxX = ScaffoldFacePositionFactory.clamp(maxX, box.minX, box.maxX);
            double clampedMinY = ScaffoldFacePositionFactory.clamp(minY, box.minY, box.maxY);
            double clampedMaxY = ScaffoldFacePositionFactory.clamp(maxY, box.minY, box.maxY);
            double clampedMinZ = ScaffoldFacePositionFactory.clamp(minZ, box.minZ, box.maxZ);
            double clampedMaxZ = ScaffoldFacePositionFactory.clamp(maxZ, box.minZ, box.maxZ);
            return new Face(
                    Math.min(clampedMinX, clampedMaxX),
                    Math.max(clampedMinX, clampedMaxX),
                    Math.min(clampedMinY, clampedMaxY),
                    Math.max(clampedMinY, clampedMaxY),
                    Math.min(clampedMinZ, clampedMaxZ),
                    Math.max(clampedMinZ, clampedMaxZ)
            );
        }

        private Vec3 random(double randomNumber) {
            double t = (Mth.clamp(randomNumber, -1.0, 1.0) + 1.0) * 0.5;
            double inv = 1.0 - t;
            return new Vec3(
                    minX * inv + maxX * t,
                    minY * t + maxY * inv,
                    minZ * inv + maxZ * t
            );
        }

        private List<Vec3> samplePoints() {
            List<Vec3> points = new ArrayList<>(27);
            double[] samples = {0.0, 0.25, 0.5, 0.75, 1.0};

            for (double sx : samples) {
                for (double sy : samples) {
                    for (double sz : samples) {
                        double x = lerp(minX, maxX, sx);
                        double y = lerp(minY, maxY, sy);
                        double z = lerp(minZ, maxZ, sz);
                        points.add(new Vec3(x, y, z));
                    }
                }
            }

            return points;
        }

        private List<Vec3> edgePoints() {
            List<Vec3> points = new ArrayList<>(8);
            double[] xs = {minX, maxX};
            double[] ys = {minY, maxY};
            double[] zs = {minZ, maxZ};
            for (double x : xs) {
                for (double y : ys) {
                    for (double z : zs) {
                        points.add(new Vec3(x, y, z));
                    }
                }
            }
            return points;
        }

        public double area() {
            double dx = maxX - minX;
            double dy = maxY - minY;
            double dz = maxZ - minZ;
            if (dx <= 1.0E-7) return Math.max(dy, 0.0) * Math.max(dz, 0.0);
            if (dy <= 1.0E-7) return Math.max(dx, 0.0) * Math.max(dz, 0.0);
            if (dz <= 1.0E-7) return Math.max(dx, 0.0) * Math.max(dy, 0.0);
            return 0.0;
        }

        public double minY() {
            return minY;
        }

        public double maxY() {
            return maxY;
        }
    }

    private record LineSegment(Vec3 start, Vec3 end) {
    }

    private record YawCandidate(Vec3 point, float diff) {
    }
}
