package de.stephanlindauer.criticalmass.twitter;


import android.app.Activity;
import android.os.AsyncTask;
import de.stephanlindauer.criticalmass.fragments.TwitterFragment;
import de.stephanlindauer.criticalmass.utils.AsyncCallback;
import de.stephanlindauer.criticalmass.utils.JSONUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import twitter4j.*;
import twitter4j.auth.AccessToken;

/**
 * Proxy implementation of twitter4j library
 */
public class TwitterApi implements ProxyApi {

    private TwitterStream twitterStream;
    private Twitter twitter;
    private final JSONObject appConfig;

    /**
     * Proxy constructor for Twitter4j library.
     *
     * @param context - Activity.
     */
    public TwitterApi(@NotNull final Activity context) {
        super();

        // load secrets
        appConfig = JSONUtils.loadJsonFromAssets(context, "app.json");
    }

    private void initTwitterStream() {
        if (twitterStream != null)
            return;
        twitterStream = new TwitterStreamFactory().getInstance();
        twitterStream.setOAuthConsumer(appConfig.optString("CONSUMER_KEY"), appConfig.optString("CONSUMER_SECRET"));
        twitterStream.setOAuthAccessToken(new AccessToken(appConfig.optString("ACCESS_TOKEN"), appConfig.optString("ACCESS_TOKEN_SECRET")));
    }

    private void initTwitter() {
        if (twitter != null)
            return;
        twitter = TwitterFactory.getSingleton();
        twitter.setOAuthConsumer(appConfig.optString("CONSUMER_KEY"), appConfig.optString("CONSUMER_SECRET"));
        twitter.setOAuthAccessToken(new AccessToken(appConfig.optString("ACCESS_TOKEN"), appConfig.optString("ACCESS_TOKEN_SECRET")));
    }

    /**
     * Connects to Twitter stream API and starts queering for tweets with provided hashtags array
     *
     * @param hashTag  arrays of hashtags for searching
     * @param listener listener that returns new found tweets
     */
    public void searchTweets(@NotNull final String[] hashTag, @NotNull final ITweetListener listener) {

        if (twitterStream == null)
            initTwitterStream();

        final StatusListener statusListener = new StatusListener() {

            @Override
            public void onStatus(final Status status) {
                listener.onNewTweet(new Tweet(status));
            }

            @Override
            public void onDeletionNotice(final StatusDeletionNotice statusDeletionNotice) {
                //ignored
            }

            @Override
            public void onTrackLimitationNotice(final int i) {
                //ignored
            }

            @Override
            public void onScrubGeo(final long l, final long l2) {
                //ignored
            }

            @Override
            public void onStallWarning(final StallWarning stallWarning) {
                //ignored
            }

            @Override
            public void onException(final Exception e) {
                listener.onException(e);
            }
        };

        twitterStream.addListener(statusListener);

        final FilterQuery fq = new FilterQuery();
        fq.track(hashTag);
        twitterStream.filter(fq);
    }

    public void searchTweetsAsync(@NotNull final String searchString, @NotNull final AsyncCallback cb) {

        if (twitter == null)
            initTwitter();


        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(final @Nullable Void... params) {

                Query query = new Query(searchString);
                try {
                    QueryResult result;
                    do {
                        query.setSince(TwitterFragment.TWITTER_SINCE);
                        query.setCount(TwitterFragment.TWITTER_MAX_FEED);
                        result = twitter.search(query);
                        cb.onComplete(result.getTweets());
                    } while ((query = result.nextQuery()) != null);
                } catch (final TwitterException e) {
                    cb.onException(e);
                }
                return null;
            }
        }.execute();
    }
}
