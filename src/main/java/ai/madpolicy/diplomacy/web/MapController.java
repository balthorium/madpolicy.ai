package ai.madpolicy.diplomacy.web;

import ai.madpolicy.diplomacy.map.MapLayout;
import ai.madpolicy.diplomacy.map.MapLayoutService;
import ai.madpolicy.diplomacy.map.MapRenderer;
import ai.madpolicy.diplomacy.model.BoardDocument;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MapController {

    private static final MediaType SVG = MediaType.valueOf("image/svg+xml");

    private final MapLayoutService mapLayoutService;

    public MapController(MapLayoutService mapLayoutService) {
        this.mapLayoutService = mapLayoutService;
    }

    @GetMapping(path = "/map", produces = "image/svg+xml")
    public ResponseEntity<String> boardMap(
        @RequestParam(name = "board", defaultValue = "standard") String boardId,
        @RequestParam(name = "board_revision", defaultValue = "1") int boardRevision
    ) {
        MapLayout layout = mapLayoutService.layoutFor(boardId, boardRevision);
        BoardDocument board = mapLayoutService.boardFor(boardId, boardRevision);
        return ResponseEntity.ok().contentType(SVG).body(MapRenderer.render(layout, null, board));
    }

    @PostMapping(path = "/map", consumes = "application/yaml", produces = "image/svg+xml")
    public ResponseEntity<String> gameMap(@RequestBody GameStateDocument gameState) {
        MapLayout layout = mapLayoutService.layoutFor(gameState.board(), gameState.boardRevision());
        BoardDocument board = mapLayoutService.boardFor(gameState.board(), gameState.boardRevision());
        return ResponseEntity.ok().contentType(SVG).body(MapRenderer.render(layout, gameState, board));
    }
}
