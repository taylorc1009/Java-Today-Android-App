/*
*       40398643 | Taylor Courtney
*  TODO
*   Add a task to detect when permission requests complete + request multiple permissions at once
*   Add a task to detect when the devices location has been retrieved
*   Fix alarm not ringing on app kill
*   Add tabs? Home and Alarms
*   Add API keys to a JSON file and encrypt it?
*   - Could do the same with the google-services.json
*   It would be cool to make the weather details do the slide up with scroll down animation you see in, for example, Spotify playlist titles
*   Get reminders from calendar
*   Fix EditText handle colours in sign in activity
*   Have the calendar display event locations
* */

package com.app.today.PresentationLayer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.HandlerThread;
import android.view.View;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.util.Log;
import com.androdocs.httprequest.HttpRequest;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import com.app.today.BusinessLayer.CalendarContentResolver;
import com.app.today.BusinessLayer.Event;
import com.app.today.BusinessLayer.Headline;
import com.app.today.PresentationLayer.Utilities.HeadlineAdapter;
import com.app.today.BusinessLayer.HeadlineReceiver;
import com.app.today.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import androidx.viewpager2.widget.ViewPager2;
import me.relex.circleindicator.CircleIndicator3;

public class MainActivity extends AppCompatActivity {
    //Used to determine which permission we're asking for
    static final int MY_PERMISSIONS_REQUEST_READ_CALENDAR = 0;
    static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    //UI attributes
    TextView lastWUpdateTxt, forecastTxt, highsTxt, lowsTxt, temperatureTxt, windTxt, calTitle, newsTitle, headlineText;
    ImageView alarmMore, weatherIcon, logOut, refresh, headlineThumb;
    TableLayout calTable;
    ViewPager2 headlinePager;

    //Used to get and store weather details
    private static final String WEATHER_API = "2a2d2e85e492fe3c92b568f4fe3ce854";

    //Used to verify Firebase sign in
    private static FirebaseAuth mAuth = FirebaseAuth.getInstance();
    protected FirebaseUser user;

    //Used to store retrieved headlines
    private ArrayList<Headline> headlines = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Acquires the current user and determines whether they are logged in or not
        user = mAuth.getCurrentUser();

