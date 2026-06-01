package ai.madpolicy.diplomacy.web;

import ai.madpolicy.diplomacy.app.AdjudicationService;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdjudicationController {
    private final AdjudicationService adjudicationService;

    public AdjudicationController(AdjudicationService adjudicationService) {
        this.adjudicationService = adjudicationService;
    }

    @PostMapping(
        path = "/adjudicate",
        consumes = "application/yaml",
        produces = "application/yaml"
    )
    public GameStateDocument adjudicate(@RequestBody GameStateDocument gameState) {
        return adjudicationService.adjudicate(gameState);
    }

    @GetMapping(
        path = "/officiate",
        produces = "application/yaml"
    )
    public GameStateDocument initialState(
        @RequestParam(name = "game_id", required = false) String gameId,
        @RequestParam(name = "move_phase_minutes", required = false) Integer movePhaseMinutes,
        @RequestParam(name = "retreat_phase_minutes", required = false) Integer retreatPhaseMinutes,
        @RequestParam(name = "adjustment_phase_minutes", required = false) Integer adjustmentPhaseMinutes
    ) {
        return adjudicationService.initialState(
            gameId,
            movePhaseMinutes,
            retreatPhaseMinutes,
            adjustmentPhaseMinutes
        );
    }

    @PostMapping(
        path = "/officiate",
        consumes = "application/yaml",
        produces = "application/yaml"
    )
    public GameStateDocument officiate(@RequestBody GameStateDocument gameState) {
        return adjudicationService.officiate(gameState);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
