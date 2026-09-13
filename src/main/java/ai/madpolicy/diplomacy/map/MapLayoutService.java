package ai.madpolicy.diplomacy.map;

import ai.madpolicy.diplomacy.app.BoardCatalog;
import ai.madpolicy.diplomacy.model.BoardDocument;
import ai.madpolicy.diplomacy.model.BoardPosition;
import ai.madpolicy.diplomacy.model.BoardProvince;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;
import org.springframework.stereotype.Service;

@Service
public class MapLayoutService {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 900;
    private static final int MARGIN = 80;
    private static final int FORCE_ITERATIONS = 150;
    private static final double PERTURBATION_AMPLITUDE = 14.0;
    private static final double PERTURBATION_SEGMENT_LENGTH = 25.0;

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
    // -------------------------------------------------------------------------

    private MapLayout compute(String boardId, int revision) {
        BoardDocument board = boardCatalog.resolve(boardId, revision);

        List<String> ids = new ArrayList<>(board.provinces().keySet());
        Collections.sort(ids);
        int n = ids.size();

        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(ids.get(i), i);

        // Build province-level adjacency from position-level pairs
        @SuppressWarnings("unchecked")
        Set<Integer>[] adj = new Set[n];
        for (int i = 0; i < n; i++) adj[i] = new HashSet<>();

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

        double[][] D = bfsDistanceMatrix(n, adj);
        double[][] coords = mdsCoordinates(D, n);
        separateSeaLand(board, ids, coords);
        forceDirected(coords, adj, n);
        scaleToViewport(coords, n);

        List<List<Vec2>> polygons = voronoiPolygons(coords, n);
        perturbBorders(polygons, adj, n);

        Map<String, ProvinceLayout> provinces = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String pid = ids.get(i);
            Vec2 centroid = new Vec2(coords[i][0], coords[i][1]);
            String terrain = classifyTerrain(board.provinces().get(pid), centroid);
            Map<String, Vec2> posCentroids = positionCentroids(board.provinces().get(pid), centroid);
            provinces.put(pid, new ProvinceLayout(centroid, polygons.get(i), terrain, posCentroids));
        }