        if(user == null) {
            //If not, go to sign in page
            Intent alarmActivity = new Intent(MainActivity.this, SignInActivity.class);
            startActivity(alarmActivity);
            finish();
        } else {
            //If they are, begin initializing the homepage
            Log.i("! user is signed in", Objects.requireNonNull(user.getEmail()));

            //Initialize UI attributes
            weatherIcon = findViewById(R.id.weatherIcon);
            lastWUpdateTxt = findViewById(R.id.lastWUpdate);
            forecastTxt = findViewById(R.id.forecast);
            highsTxt = findViewById(R.id.highsTxt);
            lowsTxt = findViewById(R.id.lowsTxt);
            temperatureTxt = findViewById(R.id.temperature);
            windTxt = findViewById(R.id.windSpeed);
            calTitle = findViewById(R.id.calTitle);
            calTable = findViewById(R.id.calTable);
            alarmMore = findViewById(R.id.alarmIcon);
            newsTitle = findViewById(R.id.newsTitle);
            headlinePager = findViewById(R.id.headlinePager);
            headlineText = findViewById(R.id.headlineText);
            headlineThumb = findViewById(R.id.headlineThumb);
            logOut = findViewById(R.id.logOut);
            refresh = findViewById(R.id.refresh);

            //Request required permissions then begin UI operations
            // << permission request Task should be added here >> should probably put this in onStart then?
            if(reqPermission(MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION))
                new weatherTask().execute();
            if(reqPermission(MY_PERMISSIONS_REQUEST_READ_CALENDAR))
                updateCalendarView();
            new headlineReceiver().execute();

            //If the user clicks the Alarms "+", go to AlarmSystem
            alarmMore.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent alarmActivity = new Intent(MainActivity.this, AlarmSystem.class);
                    startActivity(alarmActivity);
                }
            });
            logOut.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //Signs the user out and returns them to the sign in page
                    mAuth.signOut();
                    Intent signIn = new Intent(MainActivity.this, SignInActivity.class);
                    startActivity(signIn);
                    finish();
                }
            });
            refresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //Refreshes the weather (requests location access beforehand)
                    if(reqPermission(MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)) {
                        new weatherTask().execute();
                        //Toast.makeText(MainActivity.this, "! INFO: if weather refresh fails, there's no new data to pull or the API request limit for today has been reached", Toast.LENGTH_LONG).show();
                    }
                    //Refreshes the calendar (requests location access beforehand)
                    if(reqPermission(MY_PERMISSIONS_REQUEST_READ_CALENDAR))
                        updateCalendarView();
                    //Refreshes the news headlines
                    new headlineReceiver().execute();
                }
            });
        }
    }

    //AsyncTask for getting the weather, this is used to determine actions for before (onPreExecute) and after (onPostExecute) another
    //action (doInBackground, which as the name implies can be done in the background)
    class weatherTask extends AsyncTask<String, Void, String> {
        //Attributes defined globally for this subclass so they can be accessed by inner classes
        private double longitude, latitude;

        //onPreExecute instructs the UI to show that the weather is loading
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            updateWeatherView(View.VISIBLE, View.GONE, View.GONE);
        }

        //doInBackground gets the users location then uses it to retrieve the weather in their location
        @Override
        protected String doInBackground(String... args) {
            final LocationListener locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    longitude = location.getLongitude();
                    latitude = location.getLatitude();
                }
                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override
                public void onProviderEnabled(String provider) {}
                @Override
                public void onProviderDisabled(String provider) {}
            };

            //The GPS permission is required for the completion of weather retrieval in the current location
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                //Creates an instance of the location service and retrieves the last know location
                //We do this so the system can determine later if there's a location update, and if
                //there is, request a weather update for the new location
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                assert lm != null;
                HandlerThread t = new HandlerThread("handlerThread");
                t.start();
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 10, locationListener, t.getLooper());
                Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                try {
                    assert location != null;
                    longitude = location.getLongitude();
                    latitude = location.getLatitude();
                    Log.i("? latitude, longitude", latitude + ", " + longitude);
                    //t.quit(); <-- causes a dead thread warning, critical?
                    return HttpRequest.excuteGet("https://api.openweathermap.org/data/2.5/weather?lat=" + latitude + "&lon=" + longitude + "&units=metric&APPID=" + WEATHER_API);
                } catch (NullPointerException e) {
                    Log.e("? NullPointerException", ".getLongitude()/.getLatitude() returned 'null'", e);
                    return null;
                }
            }
            return null;
        }

        //onPostExecute basically handles the weather response:
        @Override
        protected void onPostExecute(String result) {
            //Detects whether or not we actually got a result
            if (result != null) {
                //Try to update the UI using the results in the JSON file
                //Else show the error message (in the catch)
                try {
                    //Stores the .json result
                    JSONObject jsonObj = new JSONObject(result);

                    //Organises the results into suitable Objects
                    JSONObject main = jsonObj.getJSONObject("main");
                    JSONObject wind = jsonObj.getJSONObject("wind");
                    JSONObject weather = jsonObj.getJSONArray("weather").getJSONObject(0);
                    long updatedAt = jsonObj.getLong("dt");

                    List<String> weatherDetails = new ArrayList<>();

                    //Add the objects main values into a list of strings to be displayed
                    weatherDetails.add(weather.getString("description"));

                    //Round numerical values to display a whole number
                    weatherDetails.add(Math.round(Double.parseDouble(main.getString("temp"))) + "°C");
                    weatherDetails.add(Math.round(Double.parseDouble(main.getString("temp_max"))) + "°C");
                    weatherDetails.add(Math.round(Double.parseDouble(main.getString("temp_min"))) + "°C");
                    weatherDetails.add(Math.round(Double.parseDouble(wind.getString("speed"))) + " MPH WINDS");

                    weatherDetails.add("last updated: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH).format(new Date(updatedAt * 1000)));

                    int weatherID = Integer.parseInt(weather.getString("id"));
                    //Based on the weather ID, this will determine which drawable weather icon to use
                    if (weatherID == 800)
                        weatherIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.clear));
                    else if (weatherID == 801)
                        weatherIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.fair));
                    else if (weatherID == 802)
                        weatherIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.lightclouds));
                    else if (weatherID == 803 || weatherID == 804)
                        weatherIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.clouds));
                    else if (weatherID >= 500 && weatherID <= 504)
                        weatherIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.lightrain));
                    else if (weatherID == 511 || (weatherID >= 600 && weatherID <= 602) || (weatherID >= 611 && weatherID <= 613) || weatherID == 615 || weatherID == 616 || (weatherID >= 620 && weatherID <= 622))
                        weatherIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ice));
                    else if ((weatherID >= 520 && weatherID <= 522) || weatherID == 531 || (weatherID >= 300 && weatherID <= 302) || (weatherID >= 310 && weatherID <= 314) || weatherID == 321)
                        weatherIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.rain));
                    else if ((weatherID >= 200 && weatherID <= 202) || (weatherID >= 210 && weatherID <= 212) || weatherID == 221 || (weatherID >= 230 && weatherID <= 232))
                        weatherIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.storm));
                    else if (weatherID == 701 || weatherID == 711 || weatherID == 721 || weatherID == 731 || weatherID == 741 || weatherID == 751 || weatherID == 761 || weatherID == 762 || weatherID == 771 || weatherID == 781)
                        weatherIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.atmosphere));
                    else
                        weatherIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.info));

                    forecastTxt.setText(weatherDetails.get(0).toUpperCase());
                    temperatureTxt.setText(weatherDetails.get(1));
                    highsTxt.setText(weatherDetails.get(2));
                    lowsTxt.setText(weatherDetails.get(3));
                    windTxt.setText(weatherDetails.get(4));
                    lastWUpdateTxt.setText(weatherDetails.get(5));
                    updateWeatherView(View.GONE, View.VISIBLE, View.GONE);
                } catch (JSONException e) {
                    Log.e("? weather JSONException", ".json empty?", e);
                    updateWeatherView(View.GONE, View.GONE, View.VISIBLE);
                }
            }
            else
                updateWeatherView(View.GONE, View.GONE, View.VISIBLE);
        }
    }

    //Used to hide/show certain views based on the parameters received
    private void updateWeatherView(int load, int group, int error) {
        findViewById(R.id.weatherLoad).setVisibility(load);
        findViewById(R.id.weatherStats).setVisibility(group);
        findViewById(R.id.weatherError).setVisibility(error);
    }

    //Used to handle the response from the CalendarContentResolver
    private void updateCalendarView() {
        //First, clear the table of calendar events in the UI
        calTable.removeAllViews();

        //Create a CalendarContentResolver to query and return the events of the calendar
        CalendarContentResolver resolver = new CalendarContentResolver();
        List<Event> calendar = resolver.getCalendar(this);

        //If the calendar permission was granted, then this should continue
        //Else show error message
        if(calendar != null) {
            //If there are events happening today, add them to the UI table
            //Else show there are no events today
            if(!calendar.isEmpty()) {
                calTitle.setText(R.string.calTitle);

                for(Event event : calendar)
                    createCalendarTableRow(event.getTitle(), event.getDuration(), event.getDescription());

                calTable.setLayoutParams(new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT));

                /* This should be used to fix the layout constraints as for some reason they're removed after using 'view.setLayoutParams'
                Instead, I've cheated and put the table in its own ConstraintLayout - this isn't the most practical fix
                ConstraintSet setLayout = new ConstraintSet();
                setLayout.clone((ConstraintLayout) calTable.getParent());
                setLayout.connect(findViewById(R.id.calIcon).getId(), ConstraintSet.BOTTOM, calTable.getId(), ConstraintSet.TOP);
                setLayout.connect(((ConstraintLayout) calTable.getParent()).getId(), ConstraintSet.START, calTable.getId(), ConstraintSet.START);
                setLayout.applyTo((ConstraintLayout) calTable.getParent());*/
            } else
                calTitle.setText(R.string.calEmpty);
        }
        else
            calTitle.setText(R.string.calError);
    }

    private void createCalendarTableRow(String title, String duration, String description) {
        String output;

        //Creates a new TableRow to be added to the table
        final TableRow eventRow = new TableRow(getApplicationContext());

        final CardView card = new CardView(getApplicationContext());
        TableRow.LayoutParams paramsCrd = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT, 1.0f);
        paramsCrd.setMargins(8, 8, 8, 8);
        card.setLayoutParams(paramsCrd);
        card.setRadius(16);

        final ConstraintLayout constraintLayout = new ConstraintLayout(getApplicationContext());
        constraintLayout.setId(20+calTable.getChildCount());
        CardView.LayoutParams cParams = new CardView.LayoutParams(CardView.LayoutParams.MATCH_PARENT, CardView.LayoutParams.WRAP_CONTENT);
        cParams.setMargins(8, 8, 8, 8);
        constraintLayout.setLayoutParams(cParams);

        //Creates and defines a TextView to display an event
        TextView eventTxt = new TextView(getApplicationContext());
        eventTxt.setId(21+calTable.getChildCount());
        output = title + " | " + duration;
        eventTxt.setText(output);
        eventTxt.setTextSize(16);
        eventTxt.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.black));
        eventTxt.setTypeface(null, Typeface.BOLD);
        constraintLayout.addView(eventTxt);

        final TextView eventDesc = new TextView(getApplicationContext());
        eventDesc.setId(22+calTable.getChildCount());
        output = description;
        eventDesc.setText(output);
        eventDesc.setTextSize(15);
        eventDesc.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.black));
        eventDesc.setTypeface(null, Typeface.BOLD_ITALIC);
        eventDesc.setAlpha(0);
        eventDesc.setVisibility(View.GONE);
        constraintLayout.addView(eventDesc);

        final ImageView expand = new ImageView(getApplicationContext());
        expand.setId(23+calTable.getChildCount());
        expand.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.expand));
        expand.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.colorSecondary));
        ConstraintLayout.LayoutParams paramsImg = new ConstraintLayout.LayoutParams(40, 40);
        paramsImg.setMargins(0, 0, 8, 0);
        constraintLayout.addView(expand, 48, 48);

        expand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(eventDesc.getVisibility() == View.GONE) {
                    expand.animate().rotation(180);
                    eventDesc.setVisibility(View.VISIBLE);
                    eventDesc.animate().alpha(1).setDuration(300).setListener(null);
                }
                else {
                    expand.animate().rotation(0);
                    eventDesc.animate().alpha(0).setDuration(300).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            eventDesc.setVisibility(View.GONE);
                        }
                    });
                }
            }
        });

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(constraintLayout);

        constraintSet.connect(expand.getId(), ConstraintSet.END, constraintLayout.getId(), ConstraintSet.END, 8);
        constraintSet.connect(expand.getId(), ConstraintSet.TOP, eventTxt.getId(), ConstraintSet.TOP);
        constraintSet.connect(expand.getId(), ConstraintSet.BOTTOM, eventTxt.getId(), ConstraintSet.BOTTOM);

        constraintSet.connect(eventTxt.getId(), ConstraintSet.START, constraintLayout.getId(), ConstraintSet.START, 8);
        constraintSet.connect(eventTxt.getId(), ConstraintSet.TOP, constraintLayout.getId(), ConstraintSet.TOP);
        constraintSet.connect(eventTxt.getId(), ConstraintSet.END, expand.getId(), ConstraintSet.START, 8);
        constraintSet.setHorizontalBias(eventTxt.getId(), 0f);

        constraintSet.connect(eventDesc.getId(), ConstraintSet.START, constraintLayout.getId(), ConstraintSet.START, 8);
        constraintSet.connect(eventDesc.getId(), ConstraintSet.TOP, eventTxt.getId(), ConstraintSet.BOTTOM, 8);
        constraintSet.connect(eventDesc.getId(), ConstraintSet.END, constraintLayout.getId(), ConstraintSet.END, 8);
        constraintSet.setHorizontalBias(eventDesc.getId(), 0f);

        constraintSet.setHorizontalWeight(eventTxt.getId(), 1.0f);
        constraintSet.setHorizontalWeight(eventDesc.getId(), 1.0f);

        constraintSet.applyTo(constraintLayout);

        card.addView(constraintLayout);
        eventRow.addView(card);
        calTable.addView(eventRow, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
    }

    //AsyncTask for getting headlines
    class headlineReceiver extends AsyncTask<String, Void, List<Headline>> {
        //onPreExecute shows the user the weather is loading
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            updateHeadlinesView(View.VISIBLE, View.GONE);
        }

        //doInBackground retrieves headlines and passes them to onPostExecute
        @Override
        protected List<Headline> doInBackground(String... strings) {
            return HeadlineReceiver.getHeadlines();
        }

        //onPostExecutes shows the results to the user
        @Override
        protected void onPostExecute(List<Headline> result) {
            //Clean the headlines List before new data is added
            if(headlines != null && !headlines.isEmpty())
                headlines.clear();

            //If results aren't empty, display them in the headline table
            //Else show headline error
            if(result != null) {
                //Integer used just to label the headlines with a number
                for(Headline headline : result) {
                    try {
                        assert headlines != null;
                        headlines.add(headline);
                    } catch(NullPointerException e) {
                        Log.e("? headlines ArrayList result equals null", e.toString());
                    }
                }
                updateArticles();
                newsTitle.setText(R.string.newsTitle);
                updateHeadlinesView(View.GONE, View.VISIBLE);
            } else {
                newsTitle.setText(R.string.newsError);
                updateHeadlinesView(View.GONE, View.GONE);
            }
        }
    }

    //TODO you should probably take a look at the bitmaps lecture to make this more memory efficient?
    private void updateArticles() {
        //headlinePager.removeAllViews(); <-- ViewPager2 seems to do this automatically?

        HeadlineAdapter adapter = new HeadlineAdapter(this, headlines);
        headlinePager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        headlinePager.setAdapter(adapter);
        headlinePager.setOffscreenPageLimit(3);

        headlinePager.setPageTransformer(new ViewPager2.PageTransformer() {
            @Override
            public void transformPage(@NonNull View page, float position) {
                float offset = position * - 30;
                if (position < -1) {
                    page.setTranslationX(-offset);
                } else if (position <= 1) {
                    float scaleFactor = Math.max(0.7f, 1 - Math.abs(position));
                    page.setTranslationX(offset);
                    page.setScaleY(scaleFactor);
                    page.setAlpha(scaleFactor);
                } else {
                    page.setAlpha(0);
                    page.setTranslationX(offset);
                }

                // NO ZOOM TRANSITION
                /*if (headlinePager.getOrientation() == ViewPager2.ORIENTATION_HORIZONTAL)
                    if (ViewCompat.getLayoutDirection(headlinePager) == ViewCompat.LAYOUT_DIRECTION_RTL)
                        page.setTranslationX(-offset);
                    else
                        page.setTranslationX(offset);
                else
                    page.setTranslationY(offset);*/
            }
        });

        CircleIndicator3 indicator = findViewById(R.id.headlineIndicator);
        indicator.setViewPager(headlinePager);
        adapter.registerAdapterDataObserver(indicator.getAdapterDataObserver());
    }

    //Used to hide/show certain views based on the parameters
    private void updateHeadlinesView(int load, int headline) {
        findViewById(R.id.newsLoad).setVisibility(load);
        findViewById(R.id.newsHeading).setVisibility(headline);
        findViewById(R.id.headlinePager).setVisibility(headline);
    }

    //Used by the app to request a permission (p)
    private boolean reqPermission(int p) {
        //Defines the permission we're looking for
        String per;
        switch (p) {
            case MY_PERMISSIONS_REQUEST_READ_CALENDAR:
                per = Manifest.permission.READ_CALENDAR;
                break;
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION:
                per = Manifest.permission.ACCESS_FINE_LOCATION;
                break;
            default:
                return false;
        }

        //If the permission is not already granted, request it
        //Else return true/granted
        if (ContextCompat.checkSelfPermission(this, per) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{per}, MY_PERMISSIONS_REQUEST_READ_CALENDAR);

            //Checks again after the request was made to check the result
            return ContextCompat.checkSelfPermission(this, per) == PackageManager.PERMISSION_GRANTED;
        } else
            return true;
    }
}