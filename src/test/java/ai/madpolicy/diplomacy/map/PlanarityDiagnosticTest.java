package ai.madpolicy.diplomacy.map;

import ai.madpolicy.diplomacy.app.BoardCatalog;
import ai.madpolicy.diplomacy.model.BoardDocument;
import ai.madpolicy.diplomacy.model.BoardPosition;
import ai.madpolicy.diplomacy.model.BoardProvince;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;

class PlanarityDiagnosticTest {

    @Test
    void diagnoseStandardBoardPlanarity() {
        BoardDocument board = new BoardCatalog().resolve("standard", 1);

        // Build province-level graph from position-level adjacency
        Map<String, String> provinceByPosition = new HashMap<>();
        for (Map.Entry<String, BoardProvince> e : board.provinces().entrySet()) {
            for (BoardPosition pos : e.getValue().positions()) {
                provinceByPosition.put(pos.id(), e.getKey());
            }
        }

        Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        for (String pid : board.provinces().keySet()) {
            graph.addVertex(pid);
        }
        for (List<String> edge : board.adjacency()) {
            String pa = provinceByPosition.get(edge.get(0));
            String pb = provinceByPosition.get(edge.get(1));
            if (pa != null && pb != null && !pa.equals(pb)) {
                graph.addEdge(pa, pb);
            }
        }

        int v = graph.vertexSet().size();
        int e = graph.edgeSet().size();
        System.out.println("=== Planarity diagnostic: standard board ===");
        System.out.println("Provinces (V): " + v);
        System.out.println("Adjacencies (E): " + e);
        System.out.println("Planar limit (3V-6): " + (3 * v - 6));

        BoyerMyrvoldPlanarityInspector<String, DefaultEdge> inspector =
            new BoyerMyrvoldPlanarityInspector<>(graph);

        boolean planar = inspector.isPlanar();
        System.out.println("PLANAR: " + planar);

        if (planar) {
            var embedding = inspector.getEmbedding();
            // Count faces by walking directed edges via the rotation system
            Set<String> visitedDirected = new HashSet<>();
            int faces = 0;
            List<Integer> faceSizes = new ArrayList<>();
            for (String u : graph.vertexSet()) {
                for (DefaultEdge de : embedding.getEdgesAround(u)) {
                    String vTarget = otherEnd(graph, de, u);
                    if (visitedDirected.contains(u + "->" + vTarget)) continue;
                    int size = 0;
                    String cu = u, cv = vTarget;
                    do {
                        visitedDirected.add(cu + "->" + cv);
                        size++;
                        // next directed edge: at cv, find cu in rotation, take successor
                        List<DefaultEdge> rot = embedding.getEdgesAround(cv);
                        int idx = -1;
                        for (int i = 0; i < rot.size(); i++) {
                            if (otherEnd(graph, rot.get(i), cv).equals(cu)) { idx = i; break; }
                        }
                        String cw = otherEnd(graph, rot.get((idx + 1) % rot.size()), cv);
                        cu = cv;
                        cv = cw;
                    } while (!(cu.equals(u) && cv.equals(vTarget)));
                    faces++;
                    faceSizes.add(size);
                }
            }
            System.out.println("Faces (F): " + faces);
            System.out.println("Euler check V - E + F = " + (v - e + faces) + " (expected 2)");
            faceSizes.sort((a, b) -> b - a);
            System.out.println("Largest face sizes: " + faceSizes.subList(0, Math.min(5, faceSizes.size())));
            System.out.println("Face size distribution: min=" + faceSizes.get(faceSizes.size() - 1)
                + " max=" + faceSizes.get(0));
        } else {
            Graph<String, DefaultEdge> kuratowski = inspector.getKuratowskiSubdivision();
            System.out.println("NOT planar. Kuratowski subdivision (offending structure):");
            Set<String> involved = new HashSet<>();
            for (DefaultEdge de : kuratowski.edgeSet()) {
                String src = kuratowski.getEdgeSource(de);
                String tgt = kuratowski.getEdgeTarget(de);
                involved.add(src);
                involved.add(tgt);
                System.out.println("  " + src + " -- " + tgt);
            }
            System.out.println("Provinces involved: " + involved);
        }
    }

    private String otherEnd(Graph<String, DefaultEdge> g, DefaultEdge e, String from) {
        String src = g.getEdgeSource(e);
        return src.equals(from) ? g.getEdgeTarget(e) : src;
    }
}
