package org.secuso.privacyfriendlymemory.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.RatingBar;
import android.widget.TextView;

import org.secuso.privacyfriendlymemory.Constants;
import org.secuso.privacyfriendlymemory.common.MemoryStatistics;
import org.secuso.privacyfriendlymemory.common.ResIdAdapter;
import org.secuso.privacyfriendlymemory.model.CardDesign;
import org.secuso.privacyfriendlymemory.model.Memory;
import org.secuso.privacyfriendlymemory.model.MemoryDifficulty;
import org.secuso.privacyfriendlymemory.model.MemoryHighscore;
import org.secuso.privacyfriendlymemory.common.MemoryLayoutProvider;
import org.secuso.privacyfriendlymemory.model.MemoryMode;
import org.secuso.privacyfriendlymemory.model.MemoryPlayer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class MemoryActivity extends AppCompatDrawerActivity {

    private static Context context;
    private SharedPreferences preferences = null;
    private Memory memory;
    private MemoryStatistics statistics;
    private MemoryLayoutProvider layoutProvider;
    private Timer timerViewUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_game);
        super.setupNavigationView();
        setupPreferences();
        // make context assessable for shared preferences
        MemoryActivity.context = getApplicationContext();
        createMemory();
        createStatistics();
        createLayoutProvider();
        setupGridview();
        updateStatsView();
        setupToolBar();
    }

    @Override
    protected void onResume(){
        super.onResume();
        memory.startTimer();
    }

    @Override
    protected void onPause(){
        super.onPause();
        memory.stopTimer();
    }

    private void setupPreferences(){
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    private void setupToolBar(){
        // setup memory mode text view
        TextView modeView = (TextView) findViewById(R.id.gameModeText);
        modeView.setText(memory.getMode().getStringResID());
        // setup difficulty bar view
        List<MemoryDifficulty> validDifficulties = MemoryDifficulty.getValidDifficulties();
        int difficultyCounts = validDifficulties.size();
        RatingBar difficultyBar = (RatingBar) findViewById(R.id.gameModeStar);
        difficultyBar.setMax(difficultyCounts);
        difficultyBar.setNumStars(difficultyCounts);
        difficultyBar.setRating(validDifficulties.indexOf(memory.getDifficulty()) + 1);
        // setup diffculty text view
        TextView difficultyText = (TextView) findViewById(R.id.difficultyText);
        difficultyText.setText(getString(memory.getDifficulty().getStringResID()));

        // setup timertask and timer view
        final TextView timerView = (TextView) findViewById(R.id.timerView);
        timerViewUpdater = new Timer();
        timerViewUpdater.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        timerView.setText(timeToString(memory.getTime()));
                    }
                });
            }
        },0 , 1000);

    }

    private void setupGridview(){
        final GridView gridview = (GridView) findViewById(R.id.gridview);
        gridview.setNumColumns(layoutProvider.getColumnCount());
        final ViewGroup.MarginLayoutParams marginLayoutParams =(ViewGroup.MarginLayoutParams)gridview.getLayoutParams();
        marginLayoutParams.setMargins(layoutProvider.getMarginLeft(),layoutProvider.getMargin(),layoutProvider.getMarginRight(),0);
        gridview.setLayoutParams(marginLayoutParams);
        final MemoryImageAdapter imageAdapter = new MemoryImageAdapter(this, layoutProvider);
        gridview.setAdapter(imageAdapter);
        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                memory.select(position);

                // if two cards are false selected and memory is played with a custom card set increment "false selected count" for statistics
                if(!memory.isCustomDesign()){
                    Integer[] falseSelectedCards = memory.getFalseSelectedCards();
                    if(falseSelectedCards != null){
                        List<String> resourceNames = ResIdAdapter.getResourceName(Arrays.asList(falseSelectedCards), getApplicationContext());
                        statistics.incrementCount(resourceNames);
                        String staticsConstants = memory.getCardDesign() == CardDesign.FIRST ? Constants.STATISTICS_DECK_ONE : Constants.STATISTICS_DECK_TWO;
                        preferences.edit().putStringSet(staticsConstants, statistics.getStatisticsSet()).commit();
                    }
                }
                // adapter must be notified, that images will be refreshed if selected
                imageAdapter.notifyDataSetChanged();
                // update stats (found cards, tries, next player,..)
                updateStatsView();

                if(memory.isFinished()){
                    saveHighscore();
                    showWinDialog();
                    gridview.setEnabled(false);
                    timerViewUpdater.cancel();
                }
            }
        });

    }
    private void updateStatsView(){
        // set views for player one
        MemoryPlayer playerOne = memory.getPlayers().get(0);
        // init player name view
        TextView playerOneNameView = (TextView) findViewById(R.id.player_one_name);
        playerOneNameView.setText(getResources().getString(R.string.player_name_prefix) + " " + playerOne.getNameSuffix());
        // init found values for player one
        TextView playerOneFoundsValueView = (TextView) findViewById(R.id.player_one_found_value);
        StringBuilder playerOneFoundsValueBuilder = new StringBuilder();
        playerOneFoundsValueBuilder.append(playerOne.getFoundCardsCount()).append("/").append(memory.getDeckSize());
        String playerOneFoundsValue = playerOneFoundsValueBuilder.toString();
        playerOneFoundsValueView.setText(playerOneFoundsValue);
        // init tries value for player one
        TextView playerOneTriesValueView = (TextView) findViewById(R.id.player_one_tries_value);
        playerOneTriesValueView.setText(String.valueOf(playerOne.getTries()));
        // set views for player two, if only one player exists set views empty
        TextView playerTwoNameView = (TextView) findViewById(R.id.player_two_name);
        TextView playerTwoFoundsView = (TextView) findViewById(R.id.player_two_found);
        TextView playerTwoFoundsValueView = (TextView) findViewById(R.id.player_two_found_value);
        TextView playerTwoTriesView = (TextView) findViewById(R.id.player_two_tries);
        TextView playerTwoTriesValueView = (TextView) findViewById(R.id.player_two_tries_value);
        if(memory.isMultiplayer()){
            MemoryPlayer playerTwo = memory.getPlayers().get(1);
            // set player two name
            playerTwoNameView.setText(getResources().getString(R.string.player_name_prefix) + " " + playerTwo.getNameSuffix());
            // init found values for player two
            StringBuilder playerTwoFoundsValueBuilder = new StringBuilder();
            playerTwoFoundsValueBuilder.append(playerTwo.getFoundCardsCount()).append("/").append(memory.getDeckSize());
            String playerTwoFoundsValue = playerTwoFoundsValueBuilder.toString();
            playerTwoFoundsValueView.setText(playerTwoFoundsValue);
            // init tries value for player two
            playerTwoTriesValueView.setText(String.valueOf(playerTwo.getTries()));
        }else{
            // set all views empty
            playerTwoNameView.setText("");
            playerTwoFoundsView.setText("");
            playerTwoFoundsValueView.setText("");
            playerTwoTriesView.setText("");
            playerTwoTriesValueView.setText("");
        }

        TextView playerOneFoundsView = (TextView) findViewById(R.id.player_one_found);
        TextView playerOneTriesView = (TextView) findViewById(R.id.player_one_tries);
        // highlight current player
        MemoryPlayer currentPlayer = memory.getCurrentPlayer();
        int highlightColor = ContextCompat.getColor(this, R.color.colorPrimary);
        int normalColor = ContextCompat.getColor(this, R.color.middlegrey);
        if(currentPlayer == playerOne){
            setColorFor(highlightColor, playerOneNameView, playerOneFoundsView, playerOneFoundsValueView, playerOneTriesView, playerOneTriesValueView);
            setColorFor(normalColor, playerTwoNameView, playerTwoFoundsView, playerTwoFoundsValueView, playerTwoTriesView, playerTwoTriesValueView);
        }else{
            setColorFor(highlightColor, playerTwoNameView, playerTwoFoundsView, playerTwoFoundsValueView, playerTwoTriesView, playerTwoTriesValueView);
            setColorFor(normalColor, playerOneNameView, playerOneFoundsView, playerOneFoundsValueView, playerOneTriesView, playerOneTriesValueView);
        }
    }

    private void setColorFor(int color, TextView... views){
        for(TextView view : views){
            view.setTextColor(color);
        }
    }

    private void saveHighscore(){
        if(!memory.isMultiplayer() && !memory.isCustomDesign()){
            MemoryHighscore highscore = memory.getHighscore();
            int actualCore = highscore.getScore();
            int actualTries = highscore.getTries();
            int actualTime = highscore.getTime();
            MemoryDifficulty difficulty = memory.getDifficulty();
            String highscoreConstants = "";
            String highscoreTriesConstants = "";
            String highscoreTimeConstants = "";
            switch(difficulty) {
                case Easy:
                    highscoreConstants = Constants.HIGHSCORE_EASY;
                    highscoreTriesConstants = Constants.HIGHSCORE_EASY_TRIES;
                    highscoreTimeConstants = Constants.HIGHSCORE_EASY_TIME;
                    break;
                case Moderate:
                    highscoreConstants = Constants.HIGHSCORE_MODERATE;
                    highscoreTriesConstants = Constants.HIGHSCORE_MODERATE_TRIES;
                    highscoreTimeConstants = Constants.HIGHSCORE_MODERATE_TIME;
                    break;
                case Hard:
                    highscoreConstants = Constants.HIGHSCORE_HARD;
                    highscoreTriesConstants = Constants.HIGHSCORE_HARD_TRIES;
                    highscoreTimeConstants = Constants.HIGHSCORE_HARD_TIME;
                    break;
            }
            int currentScore = preferences.getInt(highscoreConstants, 0);
            if(actualCore > currentScore){
                preferences.edit().putInt(highscoreConstants, actualCore).commit();
                preferences.edit().putInt(highscoreTriesConstants, actualTries).commit();
                preferences.edit().putInt(highscoreTimeConstants, actualTime).commit();
            }
        }
    }

    private void showWinDialog(){
        final Dialog dialog;
        if(memory.isMultiplayer()){
            dialog = new DuoPlayerWinDialog(this, R.style.WinDialog, memory.getPlayers());
            dialog.getWindow().setContentView(R.layout.win_duo_screen_layout);
        }else {
            dialog = new SinglePlayerWinDialog(this, R.style.WinDialog, memory.getHighscore());
            dialog.getWindow().setContentView(R.layout.win_solo_screen_layout);
        }
        dialog.getWindow().setGravity(Gravity.CENTER_HORIZONTAL);
        dialog.getWindow().setBackgroundDrawableResource(R.color.transparent);

        dialog.show();

        final Activity activity = this;
        ((Button)dialog.findViewById(R.id.win_continue_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                Intent intent = new Intent(activity, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                activity.finish();
            }
        });
        ((Button)dialog.findViewById(R.id.win_showGame_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    private void createLayoutProvider(){
        layoutProvider = new MemoryLayoutProvider(this, memory);
    }

    private void createMemory() {
        Bundle intentExtras = getIntent().getExtras();
        MemoryMode mode = (MemoryMode) intentExtras.get(Constants.GAME_MODE);
        MemoryDifficulty difficulty = (MemoryDifficulty) intentExtras.get(Constants.GAME_DIFFICULTY);
        CardDesign design = (CardDesign) intentExtras.get(Constants.CARD_DESIGN);
        memory = new Memory(design, mode, difficulty);
    }

    private void createStatistics(){
        if(!memory.isCustomDesign()) {
            Bundle intentExtras = getIntent().getExtras();
            CardDesign design = (CardDesign) intentExtras.get(Constants.CARD_DESIGN);
            String statisticsConstants = "";
            switch (design) {
                case FIRST:
                    statisticsConstants = Constants.STATISTICS_DECK_ONE;
                    break;
                case SECOND:
                    statisticsConstants = Constants.STATISTICS_DECK_TWO;
                    break;
            }
            Set<String> statisticsSet = preferences.getStringSet(statisticsConstants, new HashSet<String>());
            statistics = new MemoryStatistics(statisticsSet);
        }
    }

    private String timeToString(int time) {
        int seconds = time % 60;
        int minutes = ((time - seconds) / 60) % 60;
        int hours = (time - minutes - seconds) / (3600);
        String h, m, s;
        s = (seconds < 10) ? "0" + String.valueOf(seconds) : String.valueOf(seconds);
        m = (minutes < 10) ? "0" + String.valueOf(minutes) : String.valueOf(minutes);
        h = (hours < 10) ? "0" + String.valueOf(hours) : String.valueOf(hours);
        return h + ":" + m + ":" + s;
    }

    public static Context getAppContext(){
        return MemoryActivity.context;
    }

    public class SinglePlayerWinDialog extends Dialog {
        private MemoryHighscore highscore;

        public SinglePlayerWinDialog(Context context, int themeResId, MemoryHighscore highscore) {
            super(context, themeResId);
            this.highscore = highscore;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {

            super.onCreate(savedInstanceState);
            ((TextView) findViewById(R.id.win_time)).setText(timeToString(highscore.getTime()));
            ((TextView) findViewById(R.id.win_tries)).setText(String.valueOf(highscore.getTries()));
            ((TextView) findViewById(R.id.win_highscore)).setText(String.valueOf(highscore.getScore()));

        }
    }
    public class DuoPlayerWinDialog extends Dialog {

        private final MemoryPlayer playerOne;
        private final MemoryPlayer playerTwo;

        private final int cardsCount;

        public DuoPlayerWinDialog(Context context, int themeResId, List<MemoryPlayer> players) {
            super(context,themeResId);
            if(players.size() > 2){
                throw new RuntimeException("Can not create DuoPlayerWinDialog for more than 2 players");
            }
            this.playerOne = players.get(0);
            this.playerTwo = players.get(1);
            this.cardsCount = playerOne.getFoundCardsCount() + playerTwo.getFoundCardsCount();
        }



        @Override
        protected void onCreate(Bundle savedInstanceState) {

            super.onCreate(savedInstanceState);

            ((TextView)findViewById(R.id.win_player_name)).setText(computeWinnerName());
            ((TextView)findViewById(R.id.win_first_player_name)).setText(computePlayerName(playerOne));
            ((TextView)findViewById(R.id.win_first_player_cards)).setText(playerOne.getFoundCardsCount() + "/" +  cardsCount);

            ((TextView)findViewById(R.id.win_second_player_name)).setText(computePlayerName(playerTwo));
            ((TextView)findViewById(R.id.win_second_player_cards)).setText(playerTwo.getFoundCardsCount() + "/" +  cardsCount);
        }

        private String computePlayerName(MemoryPlayer player){
            return getResources().getString(R.string.player_name_prefix) + " " + player.getNameSuffix();
        }

        private String computeWinnerName(){
            String winnerName;
            int cardsPlayerOne = playerOne.getFoundCardsCount();
            int cardsPlayerTwo = playerTwo.getFoundCardsCount();

            if(cardsPlayerOne == cardsPlayerTwo){
                winnerName = computePlayerName(playerOne) + " & " + computePlayerName(playerTwo);
            }else if(cardsPlayerOne > cardsPlayerTwo) {
                winnerName = computePlayerName(playerOne);
            }else{
                winnerName = computePlayerName(playerTwo);
            }
            return winnerName + "!";
        }


    }

}