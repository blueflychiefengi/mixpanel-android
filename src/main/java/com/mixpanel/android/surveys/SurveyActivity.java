package com.mixpanel.android.surveys;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.mixpanel.android.R;
import com.mixpanel.android.mpmetrics.MixpanelAPI;
import com.mixpanel.android.mpmetrics.Survey;
import com.mixpanel.android.mpmetrics.Survey.Question;

public class SurveyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDistinctId = getIntent().getStringExtra("distinctId");
        mToken = getIntent().getStringExtra("token");
        final String surveyJsonStr = getIntent().getStringExtra("surveyJson");
        final byte[] backgroundCompressed = getIntent().getByteArrayExtra("backgroundCompressed");

        // At some point, we will want to use the  brand color as a custom highlight for
        // textareas and selection
        @SuppressWarnings("unused")
        final int highlightColor = getIntent().getIntExtra("highlightColor", Color.WHITE);

        setContentView(R.layout.com_mixpanel_android_activity_survey);
        Bitmap background;
        if (null != backgroundCompressed) {
            background = BitmapFactory.decodeByteArray(backgroundCompressed, 0, backgroundCompressed.length);
            getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), background));
        } else {
            final View contentView = this.findViewById(R.id.com_mixpanel_android_activity_survey_id);
            contentView.setBackgroundColor(Color.argb(255, 90, 90, 90));
        }
        mPreviousButton = findViewById(R.id.button_previous);
        mNextButton = findViewById(R.id.button_next);
        mProgressTextView = (TextView) findViewById(R.id.progress_text);
        mCardHolder = (CardCarouselLayout) findViewById(R.id.question_card_holder);
        mCardHolder.setOnQuestionAnsweredListener(new CardCarouselLayout.OnQuestionAnsweredListener() {
            @Override
            public void onQuestionAnswered(Question question, String answer) {
                saveAnswer(question, answer);
                goToNextQuestion();
            }
        });

        // identify the person we're saving answers for TODO RACE CONDITION NEED DIRECT INSTANCE LOOKUP
        mMixpanel = MixpanelAPI.getInstance(this, mToken); // TODO CANT DO THIS. You've gotta make sure you use the same instance? But threads?
        mMixpanel.getPeople().identify(mDistinctId);
        try {
            mSurvey = new Survey(new JSONObject(surveyJsonStr));
            mAnswers = new HashMap<Survey.Question, String>();
        } catch (final JSONException e) {
            // TODO can't merge without doing something useful here.
            Log.e(LOGTAG, "Unable to parse survey json: " + surveyJsonStr, e);
        }

        // TODO For testing only, uncomment before merge
        // mMixpanel.getPeople().append("$surveys", mSurvey.getId());
        // mMixpanel.getPeople().append("$collections", mSurvey.getCollectionId());
        // mMixpanel.flush();
        showQuestion(0);
    }

    @Override
    public void onBackPressed() {
        if (mCurrentQuestion > 0) {
            goToPreviousQuestion();
        } else {
            super.onBackPressed();
        }
    }

    public void goToPreviousQuestion(View v) {
        goToPreviousQuestion();
    }

    public void goToNextQuestion(View v) {
        goToNextQuestion();
    }

    public void completeSurvey(View v) {
        completeSurvey();
    }

    @Override
    protected void onDestroy() {
        mMixpanel.flush();
        super.onDestroy();
    }

    private void goToPreviousQuestion() {
        if (mCurrentQuestion > 0) {
            showQuestion(mCurrentQuestion-1);
        } else {
            completeSurvey();
        }
    }

    private void goToNextQuestion() {
        if (mCurrentQuestion < mSurvey.getQuestions().size()-1) {
            showQuestion(mCurrentQuestion+1);
        } else {
            completeSurvey();
        }
    }

    private void showQuestion(final int idx) {
        final List<Question> questions = mSurvey.getQuestions();
        if (0 == idx || questions.size() == 0) {
            mPreviousButton.setEnabled(false);
        } else {
           mPreviousButton.setEnabled(true);
        }
        if (idx >= questions.size() - 1) {
            mNextButton.setEnabled(false);
        } else {
            mNextButton.setEnabled(true);
        }
        final int oldQuestion = mCurrentQuestion;
        mCurrentQuestion = idx;
        final Survey.Question question = questions.get(idx);
        final String answerValue = mAnswers.get(question);
        try {
            if (oldQuestion < idx) {
                mCardHolder.moveTo(question, answerValue, CardCarouselLayout.Direction.FORWARD);
            } else if (oldQuestion > idx) {
                mCardHolder.moveTo(question, answerValue, CardCarouselLayout.Direction.BACKWARD);
            } else {
                mCardHolder.replaceTo(question, answerValue);
            }
        } catch(final CardCarouselLayout.UnrecognizedAnswerTypeException e) {
            goToNextQuestion();
            return;
        }

        mProgressTextView.setText("" + (idx + 1) + " of " + mSurvey.getQuestions().size());
    }

    @SuppressLint("SimpleDateFormat")
    private void saveAnswer(Survey.Question question, String answer) {
        mAnswers.put(question, answer.toString());
        mMixpanel.getPeople().append("$responses", mSurvey.getCollectionId()); // <<--- TODO should be $union

        try {
            final JSONObject answerJson = new JSONObject();
            answerJson.put("$survey_id", mSurvey.getId());
            answerJson.put("$collection_id", mSurvey.getCollectionId());
            answerJson.put("$question_id", question.getId());
            answerJson.put("$question_type", question.getType().toString());

            // TODO find a better way to share this format convention
            final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            answerJson.put("$time", dateFormat.format(new Date()));
            answerJson.put("$value", answer.toString());

            mMixpanel.getPeople().append("$answers", answerJson);
        } catch (final JSONException e) {
            Log.e(LOGTAG, "Couldn't record user's answer.", e);
        }
        mMixpanel.flush();
    }

    private void completeSurvey() {
        finish();
    }

    private MixpanelAPI mMixpanel;
    private View mPreviousButton;
    private View mNextButton;
    private Survey mSurvey;
    private String mDistinctId;
    private String mToken;
    private TextView mProgressTextView;
    private CardCarouselLayout mCardHolder;

    private Map<Survey.Question, String> mAnswers;
    private int mCurrentQuestion = 0;

    private static final String LOGTAG = "MixpanelAPI";
}
