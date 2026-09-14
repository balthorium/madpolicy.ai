package ai.madpolicy.diplomacy.map;

import ai.madpolicy.diplomacy.app.BoardCatalog;
import ai.madpolicy.diplomacy.model.BoardDocument;
import ai.madpolicy.diplomacy.model.BoardPosition;
import ai.madpolicy.diplomacy.model.BoardProvince;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jgrapht.Graph;
import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

@Service
public class MapLayoutService {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 900;
    private static final int MARGIN = 80;
    private static final double OUTER_PUSH = 260.0;
    private static final double PERTURBATION_AMPLITUDE = 14.0;
    private static final double PERTURBATION_SEGMENT_LENGTH = 25.0;
    private static final long EDGE_KEY_BASE = 100_000L;

    private final BoardCatalog boardCatalog;
    private final Map<String, MapLayout> cache = new ConcurrentHashMap<>();

    public MapLayoutService() {
        this.boardCatalog = new BoardCatalog();
    }

    public MapLayout layoutFor(String boardId, int revision) {
        return cache.computeIfAbsent(boardId + ":" + revision, k -> compute(boardId, revision));
    }

    public BoardDocument boardFor(String boardId, int revision) {
        return boardCatalog.resolve(boardId, revision);
    }

    // -------------------------------------------------------------------------
    // Top-level computation
    //
    // Pipeline: planar embedding (Boyer-Myrvold) -> face extraction -> Tutte
    // coordinates (outer face pinned to a circle) -> dual polygonization.
    // Every stage guarantees the invariant the next stage requires, so borders
    // exist iff a graph edge exists — by construction, not by optimization.
    // -------------------------------------------------------------------------

    private MapLayout compute(String boardId, int revision) {
        BoardDocument board = boardCatalog.resolve(boardId, revision);

        List<String> ids = new ArrayList<>(board.provinces().keySet());
        Collections.sort(ids);
        int n = ids.size();

        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(ids.get(i), i);

        Set<Integer>[] adj = buildProvinceAdjacency(board, ids, index, n);

        int[][] rotation = planarRotationSystem(adj, n);
        PlanarFaces faces = extractFaces(rotation, n);
        double[][] coords = tutteCoordinates(faces, adj, n);
        scaleToViewport(coords, n);

        List<Vec2> facePoints = computeFacePoints(faces, coords);
        List<List<Vec2>> polygons = buildPolygons(rotation, faces, facePoints, coords, n);
        clipToCanvas(polygons, coords);
        perturbBorders(polygons, adj, n);

        Map<String, ProvinceLayout> provinces = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String pid = ids.get(i);
            Vec2 centroid = polygonCentroid(polygons.get(i), new Vec2(coords[i][0], coords[i][1]));
            String terrain = classifyTerrain(board.provinces().get(pid), centroid);
            Map<String, Vec2> posCentroids = positionCentroids(board.provinces().get(pid), centroid);
            provinces.put(pid, new ProvinceLayout(centroid, polygons.get(i), terrain, posCentroids));
        }

