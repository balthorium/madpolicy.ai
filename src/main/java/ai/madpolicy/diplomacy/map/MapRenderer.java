package ai.madpolicy.diplomacy.map;

import ai.madpolicy.diplomacy.model.BoardDocument;
import ai.madpolicy.diplomacy.model.BoardPosition;
import ai.madpolicy.diplomacy.model.BoardProvince;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.NationState;
import ai.madpolicy.diplomacy.model.UnitState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MapRenderer {

    private static final Map<String, String> NATION_COLOR = Map.of(
        "england", "#1e3a6e",
        "france",  "#1e6e9a",
        "germany", "#5a5a5a",
        "russia",  "#d4d4d4",
        "italy",   "#2d7a2d",
        "austria", "#9a2020",
        "turkey",  "#c8a820"
    );
    private static final String NEUTRAL_COLOR = "#b0a090";

    private static final Map<String, String> TERRAIN_COLOR = Map.of(
        "ocean",     "#5b8db8",
        "coast",     "#c4b280",
        "plains",    "#a8bc72",
        "mountains", "#8a7a68",
        "tundra",    "#b8cfc0"
    );

    private MapRenderer() {}

    public static String render(MapLayout layout) {
        return render(layout, null, null);
    }

    public static String render(MapLayout layout, GameStateDocument gameState, BoardDocument board) {
        // Build lookup maps from game state
        Map<String, String> provinceController = new HashMap<>();  // provinceId -> nationId
        Map<String, String> unitByPosition     = new HashMap<>();  // positionId -> nationId
        Map<String, Boolean> dislodgedPosition = new HashMap<>();  // positionId -> dislodged?

        if (gameState != null) {
            for (Map.Entry<String, NationState> e : gameState.nations().entrySet()) {
                String nationId = e.getKey();
                NationState nation = e.getValue();
                if (nation.controls() != null) {
                    for (String pid : nation.controls()) provinceController.put(pid, nationId);
                }
                if (nation.units() != null) {
                    for (UnitState unit : nation.units()) {
                        unitByPosition.put(unit.position(), nationId);
                        dislodgedPosition.put(unit.position(), unit.dislodgedBy() != null);
                    }
                }
            }
        }

        // Build positionId -> province lookup from board
        Map<String, String> provinceByPosition = new HashMap<>();
        Map<String, String> positionType       = new HashMap<>();
        if (board != null) {
            for (Map.Entry<String, BoardProvince> e : board.provinces().entrySet()) {
                for (BoardPosition pos : e.getValue().positions()) {
                    provinceByPosition.put(pos.id(), e.getKey());
                    positionType.put(pos.id(), pos.type());
                }
            }
        }

        StringBuilder svg = new StringBuilder(1 << 17);
        svg.append("""
            <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="900" viewBox="0 0 1200 900">
            """);

        // Layer 1: ocean background
        svg.append("<rect width=\"1200\" height=\"900\" fill=\"#5b8db8\"/>\n");

        // Layer 2: province fills
        svg.append("<g id=\"provinces\">\n");
        for (Map.Entry<String, ProvinceLayout> e : layout.provinces().entrySet()) {
            ProvinceLayout pl = e.getValue();
            if (pl.polygon().isEmpty()) continue;
            String fill = TERRAIN_COLOR.getOrDefault(pl.terrain(), "#aaaaaa");
            svg.append("  <path d=\"").append(toPath(pl.polygon()))
               .append("\" fill=\"").append(fill)
               .append("\" stroke=\"#777\" stroke-width=\"0.5\"/>\n");
        }
        svg.append("</g>\n");

        // Layer 3: nation control overlay (supply center provinces)
        if (board != null) {
            svg.append("<g id=\"control\">\n");
            for (Map.Entry<String, ProvinceLayout> e : layout.provinces().entrySet()) {
                String pid = e.getKey();
                if (!board.provinces().get(pid).isSupplyCenter()) continue;
                String controller = provinceController.get(pid);
                if (controller == null) continue;
                String color = NATION_COLOR.getOrDefault(controller, NEUTRAL_COLOR);
                ProvinceLayout pl = e.getValue();
                if (pl.polygon().isEmpty()) continue;
                svg.append("  <path d=\"").append(toPath(pl.polygon()))
                   .append("\" fill=\"").append(color)
                   .append("\" opacity=\"0.4\" stroke=\"none\"/>\n");
            }
            svg.append("</g>\n");
        }

        // Layer 4: borders
        svg.append("<g id=\"borders\" fill=\"none\">\n");
        for (Map.Entry<String, ProvinceLayout> e : layout.provinces().entrySet()) {
            String pid = e.getKey();
            ProvinceLayout pl = e.getValue();
            if (pl.polygon().isEmpty()) continue;
            boolean isOcean = "ocean".equals(pl.terrain());
            String stroke = isOcean ? "#4a7a9b" : "#555";
            double width = isOcean ? 0.5 : 1.0;
            svg.append("  <path d=\"").append(toPath(pl.polygon()))
               .append("\" stroke=\"").append(stroke)
               .append("\" stroke-width=\"").append(width).append("\"/>\n");
        }
        svg.append("</g>\n");

        // Layer 5: supply center markers
        if (board != null) {
            svg.append("<g id=\"supply-centers\">\n");
            for (Map.Entry<String, ProvinceLayout> e : layout.provinces().entrySet()) {
                String pid = e.getKey();
                if (!board.provinces().get(pid).isSupplyCenter()) continue;
                Vec2 c = e.getValue().centroid();
                String controller = provinceController.get(pid);
                String fill = controller != null
                    ? NATION_COLOR.getOrDefault(controller, NEUTRAL_COLOR)
                    : "white";
                svg.append("  <path d=\"").append(starPath(c.x(), c.y(), 7, 3.5))
                   .append("\" fill=\"").append(fill)
                   .append("\" stroke=\"#333\" stroke-width=\"1\"/>\n");
            }
            svg.append("</g>\n");
        }

        // Layer 6: units
        if (gameState != null && board != null) {
            svg.append("<g id=\"units\">\n");
            for (Map.Entry<String, NationState> ne : gameState.nations().entrySet()) {
                String nationId = ne.getKey();
                NationState nation = ne.getValue();
                if (nation.units() == null) continue;
                String color = NATION_COLOR.getOrDefault(nationId, NEUTRAL_COLOR);

                for (UnitState unit : nation.units()) {
                    String posId = unit.position();
                    String provId = provinceByPosition.get(posId);
                    if (provId == null) continue;
                    ProvinceLayout pl = layout.provinces().get(provId);
                    if (pl == null) continue;

                    Vec2 pos = pl.positionCentroids().getOrDefault(posId, pl.centroid());
                    boolean dislodged = unit.dislodgedBy() != null;
                    double cx = pos.x() + (dislodged ? 8 : 0);
                    double cy = pos.y() + (dislodged ? -8 : 0);

                    String type = "land".equals(positionType.get(posId)) ? "A" : "F";
                    String stroke = dislodged ? "#cc0000" : "#111";
                    String strokeW = dislodged ? "2" : "1.5";
                    String dashArray = dislodged ? " stroke-dasharray=\"3,2\"" : "";

                    svg.append("  <circle cx=\"").append(fmt(cx)).append("\" cy=\"").append(fmt(cy))
                       .append("\" r=\"14\" fill=\"").append(color)
                       .append("\" stroke=\"").append(stroke)
                       .append("\" stroke-width=\"").append(strokeW).append("\"")
                       .append(dashArray).append("/>\n");
                    svg.append("  <text x=\"").append(fmt(cx)).append("\" y=\"").append(fmt(cy))
                       .append("\" fill=\"white\" font-size=\"12\" font-weight=\"bold\"")
                       .append(" font-family=\"sans-serif\" text-anchor=\"middle\"")
                       .append(" dominant-baseline=\"middle\">").append(type).append("</text>\n");
                }
            }
            svg.append("</g>\n");
        }

        // Layer 7: province name labels
        svg.append("<g id=\"labels\">\n");
        for (Map.Entry<String, ProvinceLayout> e : layout.provinces().entrySet()) {
            ProvinceLayout pl = e.getValue();
            String pid = e.getKey();
            String name = board != null
                ? board.provinces().get(pid).name()
                : pid;
            boolean isOcean = "ocean".equals(pl.terrain());
            String fill = isOcean ? "#ddeeff" : "#222";
            int fontSize = isOcean ? 9 : 10;
            Vec2 c = pl.centroid();
            svg.append("  <text x=\"").append(fmt(c.x())).append("\" y=\"").append(fmt(c.y()))
               .append("\" fill=\"").append(fill)
               .append("\" font-size=\"").append(fontSize)
               .append("\" font-family=\"sans-serif\" text-anchor=\"middle\"")
               .append(" dominant-baseline=\"middle\">").append(abbreviate(name))
               .append("</text>\n");
        }
        svg.append("</g>\n");

        svg.append("</svg>\n");
        return svg.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String toPath(List<Vec2> poly) {
        if (poly.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("M %.1f,%.1f", poly.get(0).x(), poly.get(0).y()));
        for (int i = 1; i < poly.size(); i++) {
            sb.append(String.format(" L %.1f,%.1f", poly.get(i).x(), poly.get(i).y()));
        }
        sb.append(" Z");
        return sb.toString();
    }

    private static String starPath(double cx, double cy, double outerR, double innerR) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(90 + i * 36);
            double r = (i % 2 == 0) ? outerR : innerR;
            double x = cx + r * Math.cos(angle);
            double y = cy - r * Math.sin(angle);
            sb.append(i == 0
                ? String.format("M %.1f,%.1f", x, y)
                : String.format(" L %.1f,%.1f", x, y));
        }
        sb.append(" Z");
        return sb.toString();
    }

    private static String abbreviate(String name) {
        if (name.length() <= 12) return name;
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name.substring(0, 11) + "…";
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }
}
