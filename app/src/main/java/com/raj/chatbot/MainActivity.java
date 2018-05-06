package com.raj.chatbot;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.silvestrpredko.dotprogressbar.DotProgressBar;
import com.github.zagum.speechrecognitionview.RecognitionProgressView;
import com.github.zagum.speechrecognitionview.adapters.RecognitionListenerAdapter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.api.AIServiceException;
import ai.api.RequestExtras;
import ai.api.android.AIConfiguration;
import ai.api.android.AIDataService;
import ai.api.model.AIContext;
import ai.api.model.AIError;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Result;

public class MainActivity extends AppCompatActivity {
    private RecyclerView mRecyclerView;
    private static final String TAG = MainActivity.class.getSimpleName();

    private ImageView mActivateVoice;
    private ImageView mPlayVoice;
    private RecognitionProgressView mVoiceRecognizeView;
    DotProgressBar mDotProgressBar;
    private ImageView mActivateText;
    private SpeechRecognizer speechRecognizer;
    private EditText mEditQuery;
    ChatAdapter mAdapter;

    private AIDataService aiDataService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        wireControl();
        initService();
    }

    private void wireControl() {

        mActivateVoice = findViewById(R.id.img_activate_voice);
        mDotProgressBar = findViewById(R.id.dot_progress_bar);
        mPlayVoice = findViewById(R.id.img_play_voice);
        mActivateText = findViewById(R.id.img_activate_text);
        mVoiceRecognizeView = findViewById(R.id.voice_recognition_view);
        mEditQuery = (EditText) findViewById(R.id.edit_query);
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new ChatAdapter();
        mAdapter.addBotMsg("Welcome to Bot! How may I help you");
        mRecyclerView.setAdapter(mAdapter);

        mActivateVoice.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (v.getTag().toString().equals("voice")) {
                            hideKeyboard();
                            activateVoice();
                        } else {
                            hideKeyboard();
                            if (mEditQuery.getText().toString().trim().length() > 0)
                                sendUserRequest(mEditQuery.getText().toString().trim(), true);
                        }
                    }
                });
        mEditQuery.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    hideKeyboard();
                    if (mEditQuery.getText().toString().trim().length() > 0)
                        sendUserRequest(mEditQuery.getText().toString().trim(), true);
                    return true;
                }
                return false;
            }
        });

        mEditQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (!TextUtils.isEmpty(charSequence.toString())) {
                    mActivateVoice.setImageDrawable(getResources().getDrawable(R.drawable.ic_send));
                    mActivateVoice.setTag("text");
                } else {
                    mActivateVoice.setImageDrawable(getResources().getDrawable(R.drawable.ic_google_voice));
                    mActivateVoice.setTag("voice");
                }

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        mActivateText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mVoiceRecognizeView.stop();
                activateText();
            }
        });
        initVoiceProgressView();
        mPlayVoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                activateVoice();
            }
        });
        TTS.init(this);

    }

    public void hideKeyboard() {
        try {
            View view = getCurrentFocus();
            // Check if no view has focus:
            if (view != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }
        } catch (Exception e) {

        }
    }

    private void sendUserRequest(final String queryString, boolean isShowInChat) {

        mEditQuery.setText("");
        if (isShowInChat)
            addMySpeech(queryString);
        showAssistantWorking();

        final GetAIResponse getAIResponseTask = new GetAIResponse(aiDataService);
        getAIResponseTask.setListener(new GetAIResponse.Listener() {

            @Override
            public void onSuccess(AIResponse response, AIError error) {

                getAIResponseTask.setListener(null);

                stopAssistantWorking();
                if (response != null) {
                    onResult(response);
                } else {
                    onError(error);
                }
            }
        });

        getAIResponseTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, queryString);

    }

    private void addMySpeech(String mySpeech) {
        mAdapter.addMyChat(mySpeech);
        mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount() > 1 ? mAdapter.getItemCount() - 2 : 0);
    }

    private void addAssistantText(String assistantChat) {
        TTS.speak(assistantChat);
        if (null != assistantChat && assistantChat.length() > 0) {
            mAdapter.addBotMsg(assistantChat);
            mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount() > 1 ? mAdapter.getItemCount() - 2 : 0);
        }
    }

    public void onError(final AIError error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "Error from AI service" + error);
            }
        });
    }


    static class GetAIResponse extends AsyncTask<String, Void, AIResponse> {

        private AIError aiError;
        private Listener listener;
        WeakReference<AIDataService> weakaiDataService;

        public GetAIResponse(AIDataService aiDataService) {
            this.weakaiDataService = new WeakReference(aiDataService);
        }


        interface Listener {
            void onSuccess(AIResponse response, AIError error);
        }

        @Override
        protected AIResponse doInBackground(final String... params) {
            final AIRequest request = new AIRequest();
            String query = params[0];
            if (!TextUtils.isEmpty(query))
                request.setQuery(query);

            RequestExtras requestExtras = null;
            try {
                return weakaiDataService.get().request(request, requestExtras);
            } catch (final AIServiceException e) {
                aiError = new AIError(e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(final AIResponse response) {

            listener.onSuccess(response, aiError);
        }

        public void setListener(Listener listener) {
            this.listener = listener;
        }


    }
    private void initService() {
        final AIConfiguration config = new AIConfiguration("1ef88d95db9048119272c497fad800dc",
                AIConfiguration.SupportedLanguages.English,
                AIConfiguration.RecognitionEngine.System);
        aiDataService = new AIDataService(this, config);
    }
    public void onResult(final AIResponse response) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                final Result result = response.getResult();
                String speech = result.getFulfillment().getSpeech();
                addAssistantText(speech);

            }

        });
    }

    private void initVoiceProgressView() {

        int[] colors = {
                ContextCompat.getColor(this, R.color.color1),
                ContextCompat.getColor(this, R.color.color2),
                ContextCompat.getColor(this, R.color.color3),
                ContextCompat.getColor(this, R.color.color4),
                ContextCompat.getColor(this, R.color.color5)
        };

        int[] heights = {20, 24, 18, 23, 16};

        mVoiceRecognizeView.setSpeechRecognizer(speechRecognizer);
        mVoiceRecognizeView.setRecognitionListener(new RecognitionListenerAdapter() {
            @Override
            public void onResults(Bundle results) {

                mVoiceRecognizeView.stop();
                mVoiceRecognizeView.setVisibility(View.GONE);
                mPlayVoice.setVisibility(View.VISIBLE);
                showResults(results);

            }

        });
        mVoiceRecognizeView.setColors(colors);
        mVoiceRecognizeView.setBarMaxHeightsInDp(heights);
        mVoiceRecognizeView.setCircleRadiusInDp(2);
        mVoiceRecognizeView.setSpacingInDp(2);
        mVoiceRecognizeView.setIdleStateAmplitudeInDp(2);
        mVoiceRecognizeView.setRotationRadiusInDp(10);

        mVoiceRecognizeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mVoiceRecognizeView.stop();
                mVoiceRecognizeView.setVisibility(View.GONE);
                mPlayVoice.setVisibility(View.VISIBLE);
            }
        });
    }


    private void showResults(Bundle results) {
        ArrayList<String> matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        sendUserRequest(matches.get(0), true);
    }

    private void activateVoice() {
        TTS.stop();
        mVoiceRecognizeView.play();
        mVoiceRecognizeView.postDelayed(new Runnable() {
            @Override
            public void run() {
                startRecognition();
            }
        }, 50);

        mEditQuery.setVisibility(View.GONE);
        mActivateVoice.setVisibility(View.GONE);

        mVoiceRecognizeView.setVisibility(View.VISIBLE);
        mActivateText.setVisibility(View.VISIBLE);
        mPlayVoice.setVisibility(View.GONE);


    }

    private void activateText() {

        mEditQuery.setVisibility(View.VISIBLE);
        showKeyboard();
        mEditQuery.requestFocus();
        mActivateVoice.setVisibility(View.VISIBLE);

        mVoiceRecognizeView.setVisibility(View.GONE);
        mActivateText.setVisibility(View.GONE);
        mPlayVoice.setVisibility(View.GONE);
    }

    public void showKeyboard() {
        try {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startRecognition() {

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en_NZ");
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(6000));
        speechRecognizer.startListening(intent);
    }

    private void showAssistantWorking() {
        mDotProgressBar.setVisibility(View.VISIBLE);
    }

    private void stopAssistantWorking() {
        mDotProgressBar.setVisibility(View.GONE);
    }

}
