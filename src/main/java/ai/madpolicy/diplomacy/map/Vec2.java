package ai.madpolicy.diplomacy.map;

public record Vec2(double x, double y) {
    public Vec2 add(Vec2 o) { return new Vec2(x + o.x, y + o.y); }
    public Vec2 sub(Vec2 o) { return new Vec2(x - o.x, y - o.y); }
    public Vec2 scale(double s) { return new Vec2(x * s, y * s); }
    public double norm() { return Math.sqrt(x * x + y * y); }
    public Vec2 lerp(Vec2 o, double t) { return new Vec2(x + (o.x - x) * t, y + (o.y - y) * t); }
    public Vec2 perp() { return new Vec2(-y, x); }
}
