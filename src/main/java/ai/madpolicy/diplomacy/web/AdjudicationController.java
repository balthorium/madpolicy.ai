package ai.madpolicy.diplomacy.web;

import ai.madpolicy.diplomacy.app.AdjudicationService;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public GameStateDocument initialState() {
        return adjudicationService.initialState();
    }

    @PostMapping(
        path = "/officiate",
        consumes = "application/yaml",
        produces = "application/yaml"
    )
    public GameStateDocument officiate(@RequestBody GameStateDocument gameState) {
        return adjudicationService.officiate(gameState);
    }
}
