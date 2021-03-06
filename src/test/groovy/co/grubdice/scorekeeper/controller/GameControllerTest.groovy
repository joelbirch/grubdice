package co.grubdice.scorekeeper.controller
import co.grubdice.scorekeeper.dao.GameDao
import co.grubdice.scorekeeper.dao.PlayerDao
import co.grubdice.scorekeeper.dao.SeasonDao
import co.grubdice.scorekeeper.dao.SeasonScoreDao
import co.grubdice.scorekeeper.engine.LeagueScoreEngineImpl
import co.grubdice.scorekeeper.model.external.ScoreModel
import co.grubdice.scorekeeper.model.external.ScoreResult
import co.grubdice.scorekeeper.model.persistant.Game
import co.grubdice.scorekeeper.model.persistant.GameType
import co.grubdice.scorekeeper.model.persistant.Player
import co.grubdice.scorekeeper.model.persistant.Season
import groovy.mock.interceptor.MockFor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import static org.fest.assertions.Assertions.assertThat

class GameControllerTest {

    MockFor gameDaoMockFor
    MockFor seasonDaoMockFor
    def gameDaoProxy
    def seasonDaoProxy

    private Season season = new Season()

    @BeforeMethod
    public void setUp() throws Exception {
        gameDaoMockFor = new MockFor(GameDao)
        seasonDaoMockFor = new MockFor(SeasonDao)
    }

    @Test
    public void testCreateGameFromScoreModel() throws Exception {
        GameController controller = createScoreControllerFromMock()
        def game = controller.createGameFromScoreModel(
                new ScoreModel(gameType: GameType.LEAGUE, results: [new ScoreResult(["name"])]),
                season)

        assertThat(game.getPlayers()).isEqualTo(1)
        assertThat(game.getResults()).hasSize(1)
        assertThat(game.getPostingDate()).isNotNull()

        def result = game.getResults().first()
        assertThat(result.player.name).isEqualTo("name")
        assertThat(result.place).isEqualTo(0)
    }

    @Test
    public void testPlaceInGameCalculation() throws Exception {
        GameController controller = createScoreControllerFromMock()

        def scoreModel = new ScoreModel(gameType: GameType.LEAGUE, results: [
                new ScoreResult(['name1']), new ScoreResult(['name2', 'name3']), new ScoreResult(['name4'])])
        def game = controller.createGameFromScoreModel(scoreModel, season)
        assertThat(game.players).isEqualTo(4)
        assertThat(game.results[0].playerName).isEqualTo("name1")
        assertThat(game.results[0].place).isEqualTo(0)

        assertThat(game.results[1].playerName).isEqualTo("name2")
        assertThat(game.results[1].place).isEqualTo(1)

        assertThat(game.results[2].playerName).isEqualTo("name3")
        assertThat(game.results[2].place).isEqualTo(1)

        assertThat(game.results[3].playerName).isEqualTo("name4")
        assertThat(game.results[3].place).isEqualTo(3)
    }

    @Test
    public void testMostRecentGames() throws Exception {

        MockFor page = new MockFor(Page);

        seasonDaoMockFor.demand.findCurrentSeason {
            new Season()
        }

        page.demand.getContent(1) {
            def games = new ArrayList<Game>()
            for (def i=0;i<5;i++){
                games.add(new Game())
            }
            return games;
        }
        gameDaoMockFor.demand.findBySeason(1) { Season season, Pageable pageable ->
            assertThat(pageable.getSort().getOrderFor("postingDate").getDirection()).isEqualTo(Sort.Direction.DESC)
            return page.proxyInstance();
        }

        GameController controller = createScoreControllerFromMock()

        def recentGames = controller.getPageOfGames(5,0);

        assertThat(recentGames.size()).isEqualTo(5)

        gameDaoMockFor.verify(gameDaoProxy)
    }

    private GameController createScoreControllerFromMock() {
        gameDaoProxy = gameDaoMockFor.proxyInstance()
        seasonDaoProxy = seasonDaoMockFor.proxyInstance()
        return new GameController(gameDao: gameDaoProxy, seasonDao: seasonDaoProxy,
                leagueScoreEngine: new LeagueScoreEngineImpl(
                playerDao: [ save: { it }, findByNameLikeIgnoreCase: { String name -> return new Player(name) }] as PlayerDao,
                gameDao: [ save : { it } ] as GameDao,
                seasonScoreDao: [ findByPlayerAndSeason: { Player p, Season s -> null }, save : { it } ] as SeasonScoreDao))
    }
}