        return new MapLayout(WIDTH, HEIGHT, Collections.unmodifiableMap(provinces));
    }

    // -------------------------------------------------------------------------
    // Step 1: BFS all-pairs distance matrix
    // -------------------------------------------------------------------------

    private double[][] bfsDistanceMatrix(int n, Set<Integer>[] adj) {
        double[][] D = new double[n][n];
        int[] dist = new int[n];
        for (int src = 0; src < n; src++) {
            Arrays.fill(dist, -1);
            dist[src] = 0;
            int[] queue = new int[n];
            int head = 0, tail = 0;
            queue[tail++] = src;
            while (head < tail) {
                int cur = queue[head++];
                for (int nb : adj[cur]) {
                    if (dist[nb] == -1) {
                        dist[nb] = dist[cur] + 1;
                        queue[tail++] = nb;
                    }
                }
            }
            for (int j = 0; j < n; j++) {
                D[src][j] = dist[j] == -1 ? n : dist[j];
            }
        }
        return D;
    }

    // -------------------------------------------------------------------------
    // Step 2: Classical MDS via Commons Math EigenDecomposition
    // -------------------------------------------------------------------------

    private double[][] mdsCoordinates(double[][] D, int n) {
        // Double-center D²
        double[][] Dsq = new double[n][n];
        double[] rowMean = new double[n];
        double[] colMean = new double[n];
        double grandMean = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Dsq[i][j] = D[i][j] * D[i][j];
                rowMean[i] += Dsq[i][j];
                colMean[j] += Dsq[i][j];
                grandMean += Dsq[i][j];
            }
        }
        for (int i = 0; i < n; i++) rowMean[i] /= n;
        for (int j = 0; j < n; j++) colMean[j] /= n;
        grandMean /= (double) (n * n);

        double[][] B = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                B[i][j] = -0.5 * (Dsq[i][j] - rowMean[i] - colMean[j] + grandMean);
            }
        }

        EigenDecomposition eigen = new EigenDecomposition(new Array2DRowRealMatrix(B));

        // Find indices of top 2 real eigenvalues
        int n0 = largestEigenvalueIndex(eigen, n, -1);
        int n1 = largestEigenvalueIndex(eigen, n, n0);

        double s0 = Math.sqrt(Math.max(0, eigen.getRealEigenvalue(n0)));
        double s1 = Math.sqrt(Math.max(0, eigen.getRealEigenvalue(n1)));

        double[][] coords = new double[n][2];
        for (int i = 0; i < n; i++) {
            coords[i][0] = eigen.getEigenvector(n0).getEntry(i) * s0;
            coords[i][1] = eigen.getEigenvector(n1).getEntry(i) * s1;
        }
        return coords;
    }

    private int largestEigenvalueIndex(EigenDecomposition eigen, int n, int exclude) {
        int best = -1;
        double bestVal = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            if (i == exclude) continue;
            double val = eigen.getRealEigenvalue(i);
            if (val > bestVal) { bestVal = val; best = i; }
        }
        return best;
    }

    // -------------------------------------------------------------------------
    // Step 3: Sea/land centroid separation
    // -------------------------------------------------------------------------

    private void separateSeaLand(BoardDocument board, List<String> ids, double[][] coords) {
        int n = ids.size();
        boolean[] isSea = new boolean[n];
        for (int i = 0; i < n; i++) {
            isSea[i] = board.provinces().get(ids.get(i)).positions().stream()
                .allMatch(p -> "sea".equals(p.type()));
        }

        double[] seaCx = {0, 0}, landCx = {0, 0};
        int seaCount = 0, landCount = 0;
        for (int i = 0; i < n; i++) {
            if (isSea[i]) { seaCx[0] += coords[i][0]; seaCx[1] += coords[i][1]; seaCount++; }
            else           { landCx[0] += coords[i][0]; landCx[1] += coords[i][1]; landCount++; }
        }
        if (seaCount  > 0) { seaCx[0]  /= seaCount;  seaCx[1]  /= seaCount; }
        if (landCount > 0) { landCx[0] /= landCount; landCx[1] /= landCount; }

        for (int i = 0; i < n; i++) {
            double[] target = isSea[i] ? seaCx : landCx;
            coords[i][0] += (target[0] - coords[i][0]) * 0.4;
            coords[i][1] += (target[1] - coords[i][1]) * 0.4;
        }
    }

    // -------------------------------------------------------------------------
    // Step 4: Fruchterman-Reingold force layout
    // -------------------------------------------------------------------------

    private void forceDirected(double[][] coords, Set<Integer>[] adj, int n) {
        double k = Math.sqrt((double) WIDTH * HEIGHT / n);
        double temperature = WIDTH * 0.15;
        double cooling = temperature / (FORCE_ITERATIONS + 1);
        double[][] disp = new double[n][2];

        for (int iter = 0; iter < FORCE_ITERATIONS; iter++) {
            for (int i = 0; i < n; i++) { disp[i][0] = 0; disp[i][1] = 0; }

            // Repulsive forces between all pairs
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double dx = coords[i][0] - coords[j][0];
                    double dy = coords[i][1] - coords[j][1];
                    double dist = Math.max(0.01, Math.sqrt(dx * dx + dy * dy));
                    double force = k * k / dist;
                    double fx = dx / dist * force;
                    double fy = dy / dist * force;
                    disp[i][0] += fx; disp[i][1] += fy;
                    disp[j][0] -= fx; disp[j][1] -= fy;
                }
            }

            // Attractive forces along edges
            for (int i = 0; i < n; i++) {
                for (int j : adj[i]) {
                    if (j <= i) continue;
                    double dx = coords[i][0] - coords[j][0];
                    double dy = coords[i][1] - coords[j][1];
                    double dist = Math.max(0.01, Math.sqrt(dx * dx + dy * dy));
                    double force = dist * dist / k;
                    double fx = dx / dist * force;
                    double fy = dy / dist * force;
                    disp[i][0] -= fx; disp[i][1] -= fy;
                    disp[j][0] += fx; disp[j][1] += fy;
                }
            }

            // Apply displacements, clamped to temperature
            for (int i = 0; i < n; i++) {
                double len = Math.sqrt(disp[i][0] * disp[i][0] + disp[i][1] * disp[i][1]);
                if (len > 0) {
                    double scale = Math.min(len, temperature) / len;
                    coords[i][0] += disp[i][0] * scale;
                    coords[i][1] += disp[i][1] * scale;
                }
            }
            temperature -= cooling;
        }
    }

    // -------------------------------------------------------------------------
    // Step 5: Scale and center to viewport
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
    // Step 6: Voronoi tessellation via JTS
    // -------------------------------------------------------------------------

    private List<List<Vec2>> voronoiPolygons(double[][] coords, int n) {
        GeometryFactory gf = new GeometryFactory();
        Point[] points = new Point[n];
        for (int i = 0; i < n; i++) {
            points[i] = gf.createPoint(new Coordinate(coords[i][0], coords[i][1]));
        }
        MultiPoint sites = gf.createMultiPoint(points);

        VoronoiDiagramBuilder vdb = new VoronoiDiagramBuilder();
        vdb.setSites(sites);
        vdb.setClipEnvelope(new Envelope(0, WIDTH, 0, HEIGHT));

        GeometryCollection diagram = (GeometryCollection) vdb.getDiagram(gf);

        // JTS returns one cell per site but not necessarily in input order.
        // Match each cell back to its site by finding the cell that contains the site point.
        List<List<Vec2>> result = new ArrayList<>(Collections.nCopies(n, null));
        for (int c = 0; c < diagram.getNumGeometries(); c++) {
            Geometry cell = diagram.getGeometryN(c);
            for (int i = 0; i < n; i++) {
                if (result.get(i) != null) continue;
                if (cell.contains(points[i]) || cell.distance(points[i]) < 1e-6) {
                    result.set(i, coordinatesToVec2(cell.getCoordinates()));
                    break;
                }
            }
        }
        // Fallback: any unmatched province gets an empty polygon
        for (int i = 0; i < n; i++) {
            if (result.get(i) == null) result.set(i, List.of());
        }
        return result;
    }

    private List<Vec2> coordinatesToVec2(Coordinate[] coords) {
        List<Vec2> pts = new ArrayList<>(coords.length);
        // JTS polygons close on themselves; skip the duplicate last point
        int end = coords.length;
        if (end > 1 && coords[0].equals2D(coords[end - 1])) end--;
        for (int i = 0; i < end; i++) pts.add(new Vec2(coords[i].x, coords[i].y));
        return pts;
    }

    // -------------------------------------------------------------------------
    // Step 7: Boundary perturbation
    // -------------------------------------------------------------------------

    private void perturbBorders(List<List<Vec2>> polygons, Set<Integer>[] adj, int n) {
        // Build canonical edge key → perturbed midpoints
        // Key: "minIdx:maxIdx:v1x,v1y:v2x,v2y" — identifies a specific edge shared by two provinces
        Map<String, List<Vec2>> perturbedEdges = new HashMap<>();

        for (int i = 0; i < n; i++) {
            List<Vec2> poly = polygons.get(i);
            if (poly.isEmpty()) continue;

            List<Vec2> newPoly = new ArrayList<>();
            int sz = poly.size();
            for (int vi = 0; vi < sz; vi++) {
                Vec2 v1 = poly.get(vi);
                Vec2 v2 = poly.get((vi + 1) % sz);
                newPoly.add(v1);

                // Find if v2 is also in an adjacent province's polygon (shared edge)
                int sharedWith = -1;
                for (int j : adj[i]) {
                    if (j == i) continue;
                    List<Vec2> other = polygons.get(j);
                    if (edgeExistsReversed(other, v1, v2)) {
                        sharedWith = j;
                        break;
                    }
                }

                if (sharedWith >= 0) {
                    String key = edgeKey(i, sharedWith, v1, v2);
                    final int provI = i, provJ = sharedWith;
                    List<Vec2> mids = perturbedEdges.computeIfAbsent(key,
                        k -> computePerturbedMidpoints(v1, v2, provI, provJ));
                    // Add midpoints in correct order (forward if i < sharedWith)
                    if (i < sharedWith) {
                        newPoly.addAll(mids);
                    } else {
                        List<Vec2> reversed = new ArrayList<>(mids);
                        Collections.reverse(reversed);
                        newPoly.addAll(reversed);
                    }
                }
            }
            polygons.set(i, newPoly);
        }
    }

    private boolean edgeExistsReversed(List<Vec2> poly, Vec2 v1, Vec2 v2) {
        int sz = poly.size();
        for (int i = 0; i < sz; i++) {
            Vec2 a = poly.get(i);
            Vec2 b = poly.get((i + 1) % sz);
            if (approxEq(a, v2) && approxEq(b, v1)) return true;
        }
        return false;
    }

    private boolean approxEq(Vec2 a, Vec2 b) {
        return Math.abs(a.x() - b.x()) < 0.5 && Math.abs(a.y() - b.y()) < 0.5;
    }

    private String edgeKey(int i, int j, Vec2 v1, Vec2 v2) {
        int lo = Math.min(i, j), hi = Math.max(i, j);
        // Canonical vertex order: always from the lower-index province's perspective
        Vec2 from = (i < j) ? v1 : v2;
        Vec2 to   = (i < j) ? v2 : v1;
        return lo + ":" + hi + ":" + Math.round(from.x() * 10) + "," + Math.round(from.y() * 10)
               + "-" + Math.round(to.x() * 10) + "," + Math.round(to.y() * 10);
    }

    private List<Vec2> computePerturbedMidpoints(Vec2 v1, Vec2 v2, int i, int j) {
        long seed = (long) Math.min(i, j) * 100_000L + Math.max(i, j);
        double dx = v2.x() - v1.x(), dy = v2.y() - v1.y();
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) return List.of();

        // Perpendicular unit vector
        double px = -dy / len, py = dx / len;

        int numMids = Math.max(1, (int) (len / PERTURBATION_SEGMENT_LENGTH));
        List<Vec2> mids = new ArrayList<>(numMids);
        for (int k = 1; k <= numMids; k++) {
            double t = (double) k / (numMids + 1);
            double bx = v1.x() + dx * t;
            double by = v1.y() + dy * t;
            double disp = valueNoise(seed, k) * PERTURBATION_AMPLITUDE;
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
    // Step 8: Terrain classification
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
    // Step 9: Position centroids within province
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
