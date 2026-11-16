package im.yaHustler.render.coliseimvisual;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class TrailHistory {
    private final List<TrailHistory.TrailPoint> points = new ArrayList<>();
    private TrailHistory.TrailPoint latestPoint;
    private double sampleRemainder;

    public void clearHistory() {
        this.points.clear();
        this.latestPoint = null;
        this.sampleRemainder = 0.0;
    }

    public void addSample(Vec3 position, double time, int maxPoints) {
        if (this.latestPoint != null && (time < this.latestPoint.time() || time - this.latestPoint.time() >= 0.8 || position.distanceToSqr(this.latestPoint.position()) > 256.0)) {
            this.clearHistory();
        }

        TrailHistory.TrailPoint sample = new TrailHistory.TrailPoint(position, time);
        if (this.latestPoint == null) {
            this.latestPoint = sample;
            this.points.add(sample);
        } else {
            double sampleSteps = Math.max(position.distanceTo(this.latestPoint.position()) / 0.03, (time - this.latestPoint.time()) / 0.05);
            if (sampleSteps > 0.0) {
                double accumulatedSteps = this.sampleRemainder + sampleSteps;
                int wholeSteps = (int)Math.floor(accumulatedSteps);

                for (int step = 1; step <= wholeSteps; step++) {
                    this.points.add(this.latestPoint.lerp(sample, (step - this.sampleRemainder) / sampleSteps));
                }

                this.sampleRemainder = accumulatedSteps - wholeSteps;
            }

            this.latestPoint = sample;
            int discardCount = 0;

            while (discardCount + 1 < this.points.size() && this.points.get(discardCount + 1).time() <= time - 0.8) {
                discardCount++;
            }

            discardCount = Math.max(discardCount, this.points.size() - maxPoints - 1);
            if (discardCount > 0) {
                this.points.subList(0, discardCount).clear();
            }
        }
    }

    public TrailHistory.TrailSnapshot sampleTrail(int maxPoints) {
        if (this.latestPoint == null) {
            return new TrailHistory.TrailSnapshot(List.of(), 0.0);
        }

        double expiredFraction = 0.0;
        if (this.points.size() > 1 && this.points.getFirst().time() < this.latestPoint.time() - 0.8) {
            TrailHistory.TrailPoint oldest = this.points.getFirst();
            TrailHistory.TrailPoint next = this.points.get(1);
            expiredFraction = Math.clamp((this.latestPoint.time() - 0.8 - oldest.time()) / (next.time() - oldest.time()), 0.0, 1.0);
        }

        double length = Math.min(maxPoints - 1, this.points.size() - 1 + this.sampleRemainder - expiredFraction);
        int pointCount = Math.min(this.points.size(), (int)length + 1);
        List<TrailPoint> sampled = new ArrayList<>(pointCount);
        sampled.add(this.latestPoint);

        for (int offset = 1; offset < pointCount; offset++) {
            int index = this.points.size() - 1 - offset;
            sampled.add(this.points.get(index).lerp(this.points.get(index + 1), this.sampleRemainder));
        }

        return new TrailHistory.TrailSnapshot(sampled, length + 1.0);
    }

    public record TrailSnapshot(List<TrailHistory.TrailPoint> points, double length) {
    }

    public record TrailPoint(Vec3 position, double time) {
        public TrailHistory.TrailPoint lerp(TrailHistory.TrailPoint next, double amount) {
            return new TrailHistory.TrailPoint(this.position.lerp(next.position(), amount), this.time + (next.time - this.time) * amount);
        }
    }
}
