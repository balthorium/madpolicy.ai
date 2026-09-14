package ai.madpolicy.diplomacy.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapLayoutSmokeTest {

    @Test
    void generatesLayoutAndRendersSvg() throws Exception {
        MapLayoutService service = new MapLayoutService();
        MapLayout layout = service.layoutFor("standard", 1);

        assertEquals(75, layout.provinces().size());

        // Every province has a non-degenerate polygon
        int degenerate = 0;
        for (Map.Entry<String, ProvinceLayout> e : layout.provinces().entrySet()) {
            if (e.getValue().polygon().size() < 3) degenerate++;
        }
        System.out.println("Degenerate polygons: " + degenerate);
        assertTrue(degenerate == 0, "found " + degenerate + " degenerate polygons");

        // Determinism: computing again yields identical geometry
        MapLayout layout2 = new MapLayoutService().layoutFor("standard", 1);
        for (String pid : layout.provinces().keySet()) {
            assertEquals(
                layout.provinces().get(pid).polygon(),
                layout2.provinces().get(pid).polygon(),
                "polygon mismatch for " + pid
            );
        }

        String svg = MapRenderer.render(layout, null, service.boardFor("standard", 1));
        Path out = Path.of("target/map-smoke.svg");
        Files.writeString(out, svg);
        System.out.println("SVG written to " + out.toAbsolutePath() + " (" + svg.length() + " bytes)");
    }
}
