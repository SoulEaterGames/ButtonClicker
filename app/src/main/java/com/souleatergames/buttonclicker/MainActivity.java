package com.souleatergames.buttonclicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.OnInvitationReceivedListener;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateListener;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateListener;
import com.google.android.gms.plus.Plus;
import com.google.example.games.basegameutils.BaseGameUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

//import com.squareup.picasso.Picasso;

public class MainActivity extends Activity
    implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
    View.OnClickListener, RealTimeMessageReceivedListener,
    RoomStatusUpdateListener, RoomUpdateListener, OnInvitationReceivedListener {

    /*
     * API INTEGRATION SECTION. This section contains the code that integrates
     * the game with the Google Play game services API.
     */

    final static String TAG = "Binary Warz";

    // Request codes for the UIs that we show with startActivityForResult:
    final static int RC_SELECT_PLAYERS = 10000;
    final static int RC_INVITATION_INBOX = 10001;
    final static int RC_WAITING_ROOM = 10002;

  // Request code used to invoke sign in user interactions.
  private static final int RC_SIGN_IN = 9001;

  // Client used to interact with Google APIs.
  private GoogleApiClient mGoogleApiClient;

  // Are we currently resolving a connection failure?
  private boolean mResolvingConnectionFailure = false;

  // Has the user clicked the sign-in button?
  private boolean mSignInClicked = false;

  // Set to true to automatically start the sign in flow when the Activity starts.
  // Set to false to require the user to click the button in order to sign in.
  private boolean mAutoStartSignInFlow = true;

    // Room ID where the currently active game is taking place; null if we're
    // not playing.
    String mRoomId = null;

    // Are we playing in multiplayer mode?
    boolean mMultiplayer = false;

    // The participants in the currently active game
    ArrayList<Participant> mParticipants = null;

    // My participant ID in the currently active game
    String mMyId = null;

    // If non-null, this is the id of the invitation we received via the
    // invitation listener
    String mIncomingInvitationId = null;

    // Message buffer for sending messages
    byte[] mMsgBuf = new byte[2]; //mMsgBug is int of 1 byte + 1 byte to identify as message
    byte[] mSeedBuf = new byte[9]; //mSeedBuf is long of 8 bytes + 1 byte to identify as seed

    long mSeed;

    Vibrator vibe;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibe = (Vibrator) MainActivity.this.getSystemService(Context.VIBRATOR_SERVICE);
    // Create the Google Api Client with access to Plus and Games
    mGoogleApiClient = new GoogleApiClient.Builder(this)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .addApi(Plus.API).addScope(Plus.SCOPE_PLUS_LOGIN)
        .addApi(Games.API).addScope(Games.SCOPE_GAMES)
        .build();

    // set up a click listener for everything we care about
    for (int id : CLICKABLES) {
      findViewById(id).setOnClickListener(this);
    }
  }

  @Override
  public void onClick(View v) {
    Intent intent;

        switch (v.getId()) {
            case R.id.button_single_player:
            case R.id.button_single_player_2:
                // play a single-player game
                resetGameVars();
                startGame(false);
                break;
            case R.id.button_sign_in:
                // user wants to sign in
                // Check to see the developer who's running this sample code read the instructions :-)
                // NOTE: this check is here only because this is a sample! Don't include this
                // check in your actual production app.
                //if (!BaseGameUtils.verifySampleSetup(this, R.string.app_id)) {
                  //Log.w(TAG, "*** Warning: setup problems detected. Sign in may not work!");
                //}

                // start the sign-in flow
                //Log.d(TAG, "Sign-in button clicked");
                mSignInClicked = true;
                mGoogleApiClient.connect();
            break;
            case R.id.button_sign_out:
                // user wants to sign out
                // sign out.
                //Log.d(TAG, "Sign-out button clicked");
                mSignInClicked = false;
                Games.signOut(mGoogleApiClient);
                mGoogleApiClient.disconnect();
                switchToScreen(R.id.screen_sign_in);
                break;
            case R.id.button_invite_players:
                // show list of invitable players
                intent = Games.RealTimeMultiplayer.getSelectOpponentsIntent(mGoogleApiClient, 1, 3);
                switchToScreen(R.id.screen_wait);
                startActivityForResult(intent, RC_SELECT_PLAYERS);
                break;
            case R.id.button_see_invitations:
                // show list of pending invitations
                intent = Games.Invitations.getInvitationInboxIntent(mGoogleApiClient);
                switchToScreen(R.id.screen_wait);
                startActivityForResult(intent, RC_INVITATION_INBOX);
                break;
            case R.id.button_accept_popup_invitation:
                // user wants to accept the invitation shown on the invitation popup
                // (the one we got through the OnInvitationReceivedListener).
                acceptInviteToRoom(mIncomingInvitationId);
                mIncomingInvitationId = null;
                break;
            case R.id.button_quick_game:
                // user wants to play against a random opponent right now
                startQuickGame();
                break;
            case R.id.imageButton1:
                //Log.d(TAG, "Card 1 Pushed");
                cardPushed(0);
                break;
            case R.id.imageButton2:
                //Log.d(TAG, "Card 2 Pushed");
                cardPushed(1);
                break;
            case R.id.imageButton3:
                //Log.d(TAG, "Card 3 Pushed");
                cardPushed(2);
                break;
            case R.id.imageButton4:
                //Log.d(TAG, "Card 4 Pushed");
                cardPushed(3);
                break;
            case R.id.imageButton5:
                //Log.d(TAG, "Card 5 Pushed");
                cardPushed(4);
                break;
            case R.id.imageButton6:
                //Log.d(TAG, "Card 6 Pushed");
                cardPushed(5);
                break;
            case R.id.imageButton7:
                //Log.d(TAG, "Card 7 Pushed");
                cardPushed(6);
                break;
            case R.id.imageButton8:
                //Log.d(TAG, "Card 8 Pushed");
                cardPushed(7);
                break;
            case R.id.button9:
                submitPushed();
                break;
            case R.id.imageButtonMainCard:
                //auto-win cheat
                /*mScore = 0;
                gameFinished = true;
                if(mScore == 0){
                    playerWon("P1");
                }
                broadcastScore();
                break;*/
        }
    }

    void startQuickGame() {
        // quick-start a game with 1 randomly selected opponent
        final int MIN_OPPONENTS = 1, MAX_OPPONENTS = 1;
        Bundle autoMatchCriteria = RoomConfig.createAutoMatchCriteria(MIN_OPPONENTS,
                MAX_OPPONENTS, 0);
        RoomConfig.Builder rtmConfigBuilder = RoomConfig.builder(this);
        rtmConfigBuilder.setMessageReceivedListener(this);
        rtmConfigBuilder.setRoomStatusUpdateListener(this);
        rtmConfigBuilder.setAutoMatchCriteria(autoMatchCriteria);
        switchToScreen(R.id.screen_wait);
        keepScreenOn();
        resetGameVars();
        Games.RealTimeMultiplayer.create(mGoogleApiClient, rtmConfigBuilder.build());
    }

    @Override
    public void onActivityResult(int requestCode, int responseCode,
            Intent intent) {
        super.onActivityResult(requestCode, responseCode, intent);

        switch (requestCode) {
            case RC_SELECT_PLAYERS:
                // we got the result from the "select players" UI -- ready to create the room
                handleSelectPlayersResult(responseCode, intent);
                break;
            case RC_INVITATION_INBOX:
                // we got the result from the "select invitation" UI (invitation inbox). We're
                // ready to accept the selected invitation:
                handleInvitationInboxResult(responseCode, intent);
                break;
            case RC_WAITING_ROOM:
                // we got the result from the "waiting room" UI.
                if (responseCode == Activity.RESULT_OK) {
                    // ready to start playing
                    //Log.d(TAG, "Starting game (waiting room returned OK).");

                    //"King" will generate a seed and broadcast it to all other players
                    if (amIKing()) {
                        mSeed = System.currentTimeMillis();
                        //Log.d(TAG, "seed " + mSeed);
                        broadcastSeed(mSeed);
                    }
                    //wait 5sec before beginning game so players can receive seed
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startGame(true);
                        }
                    }, 5000);

                } else if (responseCode == GamesActivityResultCodes.RESULT_LEFT_ROOM) {
                    // player indicated that they want to leave the room
                    leaveRoom();
                } else if (responseCode == Activity.RESULT_CANCELED) {
                    // DiaLog was cancelled (user pressed back key, for instance). In our game,
                    // this means leaving the room too. In more elaborate games, this could mean
                    // something else (like minimizing the waiting room UI).
                    leaveRoom();
                }
                break;
            case RC_SIGN_IN:
                //Log.d(TAG, "onActivityResult with requestCode == RC_SIGN_IN, responseCode="
                    //+ responseCode + ", intent=" + intent);
                mSignInClicked = false;
                mResolvingConnectionFailure = false;
                if (responseCode == RESULT_OK) {
                  mGoogleApiClient.connect();
                } else {
                  BaseGameUtils.showActivityResultError(this,requestCode,responseCode, R.string.signin_other_error);
                }
                break;
        }
        super.onActivityResult(requestCode, responseCode, intent);
    }

    // Handle the result of the "Select players UI" we launched when the user clicked the
    // "Invite friends" button. We react by creating a room with those players.
    private void handleSelectPlayersResult(int response, Intent data) {
        if (response != Activity.RESULT_OK) {
            //Log.w(TAG, "*** select players UI cancelled, " + response);
            switchToMainScreen();
            return;
        }

        //Log.d(TAG, "Select players UI succeeded.");

        // get the invitee list
        final ArrayList<String> invitees = data.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);
        //Log.d(TAG, "Invitee count: " + invitees.size());

        // get the automatch criteria
        Bundle autoMatchCriteria = null;
        int minAutoMatchPlayers = data.getIntExtra(Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
        int maxAutoMatchPlayers = data.getIntExtra(Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);
        if (minAutoMatchPlayers > 0 || maxAutoMatchPlayers > 0) {
            autoMatchCriteria = RoomConfig.createAutoMatchCriteria(
                    minAutoMatchPlayers, maxAutoMatchPlayers, 0);
            //Log.d(TAG, "Automatch criteria: " + autoMatchCriteria);
        }

        // create the room
        //Log.d(TAG, "Creating room...");
        RoomConfig.Builder rtmConfigBuilder = RoomConfig.builder(this);
        rtmConfigBuilder.addPlayersToInvite(invitees);
        rtmConfigBuilder.setMessageReceivedListener(this);
        rtmConfigBuilder.setRoomStatusUpdateListener(this);
        if (autoMatchCriteria != null) {
            rtmConfigBuilder.setAutoMatchCriteria(autoMatchCriteria);
        }
        switchToScreen(R.id.screen_wait);
        keepScreenOn();
        resetGameVars();
        Games.RealTimeMultiplayer.create(mGoogleApiClient, rtmConfigBuilder.build());
        //Log.d(TAG, "Room created, waiting for it to be ready...");
    }

    // Handle the result of the invitation inbox UI, where the player can pick an invitation
    // to accept. We react by accepting the selected invitation, if any.
    private void handleInvitationInboxResult(int response, Intent data) {
        if (response != Activity.RESULT_OK) {
            //Log.w(TAG, "*** invitation inbox UI cancelled, " + response);
            switchToMainScreen();
            return;
        }

        //Log.d(TAG, "Invitation inbox UI succeeded.");
        Invitation inv = data.getExtras().getParcelable(Multiplayer.EXTRA_INVITATION);

        // accept invitation
        acceptInviteToRoom(inv.getInvitationId());
    }

    // Accept the given invitation.
    void acceptInviteToRoom(String invId) {
        // accept the invitation
        //Log.d(TAG, "Accepting invitation: " + invId);
        RoomConfig.Builder roomConfigBuilder = RoomConfig.builder(this);
        roomConfigBuilder.setInvitationIdToAccept(invId)
                .setMessageReceivedListener(this)
                .setRoomStatusUpdateListener(this);
        switchToScreen(R.id.screen_wait);
        keepScreenOn();
        resetGameVars();
        Games.RealTimeMultiplayer.join(mGoogleApiClient, roomConfigBuilder.build());
    }

    // Activity is going to the background. We have to leave the current room.
    @Override
    public void onStop() {
        //Log.d(TAG, "**** got onStop");

        // if we're in a room, leave it.
        leaveRoom();

        // stop trying to keep the screen on
        stopKeepingScreenOn();

        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()){
          switchToScreen(R.id.screen_sign_in);
        }
        else {
          switchToScreen(R.id.screen_wait);
        }
        super.onStop();
      }

    // Activity just got to the foreground. We switch to the wait screen because we will now
    // go through the sign-in flow (remember that, yes, every time the Activity comes back to the
    // foreground we go through the sign-in flow -- but if the user is already authenticated,
    // this flow simply succeeds and is imperceptible).
    @Override
    public void onStart() {
        switchToScreen(R.id.screen_wait);
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
          //Log.w(TAG,
              //"GameHelper: client was already connected on onStart()");
              switchToMainScreen();
        } else {
          //Log.d(TAG,"Connecting client.");
          mGoogleApiClient.connect();
        }
        super.onStart();
    }

    // Handle back key to make sure we cleanly leave a game if we are in the middle of one
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mCurScreen == R.id.screen_game) {
            leaveRoom();

            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    // Leave the room.
    void leaveRoom() {
        //Log.d(TAG, "Leaving room.");
        mSecondsLeft = 0;
        gameFinished = true;
        stopKeepingScreenOn();
        if (mRoomId != null) {
            Games.RealTimeMultiplayer.leave(mGoogleApiClient, this, mRoomId);
            mRoomId = null;
            switchToScreen(R.id.screen_wait);
        } else {
            switchToMainScreen();
        }
    }

    // Show the waiting room UI to track the progress of other players as they enter the
    // room and get connected.
    void showWaitingRoom(Room room) {
        // minimum number of players required for our game
        // For simplicity, we require everyone to join the game before we start it
        // (this is signaled by Integer.MAX_VALUE).
        final int MIN_PLAYERS = Integer.MAX_VALUE;
        Intent i = Games.RealTimeMultiplayer.getWaitingRoomIntent(mGoogleApiClient, room, MIN_PLAYERS);

        // show waiting room UI
        startActivityForResult(i, RC_WAITING_ROOM);
    }

    // Called when we get an invitation to play a game. We react by showing that to the user.
    @Override
    public void onInvitationReceived(Invitation invitation) {
        // We got an invitation to play a game! So, store it in
        // mIncomingInvitationId
        // and show the popup on the screen.
        mIncomingInvitationId = invitation.getInvitationId();
        ((TextView) findViewById(R.id.incoming_invitation_text)).setText(
                invitation.getInviter().getDisplayName() + " " +
                        getString(R.string.is_inviting_you));
        switchToScreen(mCurScreen); // This will show the invitation popup
    }

    @Override
    public void onInvitationRemoved(String invitationId) {
        if (mIncomingInvitationId.equals(invitationId)) {
            mIncomingInvitationId = null;
            switchToScreen(mCurScreen); // This will hide the invitation popup
        }
    }

    /*
     * CALLBACKS SECTION. This section shows how we implement the several games
     * API callbacks.
     */

    @Override
    public void onConnected(Bundle connectionHint) {
      //Log.d(TAG, "onConnected() called. Sign in successful!");

      //Log.d(TAG, "Sign-in succeeded.");

      // register listener so we are notified if we receive an invitation to play
      // while we are in the game
      Games.Invitations.registerInvitationListener(mGoogleApiClient, this);

      if (connectionHint != null) {
        //Log.d(TAG, "onConnected: connection hint provided. Checking for invite.");
        Invitation inv = connectionHint
            .getParcelable(Multiplayer.EXTRA_INVITATION);
        if (inv != null && inv.getInvitationId() != null) {
          // retrieve and cache the invitation ID
          //Log.d(TAG,"onConnected: connection hint has a room invite!");
          acceptInviteToRoom(inv.getInvitationId());
          return;
        }
      }
      switchToMainScreen();

    }

    @Override
    public void onConnectionSuspended(int i) {
      //Log.d(TAG, "onConnectionSuspended() called. Trying to reconnect.");
      mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
      //Log.d(TAG, "onConnectionFailed() called, result: " + connectionResult);

      if (mResolvingConnectionFailure) {
        //Log.d(TAG, "onConnectionFailed() ignoring connection failure; already resolving.");
        return;
      }

      if (mSignInClicked || mAutoStartSignInFlow) {
        mAutoStartSignInFlow = false;
        mSignInClicked = false;
        mResolvingConnectionFailure = BaseGameUtils.resolveConnectionFailure(this, mGoogleApiClient,
            connectionResult, RC_SIGN_IN, getString(R.string.signin_other_error));
      }

      switchToScreen(R.id.screen_sign_in);
    }

    // Called when we are connected to the room. We're not ready to play yet! (maybe not everybody
    // is connected yet).
    @Override
    public void onConnectedToRoom(Room room) {
        //Log.d(TAG, "onConnectedToRoom.");

        // get room ID, participants and my ID:
        mRoomId = room.getRoomId();
        mParticipants = room.getParticipants();
        mMyId = room.getParticipantId(Games.Players.getCurrentPlayerId(mGoogleApiClient));

        // print out the list of participants (for debug purposes)
        //Log.d(TAG, "Room ID: " + mRoomId);
        //Log.d(TAG, "My ID " + mMyId);
        //Log.d(TAG, "<< CONNECTED TO ROOM>>");
    }

    // Called when we've successfully left the room (this happens a result of voluntarily leaving
    // via a call to leaveRoom(). If we get disconnected, we get onDisconnectedFromRoom()).
    @Override
    public void onLeftRoom(int statusCode, String roomId) {
        // we have left the room; return to main screen.
        //Log.d(TAG, "onLeftRoom, code " + statusCode);
        switchToMainScreen();
    }

    // Called when we get disconnected from the room. We return to the main screen.
    @Override
    public void onDisconnectedFromRoom(Room room) {
        mRoomId = null;
        showGameError();
    }

    // Show error message about game being cancelled and return to main screen.
    void showGameError() {
        BaseGameUtils.makeSimpleDialog(this, getString(R.string.game_problem));
        switchToMainScreen();
    }

    // Called when room has been created
    @Override
    public void onRoomCreated(int statusCode, Room room) {
        //Log.d(TAG, "onRoomCreated(" + statusCode + ", " + room + ")");
        if (statusCode != GamesStatusCodes.STATUS_OK) {
            //Log.e(TAG, "*** Error: onRoomCreated, status " + statusCode);
            showGameError();
            return;
        }

        // show the waiting room UI
        showWaitingRoom(room);
    }

    // Called when room is fully connected.
    @Override
    public void onRoomConnected(int statusCode, Room room) {
        //Log.d(TAG, "onRoomConnected(" + statusCode + ", " + room + ")");
        if (statusCode != GamesStatusCodes.STATUS_OK) {
            //Log.e(TAG, "*** Error: onRoomConnected, status " + statusCode);
            showGameError();
            return;
        }
        updateRoom(room);
    }

    @Override
    public void onJoinedRoom(int statusCode, Room room) {
        //Log.d(TAG, "onJoinedRoom(" + statusCode + ", " + room + ")");
        if (statusCode != GamesStatusCodes.STATUS_OK) {
            //Log.e(TAG, "*** Error: onRoomConnected, status " + statusCode);
            showGameError();
            return;
        }

        // show the waiting room UI
        showWaitingRoom(room);
    }

    // We treat most of the room update callbacks in the same way: we update our list of
    // participants and update the display. In a real game we would also have to check if that
    // change requires some action like removing the corresponding player avatar from the screen,
    // etc.
    @Override
    public void onPeerDeclined(Room room, List<String> arg1) {
        updateRoom(room);
    }

    @Override
    public void onPeerInvitedToRoom(Room room, List<String> arg1) {
        updateRoom(room);
    }

    @Override
    public void onP2PDisconnected(String participant) {
    }

    @Override
    public void onP2PConnected(String participant) {
    }

    @Override
    public void onPeerJoined(Room room, List<String> arg1) {
        updateRoom(room);
    }

    @Override
    public void onPeerLeft(Room room, List<String> peersWhoLeft) {
        updateRoom(room);
    }

    @Override
    public void onRoomAutoMatching(Room room) {
        updateRoom(room);
    }

    @Override
    public void onRoomConnecting(Room room) {
        updateRoom(room);
    }

    @Override
    public void onPeersConnected(Room room, List<String> peers) {
        updateRoom(room);
    }

    @Override
    public void onPeersDisconnected(Room room, List<String> peers) {
        updateRoom(room);
    }

    void updateRoom(Room room) {
        if (room != null) {
            mParticipants = room.getParticipants();
        }
        if (mParticipants != null) {
            updatePeerScoresDisplay();
        }
    }

    /*
     * GAME LOGIC SECTION. Methods that implement the game's rules.
     */

    // Current state of the game:
    int mSecondsLeft = -1; // how long until the game ends (seconds)
    final static int GAME_DURATION = 20; // game duration, seconds.
    int mScore = 0; // user's current score

    Random randomGenerator;
    Deck deck;
    Card mainCard;
    Card[] hand;
    ImageButton[] imageCard;
    //ImageButton mainCardImage = (ImageButton) findViewById(R.id.imageButtonMainCard);
    //TextView addUpTo = (TextView) findViewById(R.id.AddNumber);
    TextView addUpTo;
    int addTo;
    Button btnSubmit;
    int myTotalCardValue;
    boolean gameFinished;
    TextView cardsLeft;
    //TextView myCardsLeft;

    // Reset game variables in preparation for a new game.
    void resetGameVars() {
        mSecondsLeft = GAME_DURATION;
        mScore = 8;
        mParticipantScore.clear();
        mFinishedParticipants.clear();
    }

    void resetNewRound(){
        mSecondsLeft = GAME_DURATION;

        mainCard = deck.drawCard();
        setCardImage((ImageView) findViewById(R.id.imageButtonMainCard), mainCard.getRank(), mainCard.getSuit(), false);
        addTo = randomNumberGenerator(mainCard.getRank() + 1, 30, randomGenerator);
        addUpTo = (TextView)findViewById(R.id.AddNumber);
        addUpTo.setText("" + addTo);
        myTotalCardValue = 0;
        btnSubmit.setText("Submit: " + myTotalCardValue);

        for (int i = 0; i < 8; i++) {
            if (hand[i].getSelected()) {
                hand[i].setSelected(false);
                imageCard[i].clearColorFilter();
            }
        }
        cardsLeft.setText("cards: " + deck.getCardsRemaining() + "");
    }

    // Start the gameplay phase of the game.
    void startGame(boolean multiplayer) {
        mMultiplayer = multiplayer;
        updateScoreDisplay();
        broadcastScore();
        switchToScreen(R.id.screen_game);

        //mSeed = System.currentTimeMillis();
        gameFinished = false;
        randomGenerator = mMultiplayer ? new Random(mSeed) : new Random();

        deck = mMultiplayer ? new Deck(mSeed): new Deck();
        mainCard = deck.drawCard();

        /*
        ImageView playCard = (ImageView) findViewById(R.id.imageButtonMainCard);
        String testImageURL = Images.imageFullURLS[0];
        Picasso.with(this)
                .load(testImageURL)
                .into(playCard);
        */

        addTo = randomNumberGenerator(mainCard.getRank() + 1, 30, randomGenerator);
        addUpTo = (TextView)findViewById(R.id.AddNumber);
        addUpTo.setText(addTo+"");
        myTotalCardValue = 0;

        btnSubmit = (Button) findViewById(R.id.button9);
        btnSubmit.setText("Submit: " + myTotalCardValue);
        imageCard = new ImageButton[8];
        imageCard[0] = (ImageButton) findViewById(R.id.imageButton1);
        imageCard[1] = (ImageButton) findViewById(R.id.imageButton2);
        imageCard[2] = (ImageButton) findViewById(R.id.imageButton3);
        imageCard[3] = (ImageButton) findViewById(R.id.imageButton4);
        imageCard[4] = (ImageButton) findViewById(R.id.imageButton5);
        imageCard[5] = (ImageButton) findViewById(R.id.imageButton6);
        imageCard[6] = (ImageButton) findViewById(R.id.imageButton7);
        imageCard[7] = (ImageButton) findViewById(R.id.imageButton8);
        cardsLeft = (TextView) findViewById(R.id.cardsLeft);



        if (mMultiplayer){
            setUpMultiplayerHand();
        }
        else{
            drawHand();
        }
        cardsLeft.setText("cards: " + deck.getCardsRemaining() + "");

        reenableCards();
        //myCardsLeft = (TextView) findViewById(R.id.score0);
        //myCardsLeft.setText("P1: "+cardLeftInHand());

        setCardImage((ImageView)findViewById(R.id.imageButtonMainCard), mainCard.getRank(), mainCard.getSuit(), false);


        // run the gameTick() method every second to update the game.
        final Handler h = new Handler();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                //if (mSecondsLeft <= 0)
                if (gameFinished || mCurScreen != R.id.screen_game)
                    return;
                gameTick();
                h.postDelayed(this, 1000);
            }
        }, 1000);
    }

    // Game tick -- update countdown, check if game ended.
    void gameTick() {
        if (mSecondsLeft > 0)
            --mSecondsLeft;

        // update countdown
        /*
        ((TextView) findViewById(R.id.countdown)).setText("0:" +
                (mSecondsLeft < 10 ? "0" : "") + String.valueOf(mSecondsLeft));*/

        ((TextView) findViewById(R.id.timer)).setText("0:" +
                (mSecondsLeft < 10 ? "0" : "") + String.valueOf(mSecondsLeft));

        if (mSecondsLeft <= 0) {
            //timer runs out: vibrate then new round
            vibe.vibrate(80);
            resetNewRound();
        }
        if (deck.getCardsRemaining() <= 0){
            deckEmptyWinner();
        }
    }

    void cardPushed(int i){
        if (hand[i].getSelected()) {
            myTotalCardValue -= hand[i].getRank();
            //imageCard[i].getBackground().setAlpha(255);
            //imageCard[i].setBackgroundResource(Color.TRANSPARENT);
            imageCard[i].clearColorFilter();
            hand[i].setSelected(false);
        } else {
            myTotalCardValue += hand[i].getRank();
            //imageCard[i].getBackground().setAlpha(100);
            //imageCard[i].setBackgroundResource(R.color.card_selected);
            imageCard[i].setColorFilter(R.color.card_selected);
            hand[i].setSelected(true);
        }
        btnSubmit.setText("Submit: " + myTotalCardValue);
    }

    void submitPushed(){
        //Log.d(TAG, myTotalCardValue + mainCard.getRank() +" =?= " + addTo);
        if (myTotalCardValue + mainCard.getRank() == addTo) {
            Toast.makeText(getApplicationContext(), "TRUE", Toast.LENGTH_SHORT).show();

            for (int i = 0; i < 8; i++) {
                if (hand[i].getSelected()) {
                    hand[i].setSelected(false);
                    imageCard[i].setEnabled(false);
                    imageCard[i].setImageResource(R.drawable.zzback_of_card);
                }
            }
            //myCardsLeft.setText("P1: "+cardLeftInHand());
            mScore = cardLeftInHand();
            broadcastScore();
            broadcastNewRound();
            resetNewRound();
            if(mScore == 0){
                playerWon("P1");
            }
        } else {
            Toast.makeText(getApplicationContext(), "FALSE", Toast.LENGTH_SHORT).show();
        }
    }

    int cardLeftInHand(){
        int cardsLeftHand = 0;
        for(int i = 0 ; i <8 ;i++){
            if(imageCard[i].isEnabled()){
                cardsLeftHand++;
            }
        }

        //Log.d(TAG, "cards left in hand" +cardsLeftHand+"");
        return cardsLeftHand;

    }

    void reenableCards(){
        for (int i = 0; i < 8; i++) {
            imageCard[i].setEnabled(true);
            imageCard[i].clearColorFilter();
        }
    }

    /*
     * COMMUNICATIONS SECTION. Methods that implement the game's network
     * protocol.
     */

    // Score of other participants. We update this as we receive their scores
    // from the network.
    Map<String, Integer> mParticipantScore = new HashMap<String, Integer>();

    // Participants who sent us their final score.
    Set<String> mFinishedParticipants = new HashSet<String>();

    // Called when we receive a real-time message from the network.
    // Messages in our game are made up of 2 bytes: the first one is 'F' or 'U'
    // indicating
    // whether it's a final or interim score. The second byte is the score.
    // There is also the
    // 'S' message, which indicates that the game should start.
    @Override
    public void onRealTimeMessageReceived(RealTimeMessage rtm) {
        byte[] buf = rtm.getMessageData();
        String sender = rtm.getSenderParticipantId();

        if (buf[0] == 'h') {
            //Log.d(TAG, "Hand Message received: " + (char) buf[0] + "/" + (int) buf[1]);
            // score update.
            int existingScore = mParticipantScore.containsKey(sender) ?
                    mParticipantScore.get(sender) : 8;
            int thisScore = (int) buf[1];
            if (thisScore < existingScore) {
                // this check is necessary because packets may arrive out of
                // order, so we
                // should only ever consider the highest score we received, as
                // we know in our
                // game there is no way to lose points. If there was a way to
                // lose points,
                // we'd have to add a "serial number" to the packet.
                mParticipantScore.put(sender, thisScore);
            }

            // update the scores on the screen
            updatePeerScoresDisplay();
        }
        else if(buf[0] == 'n'){
            //Log.d(TAG, "Other player scored start new round");
            resetNewRound();
        }
        else {
           //Log.d(TAG, "Message received: " + buf[0] + buf[1] + buf[2] + buf[3] + buf[4] + buf[5] + buf[6] + buf[7]);
           //Log.d(TAG, "Message in Long: " + bytesToLong(buf));
           mSeed = bytesToLong(buf);
        }
    }

    // Broadcast my score to everybody else.
    void broadcastScore() {
        if (!mMultiplayer)
            return; // playing single-player mode

        // First byte in message indicates whether it's a final score or not
        mMsgBuf[0] = (byte) ('h');

        // Second byte is the score.
        mMsgBuf[1] = (byte) mScore;

        // Send to every other participant.
        for (Participant p : mParticipants) {
            if (p.getParticipantId().equals(mMyId))
                continue;
            if (p.getStatus() != Participant.STATUS_JOINED)
                continue;

            // final score notification must be sent via reliable message
            Games.RealTimeMultiplayer.sendReliableMessage(mGoogleApiClient, null, mMsgBuf,
                    mRoomId, p.getParticipantId());

            // it's an interim score notification, so we can use unreliable
                /*Games.RealTimeMultiplayer.sendUnreliableMessage(mGoogleApiClient, mMsgBuf, mRoomId,
                        p.getParticipantId());*/

        }
    }

    void broadcastSeed(long seed){
        mSeedBuf = longToBytes(seed);
        // Send seed to every other participant.
        for (Participant p : mParticipants) {
            if (p.getParticipantId().equals(mMyId)){
                continue;
            }
            if (p.getStatus() != Participant.STATUS_JOINED) {
                continue;
            }
            //Log.d(TAG, "Seed Sent: " + bytesToLong(mSeedBuf));
            Games.RealTimeMultiplayer.sendReliableMessage(mGoogleApiClient, null, mSeedBuf,
                    mRoomId, p.getParticipantId());
        }
    }

    void broadcastNewRound() {
        if (!mMultiplayer)
            return; // playing single-player mode

        // First byte in message indicates new round
        mMsgBuf[0] = (byte) ('n');

        // Send to every other participant.
        for (Participant p : mParticipants) {
            if (p.getParticipantId().equals(mMyId))
                continue;
            if (p.getStatus() != Participant.STATUS_JOINED)
                continue;

            Games.RealTimeMultiplayer.sendReliableMessage(mGoogleApiClient, null, mMsgBuf,
                    mRoomId, p.getParticipantId());

        }
    }

    /*
     * UI SECTION. Methods that implement the game's UI.
     */

    // This array lists everything that's clickable, so we can install click
    // event handlers.
    final static int[] CLICKABLES = {
            R.id.button_accept_popup_invitation, R.id.button_invite_players,
            R.id.button_quick_game, R.id.button_see_invitations, R.id.button_sign_in,
            R.id.button_sign_out, R.id.button_single_player,
            R.id.button_single_player_2, R.id.imageButton1, R.id.imageButton2, R.id.imageButton3,
            R.id.imageButton4, R.id.imageButton5, R.id.imageButton6, R.id.imageButton7, R.id.imageButton8,
            R.id.button9, R.id.imageButtonMainCard
    };

    // This array lists all the individual screens our game has.
    final static int[] SCREENS = {
            R.id.screen_game, R.id.screen_main, R.id.screen_sign_in,
            R.id.screen_wait
    };
    int mCurScreen = -1;

    void switchToScreen(int screenId) {
        // make the requested screen visible; hide all others.
        for (int id : SCREENS) {
            findViewById(id).setVisibility(screenId == id ? View.VISIBLE : View.GONE);
        }
        mCurScreen = screenId;

        // should we show the invitation popup?
        boolean showInvPopup;
        if (mIncomingInvitationId == null) {
            // no invitation, so no popup
            showInvPopup = false;
        } else if (mMultiplayer) {
            // if in multiplayer, only show invitation on main screen
            showInvPopup = (mCurScreen == R.id.screen_main);
        } else {
            // single-player: show on main screen and gameplay screen
            showInvPopup = (mCurScreen == R.id.screen_main || mCurScreen == R.id.screen_game);
        }
        findViewById(R.id.invitation_popup).setVisibility(showInvPopup ? View.VISIBLE : View.GONE);
    }

    void switchToMainScreen() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            switchToScreen(R.id.screen_main);
        }
        else {
            switchToScreen(R.id.screen_sign_in);
        }
    }

    // updates the label that shows my score
    void updateScoreDisplay() {
        //((TextView) findViewById(R.id.my_score)).setText(formatScore(mScore));
    }

    // formats a score as a three-digit number
    String formatScore(int i) {
        if (i < 0)
            i = 0;
        String s = String.valueOf(i);
        return s.length() == 1 ? "00" + s : s.length() == 2 ? "0" + s : s;
    }

    // updates the screen with the scores from our peers
    void updatePeerScoresDisplay() {
        //((TextView) findViewById(R.id.score0)).setText(formatScore(mScore) + " - Me");
        int[] arr = {
                R.id.score1, R.id.score2, R.id.score3
        };
        int i = 0;

        if (mRoomId != null) {
            for (Participant p : mParticipants) {
                String pid = p.getParticipantId();
                if (pid.equals(mMyId))
                    continue;
                if (p.getStatus() != Participant.STATUS_JOINED)
                    continue;
                int score = mParticipantScore.containsKey(pid) ? mParticipantScore.get(pid) : 8;
                ((TextView) findViewById(arr[i])).setText("P"+(i+2)+": "+score);
                ++i;
                if(score == 0){
                    playerWon(p.getDisplayName());
                }
            }
        }

        for (; i < arr.length; ++i) {
            ((TextView) findViewById(arr[i])).setText("");
        }
    }

    /*
     * MISC SECTION. Miscellaneous methods.
     */


    // Sets the flag to keep this screen on. It's recommended to do that during
    // the
    // handshake when setting up a game, because if the screen turns off, the
    // game will be
    // cancelled.
    void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // Clears the flag that keeps the screen on.
    void stopKeepingScreenOn() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }


    private int randomNumberGenerator(int start, int end, Random rGen) {
        if (start < 2) {
            start = 2;
        }
        int range = (end - start + 1);
        double fraction = range * rGen.nextDouble();

        return (int) (fraction + start);
    }

    private void drawHand() {
        hand = new Card[8];
        for (int i = 0; i < 8; i++) {
            hand[i] = deck.drawCard();
            setCardImage(imageCard[i], hand[i].getRank(), hand[i].getSuit(), true);
        }
    }

    private void setCardImage(ImageView card, int rank, int suit, Boolean isHandCard){

        /*String cardImageURL = Images.getImageURL(rank, suit, isHandCard);

        Picasso.with(this)
                .load(cardImageURL)
                .into(card);
        */

        if (suit == 0) {
            switch (rank) {
                case 1:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_0001);
                    }else {
                        card.setImageResource(R.drawable.banana_0001);
                    }
                    break;
                case 2:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_0010);
                    }else {
                        card.setImageResource(R.drawable.banana_0010);
                    }
                    break;
                case 3:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_0011);
                    }else {
                        card.setImageResource(R.drawable.banana_0011);
                    }
                    break;
                case 4:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_0100);
                    }else {
                        card.setImageResource(R.drawable.banana_0100);
                    }
                    break;
                case 5:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_0101);
                    }else {
                        card.setImageResource(R.drawable.banana_0101);
                    }
                    break;
                case 6:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_0110);
                    }else {
                        card.setImageResource(R.drawable.banana_0110);
                    }
                    break;
                case 7:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_0111);
                    }else {
                        card.setImageResource(R.drawable.banana_0111);
                    }
                    break;
                case 8:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_1000);
                    }else {
                        card.setImageResource(R.drawable.banana_1000);
                    }
                    break;
                case 9:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_1001);
                    }else {
                        card.setImageResource(R.drawable.banana_1001);
                    }
                    break;
                case 10:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_a);
                    }else {
                        card.setImageResource(R.drawable.banana_a);
                    }
                    break;
                case 11:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_b);
                    }else {
                        card.setImageResource(R.drawable.banana_b);
                    }
                    break;
                case 12:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_c);
                    }else {
                        card.setImageResource(R.drawable.banana_c);
                    }
                    break;
                case 13:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_d);
                    }else {
                        card.setImageResource(R.drawable.banana_d);
                    }
                    break;
                case 14:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_e);
                    }else {
                        card.setImageResource(R.drawable.banana_e);
                    }
                    break;
                case 15:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_banana_f);
                    }else {
                        card.setImageResource(R.drawable.banana_f);
                    }
                    break;
            }
        } else if (suit == 1) {
            switch (rank) {
                case 1:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_0001);
                    }else {
                        card.setImageResource(R.drawable.cat_0001);
                    }
                    break;
                case 2:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_0010);
                    }else {
                        card.setImageResource(R.drawable.cat_0010);
                    }
                    break;
                case 3:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_0011);
                    }else {
                        card.setImageResource(R.drawable.cat_0011);
                    }
                    break;
                case 4:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_0100);
                    }else {
                        card.setImageResource(R.drawable.cat_0100);
                    }
                    break;
                case 5:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_0101);
                    }else {
                        card.setImageResource(R.drawable.cat_0101);
                    }
                    break;
                case 6:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_0110);
                    }else {
                        card.setImageResource(R.drawable.cat_0110);
                    }
                    break;
                case 7:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_0111);
                    }else {
                        card.setImageResource(R.drawable.cat_0111);
                    }
                    break;
                case 8:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_1000);
                    }else {
                        card.setImageResource(R.drawable.cat_1000);
                    }
                    break;
                case 9:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_1001);
                    }else {
                        card.setImageResource(R.drawable.cat_1001);
                    }
                    break;
                case 10:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_a);
                    }else {
                        card.setImageResource(R.drawable.cat_a);
                    }
                    break;
                case 11:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_b);
                    }else {
                        card.setImageResource(R.drawable.cat_b);
                    }
                    break;
                case 12:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_c);
                    }else {
                        card.setImageResource(R.drawable.cat_c);
                    }
                    break;
                case 13:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_d);
                    }else {
                        card.setImageResource(R.drawable.cat_d);
                    }
                    break;
                case 14:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_e);
                    }else {
                        card.setImageResource(R.drawable.cat_e);
                    }
                    break;
                case 15:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cat_f);
                    }else {
                        card.setImageResource(R.drawable.cat_f);
                    }
                    break;
            }
        } else if (suit == 2) {
            switch (rank) {
                case 1:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_0001);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_0001);
                    }
                    break;
                case 2:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_0010);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_0010);
                    }
                    break;
                case 3:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_0011);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_0011);
                    }
                    break;
                case 4:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_0100);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_0100);
                    }
                    break;
                case 5:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_0101);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_0101);
                    }
                    break;
                case 6:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_0110);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_0110);
                    }
                    break;
                case 7:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_0111);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_0111);
                    }
                    break;
                case 8:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_1000);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_1000);
                    }
                    break;
                case 9:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_1001);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_1001);
                    }
                    break;
                case 10:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_a);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_a);
                    }
                    break;
                case 11:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_b);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_b);
                    }
                    break;
                case 12:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_c);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_c);
                    }
                    break;
                case 13:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_d);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_d);
                    }
                    break;
                case 14:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_e);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_e);
                    }
                    break;
                case 15:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_philosoraptor_f);
                    }else {
                        card.setImageResource(R.drawable.philosoraptor_f);
                    }
                    break;
            }
        } else if (suit == 3) {
            switch (rank) {
                case 1:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_0001);
                    }else {
                        card.setImageResource(R.drawable.cursor_0001);
                    }
                    break;
                case 2:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_0010);
                    }else {
                        card.setImageResource(R.drawable.cursor_0010);
                    }
                    break;
                case 3:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_0011);
                    }else {
                        card.setImageResource(R.drawable.cursor_0011);
                    }
                    break;
                case 4:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_0100);
                    }else {
                        card.setImageResource(R.drawable.cursor_0100);
                    }
                    break;
                case 5:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_0101);
                    }else {
                        card.setImageResource(R.drawable.cursor_0101);
                    }
                    break;
                case 6:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_0110);
                    }else {
                        card.setImageResource(R.drawable.cursor_0110);
                    }
                    break;
                case 7:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_0111);
                    }else {
                        card.setImageResource(R.drawable.cursor_0111);
                    }
                    break;
                case 8:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_1000);
                    }else {
                        card.setImageResource(R.drawable.cursor_1000);
                    }
                    break;
                case 9:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_1001);
                    }else {
                        card.setImageResource(R.drawable.cursor_1001);
                    }
                    break;
                case 10:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_a);
                    }else {
                        card.setImageResource(R.drawable.cursor_a);
                    }
                    break;
                case 11:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_b);
                    }else {
                        card.setImageResource(R.drawable.cursor_b);
                    }
                    break;
                case 12:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_c);
                    }else {
                        card.setImageResource(R.drawable.cursor_c);
                    }
                    break;
                case 13:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_d);
                    }else {
                        card.setImageResource(R.drawable.cursor_d);
                    }
                    break;
                case 14:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_e);
                    }else {
                        card.setImageResource(R.drawable.cursor_e);
                    }
                    break;
                case 15:
                    if(isHandCard){
                        card.setImageResource(R.drawable.sq_cursor_f);
                    }else {
                        card.setImageResource(R.drawable.cursor_f);
                    }
                    break;
            }
        }
    }

    void playerWon(String winner){
        gameFinished = true;
        AlertDialog timeUP = new AlertDialog.Builder(MainActivity.this).create();
        timeUP.setCanceledOnTouchOutside(false);
        timeUP.setCancelable(false);
        timeUP.setTitle("Winner");
        if(winner.equals("P1")){
            timeUP.setMessage("You win!");
        }
        else {
            timeUP.setMessage(winner + " wins!");
        }
        timeUP.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //switchToMainScreen();
                        leaveRoom();
                        dialog.dismiss();
                    }
                });
        timeUP.show();
    }

    void deckEmptyWinner(){
        Set<String> winner = new HashSet<String>();
        int currentLowHand = 8;
        for(Participant p : mParticipants){
            String pid = p.getParticipantId();
            if (p.getStatus() != Participant.STATUS_JOINED)
                continue;
            int handScore = mParticipantScore.containsKey(pid) ? mParticipantScore.get(pid) : 8;

            if(handScore == currentLowHand){
                winner.add(p.getDisplayName());
            }
            if(handScore < currentLowHand){
                winner.clear();
                winner.add(p.getDisplayName());
                currentLowHand = handScore;
            }
        }
        if (cardLeftInHand() == currentLowHand){
            String fullWinners = "You";
            for(String s : winner){
                fullWinners += " and " + s;
            }

            playerWon(fullWinners);
        }
        else if(cardLeftInHand() < currentLowHand){
            playerWon("P1");
        }
        else{
            String fullWinners = "";
            for(String s : winner){
                fullWinners +=  s + ", ";
            }
            playerWon(fullWinners);
        }
    }

    String[] getSortedParticipantID(){
        int numPlayers = mParticipants.size();
        String[] participantsID = new String[numPlayers];
        for (int i = 0 ; i < numPlayers ; i++){
            participantsID[i] = mParticipants.get(i).getParticipantId();
        }
       Arrays.sort(participantsID);
       return participantsID;
    }

    void setUpMultiplayerHand(){
        String[] participantsID = getSortedParticipantID();
        int numPlayers = participantsID.length;
        int index = 0;

        for(int i = 0; i < participantsID.length ; i++){
            if (participantsID[i].equals(mMyId)){
                index = i;
            }
        }

        switch (index){
            case 0:
                drawHand();
                for(int i = 0 ; i < numPlayers-1; i++){
                    wasteCards(8);
                }
                break;
            case 1:
                wasteCards(8);
                drawHand();
                for(int i = 0 ; i < numPlayers-2; i++){
                    wasteCards(8);
                }
                break;
            case 2:
                wasteCards(16);
                drawHand();
                for(int i = 0 ; i < numPlayers-3; i++){
                    wasteCards(8);
                }
                break;
            case 3:
                wasteCards(24);
                drawHand();
                break;
        }
    }

    void wasteCards(int x){
        Card temp;
        for(int i = 0 ; i < x ; i++){
            temp = deck.drawCard();
            //Log.d(TAkG, "temp: " + temp.getRank() +" " + temp.getSuit());
        }
    }

    boolean amIKing(){
        String[] participantsID = getSortedParticipantID();
        return (participantsID[0].equals(mMyId));
    }

    //coverts the long seed to bytes for sending as message
    public static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= 8;
        }
        return result;
    }
    //converts the byte[] to long seed
    public static long bytesToLong(byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }

}