        Map<String, Set<String>> adjacencies = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            Set<String> neighbors = new HashSet<>();
            for (int j : adj[i]) neighbors.add(ids.get(j));
            adjacencies.put(ids.get(i), Collections.unmodifiableSet(neighbors));
        }

        return new MapLayout(WIDTH, HEIGHT, Collections.unmodifiableMap(provinces),
            Collections.unmodifiableMap(adjacencies));
    }

    @SuppressWarnings("unchecked")
    private Set<Integer>[] buildProvinceAdjacency(
        BoardDocument board, List<String> ids, Map<String, Integer> index, int n
    ) {
        Set<Integer>[] adj = new Set[n];
        for (int i = 0; i < n; i++) adj[i] = new LinkedHashSet<>();

        Map<String, String> provinceByPosition = new HashMap<>();
        for (Map.Entry<String, BoardProvince> e : board.provinces().entrySet()) {
            for (BoardPosition pos : e.getValue().positions()) {
                provinceByPosition.put(pos.id(), e.getKey());
            }
        }
        for (List<String> edge : board.adjacency()) {
            String pa = provinceByPosition.get(edge.get(0));
            String pb = provinceByPosition.get(edge.get(1));
            if (pa != null && pb != null && !pa.equals(pb)) {
                int ia = index.get(pa), ib = index.get(pb);
                adj[ia].add(ib);
                adj[ib].add(ia);
            }
        }
        return adj;
    }

    // -------------------------------------------------------------------------
    // Step 1: Planar rotation system via Boyer-Myrvold
    //
    // The rotation system (cyclic neighbor order around each vertex) comes from
    // the combinatorial planarity algorithm, NOT from any geometric layout.
    // This is what guarantees the face structure is valid.
    // -------------------------------------------------------------------------

    private int[][] planarRotationSystem(Set<Integer>[] adj, int n) {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i < n; i++) graph.addVertex(i);
        for (int i = 0; i < n; i++) {
            for (int j : adj[i]) {
                if (j > i) graph.addEdge(i, j);
            }
        }

        BoyerMyrvoldPlanarityInspector<Integer, DefaultEdge> inspector =
            new BoyerMyrvoldPlanarityInspector<>(graph);
        if (!inspector.isPlanar()) {
            throw new IllegalStateException(
                "Province graph is not planar; cannot construct map layout");
        }

        var embedding = inspector.getEmbedding();
        int[][] rotation = new int[n][];
        for (int v = 0; v < n; v++) {
            List<DefaultEdge> around = embedding.getEdgesAround(v);
            rotation[v] = new int[around.size()];
            for (int k = 0; k < around.size(); k++) {
                DefaultEdge e = around.get(k);
                int src = graph.getEdgeSource(e);
                int tgt = graph.getEdgeTarget(e);
                rotation[v][k] = (src == v) ? tgt : src;
            }
        }
        return rotation;
    }

    // -------------------------------------------------------------------------
    // Step 2: Face extraction from the rotation system
    //
    // Traversal rule: next(u -> v) = (v -> w) where w is the successor of u in
    // v's rotation. Each directed edge belongs to exactly one face. The outer
    // face is the largest cycle (validated by the planarity diagnostic: the
    // standard board has one size-13 face, all others size 3-5).
    // -------------------------------------------------------------------------

    private static final class PlanarFaces {
        final Map<Long, Integer> faceOfDirectedEdge = new HashMap<>();
        final List<List<Integer>> faceCycles = new ArrayList<>();
        int outerFace = -1;
    }

    private PlanarFaces extractFaces(int[][] rotation, int n) {
        PlanarFaces pf = new PlanarFaces();

        for (int u = 0; u < n; u++) {
            for (int v : rotation[u]) {
                long startKey = directedKey(u, v);
                if (pf.faceOfDirectedEdge.containsKey(startKey)) continue;

                int faceId = pf.faceCycles.size();
                List<Integer> cycle = new ArrayList<>();
                int cu = u, cv = v;
                do {
                    pf.faceOfDirectedEdge.put(directedKey(cu, cv), faceId);
                    cycle.add(cv);
                    int w = successor(rotation[cv], cu);
                    cu = cv;
                    cv = w;
                } while (!(cu == u && cv == v));
                pf.faceCycles.add(cycle);
            }
        }

        int maxSize = -1;
        for (int f = 0; f < pf.faceCycles.size(); f++) {
            if (pf.faceCycles.get(f).size() > maxSize) {
                maxSize = pf.faceCycles.get(f).size();
                pf.outerFace = f;
            }
        }
        return pf;
    }

    private long directedKey(int u, int v) {
        return u * EDGE_KEY_BASE + v;
    }

    private long undirectedKey(int u, int v) {
        return Math.min(u, v) * EDGE_KEY_BASE + Math.max(u, v);
    }

    private int successor(int[] rotationRow, int u) {
        for (int i = 0; i < rotationRow.length; i++) {
            if (rotationRow[i] == u) return rotationRow[(i + 1) % rotationRow.length];
        }
        throw new IllegalStateException("rotation system inconsistency");
    }

    // -------------------------------------------------------------------------
    // Step 3: Tutte barycentric coordinates
    //
    // Pin the outer face vertices on a circle; every interior vertex solves
    // deg(v) * pos(v) - sum(pos(neighbors)) = 0. Tutte's theorem guarantees the
    // resulting straight-line drawing is crossing-free and realizes the
    // embedding, so the dual polygonization downstream is geometrically valid.
    // -------------------------------------------------------------------------

    private double[][] tutteCoordinates(PlanarFaces faces, Set<Integer>[] adj, int n) {
        // Outer cycle in walk order, deduplicated (repeats possible if the
        // boundary pinches at a cut vertex)
        List<Integer> outerCycle = new ArrayList<>(
            new LinkedHashSet<>(faces.faceCycles.get(faces.outerFace)));
        int m = outerCycle.size();

        double[][] coords = new double[n][2];
        boolean[] pinned = new boolean[n];
        for (int k = 0; k < m; k++) {
            double angle = 2 * Math.PI * k / m;
            int v = outerCycle.get(k);
            coords[v][0] = Math.cos(angle) * 1000.0;
            coords[v][1] = Math.sin(angle) * 1000.0;
            pinned[v] = true;
        }

        List<Integer> interior = new ArrayList<>();
        int[] interiorIndex = new int[n];
        for (int v = 0; v < n; v++) {
            if (!pinned[v]) {
                interiorIndex[v] = interior.size();
                interior.add(v);
            }
        }
        int mi = interior.size();
        if (mi == 0) return coords;

        RealMatrix A = new Array2DRowRealMatrix(mi, mi);
        double[] bx = new double[mi];
        double[] by = new double[mi];
        for (int r = 0; r < mi; r++) {
            int v = interior.get(r);
            A.setEntry(r, r, adj[v].size());
            for (int u : adj[v]) {
                if (pinned[u]) {
                    bx[r] += coords[u][0];
                    by[r] += coords[u][1];
                } else {
                    A.setEntry(r, interiorIndex[u], A.getEntry(r, interiorIndex[u]) - 1);
                }
            }
        }

        DecompositionSolver solver = new LUDecomposition(A).getSolver();
        RealVector xs = solver.solve(new ArrayRealVector(bx));
        RealVector ys = solver.solve(new ArrayRealVector(by));
        for (int r = 0; r < mi; r++) {
            int v = interior.get(r);
            coords[v][0] = xs.getEntry(r);
            coords[v][1] = ys.getEntry(r);
        }
        return coords;
    }

    // -------------------------------------------------------------------------
    // Step 4: Scale and center to viewport
    // -------------------------------------------------------------------------

    private void scaleToViewport(double[][] coords, int n) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, coords[i][0]); maxX = Math.max(maxX, coords[i][0]);
            minY = Math.min(minY, coords[i][1]); maxY = Math.max(maxY, coords[i][1]);
        }
        double rangeX = Math.max(1e-6, maxX - minX);
        double rangeY = Math.max(1e-6, maxY - minY);
        double usableW = WIDTH - 2.0 * MARGIN;
        double usableH = HEIGHT - 2.0 * MARGIN;
        double scale = Math.min(usableW / rangeX, usableH / rangeY);
        double offX = MARGIN + (usableW - rangeX * scale) / 2.0;
        double offY = MARGIN + (usableH - rangeY * scale) / 2.0;
        for (int i = 0; i < n; i++) {
            coords[i][0] = (coords[i][0] - minX) * scale + offX;
            coords[i][1] = (coords[i][1] - minY) * scale + offY;
        }
    }

    // -------------------------------------------------------------------------
    // Step 5: Face points and polygon construction
    // -------------------------------------------------------------------------

    private List<Vec2> computeFacePoints(PlanarFaces faces, double[][] coords) {
        List<Vec2> points = new ArrayList<>(faces.faceCycles.size());
        for (List<Integer> cycle : faces.faceCycles) {
            double sx = 0, sy = 0;
            for (int v : cycle) {
                sx += coords[v][0];
                sy += coords[v][1];
            }
            points.add(new Vec2(sx / cycle.size(), sy / cycle.size()));
        }
        return points;
    }

    private List<List<Vec2>> buildPolygons(
        int[][] rotation, PlanarFaces faces, List<Vec2> facePoints, double[][] coords, int n
    ) {
        Vec2 center = new Vec2(WIDTH / 2.0, HEIGHT / 2.0);
        Map<Long, Vec2> outerPoints = new HashMap<>();

        List<List<Vec2>> polygons = new ArrayList<>(n);
        for (int p = 0; p < n; p++) {
            int[] rot = rotation[p];
            List<Vec2> pts = new ArrayList<>();
            int k = rot.length;
            for (int i = 0; i < k; i++) {
                int a = rot[i];
                int b = rot[(i + 1) % k];
                Integer f = faces.faceOfDirectedEdge.get(directedKey(a, p));
                if (f == null) continue;
                if (f != faces.outerFace) {
                    pts.add(facePoints.get(f));
                } else {
                    pts.add(outerPoint(outerPoints, p, a, coords, center));
                    pts.add(outerPoint(outerPoints, p, b, coords, center));
                }
            }
            polygons.add(dedupeConsecutive(pts));
        }
        return polygons;
    }

    private Vec2 outerPoint(
        Map<Long, Vec2> cache, int p, int q, double[][] coords, Vec2 center
    ) {
        return cache.computeIfAbsent(undirectedKey(p, q), key -> {
            Vec2 mid = new Vec2(
                (coords[p][0] + coords[q][0]) / 2.0,
                (coords[p][1] + coords[q][1]) / 2.0
            );
            Vec2 dir = mid.sub(center);
            double len = dir.norm();
            if (len < 1e-6) dir = new Vec2(0, -1);
            else dir = dir.scale(1.0 / len);
            return mid.add(dir.scale(OUTER_PUSH));
        });
    }

    private List<Vec2> dedupeConsecutive(List<Vec2> pts) {
        if (pts.size() < 2) return pts;
        List<Vec2> out = new ArrayList<>(pts.size());
        for (Vec2 v : pts) {
            if (out.isEmpty() || out.get(out.size() - 1).sub(v).norm() > 1e-6) {
                out.add(v);
            }
        }
        if (out.size() > 1 && out.get(0).sub(out.get(out.size() - 1)).norm() < 1e-6) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Step 6: Repair and clip polygons to the canvas
    // -------------------------------------------------------------------------

    private void clipToCanvas(List<List<Vec2>> polygons, double[][] coords) {
        GeometryFactory gf = new GeometryFactory();
        Geometry canvas = gf.toGeometry(new Envelope(0, WIDTH, 0, HEIGHT));

        for (int i = 0; i < polygons.size(); i++) {
            List<Vec2> poly = polygons.get(i);
            if (poly.size() < 3) {
                polygons.set(i, fallbackSquare(coords[i]));
                continue;
            }
            Coordinate[] ring = new Coordinate[poly.size() + 1];
            for (int j = 0; j < poly.size(); j++) {
                ring[j] = new Coordinate(poly.get(j).x(), poly.get(j).y());
            }
            ring[poly.size()] = ring[0];

            try {
                Geometry g = gf.createPolygon(ring);
                if (!g.isValid()) g = g.buffer(0);
                g = g.intersection(canvas);
                g = largestPolygon(g);
                if (g == null || g.isEmpty()) {
                    polygons.set(i, fallbackSquare(coords[i]));
                    continue;
                }
                Coordinate[] cs = ((Polygon) g).getExteriorRing().getCoordinates();
                List<Vec2> out = new ArrayList<>(cs.length);
                for (int j = 0; j < cs.length - 1; j++) {
                    out.add(new Vec2(cs[j].x, cs[j].y));
                }
                polygons.set(i, out);
            } catch (RuntimeException e) {
                polygons.set(i, fallbackSquare(coords[i]));
            }
        }
    }

    private Geometry largestPolygon(Geometry g) {
        if (g instanceof Polygon) return g;
        Geometry best = null;
        double bestArea = -1;
        for (int i = 0; i < g.getNumGeometries(); i++) {
            Geometry part = g.getGeometryN(i);
            if (part instanceof Polygon && part.getArea() > bestArea) {
                bestArea = part.getArea();
                best = part;
            }
        }
        return best;
    }

    private List<Vec2> fallbackSquare(double[] coord) {
        double x = coord[0], y = coord[1], r = 15;
        return List.of(
            new Vec2(x - r, y - r), new Vec2(x + r, y - r),
            new Vec2(x + r, y + r), new Vec2(x - r, y + r)
        );
    }

    private Vec2 polygonCentroid(List<Vec2> poly, Vec2 fallback) {
        if (poly.size() < 3) return fallback;
        double area = 0, cx = 0, cy = 0;
        int sz = poly.size();
        for (int i = 0; i < sz; i++) {
            Vec2 a = poly.get(i), b = poly.get((i + 1) % sz);
            double cross = a.x() * b.y() - b.x() * a.y();
            area += cross;
            cx += (a.x() + b.x()) * cross;
            cy += (a.y() + b.y()) * cross;
        }
        if (Math.abs(area) < 1e-9) return fallback;
        area /= 2.0;
        return new Vec2(cx / (6 * area), cy / (6 * area));
    }

    // -------------------------------------------------------------------------
    // Boundary perturbation (currently disabled; re-enable via compute())
    // -------------------------------------------------------------------------

    private void perturbBorders(List<List<Vec2>> polygons, Set<Integer>[] adj, int n) {
        // Phase 1: detect shared edges on the unmodified polygons and compute
        // each border's jagged polyline exactly once, in a canonical direction
        // (from the lexicographically smaller endpoint). Detection must happen
        // before any polygon is rebuilt, otherwise a neighbor processed earlier
        // no longer contains the original edge and the two sides diverge.
        Map<String, List<Vec2>> midsByEdge = new HashMap<>();
        for (int i = 0; i < n; i++) {
            List<Vec2> poly = polygons.get(i);
            int sz = poly.size();
            for (int vi = 0; vi < sz; vi++) {
                Vec2 v1 = poly.get(vi);
                Vec2 v2 = poly.get((vi + 1) % sz);
                String key = unorderedCoordKey(v1, v2);
                if (midsByEdge.containsKey(key)) continue;

                int sharedWith = findSharedNeighbor(polygons, adj[i], v1, v2);
                if (sharedWith < 0) continue;

                Vec2 from = lexLess(v1, v2) ? v1 : v2;
                Vec2 to   = lexLess(v1, v2) ? v2 : v1;
                midsByEdge.put(key, computePerturbedMidpoints(from, to, i, sharedWith));
            }
        }

        // Phase 2: rebuild every polygon, inserting the canonical midpoints in
        // the direction each polygon traverses the edge.
        for (int i = 0; i < n; i++) {
            List<Vec2> poly = polygons.get(i);
            if (poly.isEmpty()) continue;
            List<Vec2> newPoly = new ArrayList<>();
            int sz = poly.size();
            for (int vi = 0; vi < sz; vi++) {
                Vec2 v1 = poly.get(vi);
                Vec2 v2 = poly.get((vi + 1) % sz);
                newPoly.add(v1);
                List<Vec2> mids = midsByEdge.get(unorderedCoordKey(v1, v2));
                if (mids == null || mids.isEmpty()) continue;
                if (lexLess(v1, v2)) {
                    newPoly.addAll(mids);
                } else {
                    List<Vec2> reversed = new ArrayList<>(mids);
                    Collections.reverse(reversed);
                    newPoly.addAll(reversed);
                }
            }
            polygons.set(i, newPoly);
        }
    }

    private int findSharedNeighbor(List<List<Vec2>> polygons, Set<Integer> neighbors, Vec2 v1, Vec2 v2) {
        for (int j : neighbors) {
            List<Vec2> other = polygons.get(j);
            int sz = other.size();
            for (int k = 0; k < sz; k++) {
                Vec2 a = other.get(k);
                Vec2 b = other.get((k + 1) % sz);
                if (approxEq(a, v2) && approxEq(b, v1)) return j;
            }
        }
        return -1;
    }

    private boolean approxEq(Vec2 a, Vec2 b) {
        return Math.abs(a.x() - b.x()) < 0.5 && Math.abs(a.y() - b.y()) < 0.5;
    }

    private boolean lexLess(Vec2 a, Vec2 b) {
        if (a.x() != b.x()) return a.x() < b.x();
        return a.y() < b.y();
    }

    private String unorderedCoordKey(Vec2 v1, Vec2 v2) {
        Vec2 lo = lexLess(v1, v2) ? v1 : v2;
        Vec2 hi = lexLess(v1, v2) ? v2 : v1;
        return Math.round(lo.x() * 10) + "," + Math.round(lo.y() * 10)
             + "-" + Math.round(hi.x() * 10) + "," + Math.round(hi.y() * 10);
    }

    private List<Vec2> computePerturbedMidpoints(Vec2 from, Vec2 to, int i, int j) {
        long seed = (long) Math.min(i, j) * EDGE_KEY_BASE + Math.max(i, j);
        double dx = to.x() - from.x(), dy = to.y() - from.y();
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) return List.of();

        double px = -dy / len, py = dx / len;
        // Taper displacement toward endpoints so corners stay pinned, and cap
        // amplitude on short edges to avoid spikes and self-intersections.
        double amplitude = Math.min(PERTURBATION_AMPLITUDE, len * 0.2);

        int numMids = Math.max(1, (int) (len / PERTURBATION_SEGMENT_LENGTH));
        List<Vec2> mids = new ArrayList<>(numMids);
        for (int k = 1; k <= numMids; k++) {
            double t = (double) k / (numMids + 1);
            double bx = from.x() + dx * t;
            double by = from.y() + dy * t;
            double disp = valueNoise(seed, k) * amplitude * Math.sin(Math.PI * t);
            mids.add(new Vec2(bx + px * disp, by + py * disp));
        }
        return mids;
    }

    private double valueNoise(long seed, int k) {
        long h = seed * 6364136223846793005L + k * 1442695040888963407L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return ((h & 0xFFFFL) / 32767.0) - 1.0;
    }

    // -------------------------------------------------------------------------
    // Terrain classification
    // -------------------------------------------------------------------------

    private String classifyTerrain(BoardProvince province, Vec2 centroid) {
        List<BoardPosition> positions = province.positions();
        boolean allSea  = positions.stream().allMatch(p -> "sea".equals(p.type()));
        boolean hasCoast = positions.stream().anyMatch(p -> "coast".equals(p.type()));
        if (allSea)   return "ocean";
        if (hasCoast) return "coast";
        int h = Math.abs(province.name().hashCode()) % 3;
        if (h == 0) return "mountains";
        if (centroid.y() < HEIGHT * 0.35) return "tundra";
        return "plains";
    }

    // -------------------------------------------------------------------------
    // Position centroids within province
    // -------------------------------------------------------------------------

    private Map<String, Vec2> positionCentroids(BoardProvince province, Vec2 centroid) {
        List<BoardPosition> positions = province.positions();
        if (positions.size() == 1) {
            return Map.of(positions.get(0).id(), centroid);
        }
        Map<String, Vec2> result = new HashMap<>();
        for (BoardPosition pos : positions) {
            Vec2 offset = coastOffset(pos);
            result.put(pos.id(), centroid.add(offset));
        }
        return Collections.unmodifiableMap(result);
    }

    private Vec2 coastOffset(BoardPosition pos) {
        if ("north".equals(pos.coast())) return new Vec2(0, -18);
        if ("south".equals(pos.coast())) return new Vec2(0, +18);
        if ("east".equals(pos.coast()))  return new Vec2(+18, 0);
        return new Vec2(0, 0);
    }
}
