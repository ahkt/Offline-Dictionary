package com.android.priyanka.dictionary.view;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.priyanka.dictionary.R;
import com.android.priyanka.dictionary.adapter.RecyclerHistoryAdapter;
import com.android.priyanka.dictionary.database.DatabaseHelper;
import com.android.priyanka.dictionary.model.History;
import com.android.priyanka.dictionary.util.LoadDatabaseAsync;
import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.search_view)
    SearchView searchView;

    static DatabaseHelper databaseHelper;
    static boolean databaseOpened = false;

    SimpleCursorAdapter suggestionAdapter;

    ArrayList<History> historyArrayList;
    @BindView(R.id.empty_history)
    RelativeLayout emptyHistory;
    @BindView(R.id.recycler_history)
    RecyclerView recyclerHistory;
    RecyclerView.LayoutManager layoutManager;
    RecyclerView.Adapter historyAdapter;
    Cursor cursorHistory;

    Boolean doubleBackToExitPressedOnce = false;
    @BindView(R.id.adView)
    AdView adView;

    private FirebaseAnalytics firebaseAnalytics;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);

        try {
            MobileAds.initialize(this, "ca-app-pub-3940256099942544~3347511713");
        }
        catch(Exception e){
            System.out.println("ad exception"+e);
        }

        adView = findViewById(R.id.adView);

        //adView.setAdSize(AdSize.BANNER);
       // adView.setAdUnitId(getString(R.string.banner_home_footer));

        AdRequest adRequest = new AdRequest.Builder().addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();
        adView.loadAd(adRequest);

        databaseHelper = new DatabaseHelper(this);
        try {
            if (databaseHelper.checkDatabase()) {
                openDatabase();
            } else {
                //DB doesn't exist
                LoadDatabaseAsync task = new LoadDatabaseAsync(MainActivity.this);
                task.execute();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception main:110:" + e);

        }


        try {
            //setup simplecursoradapter

            final String[] from = new String[]{"en_word"};
            final int[] to = new int[]{R.id.suggestion_text};

            suggestionAdapter = new SimpleCursorAdapter(MainActivity.this, R.layout.suggestions_row, null, from, to, 0) {
                @Override
                public void changeCursor(Cursor cursor) {
                    super.swapCursor(cursor);
                }
            };
            searchView.setSuggestionsAdapter(suggestionAdapter);
        } catch (Exception e) {
            e.printStackTrace();


        }

        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int i) {
                // return false;    //
                return true;
            }

            @Override
            public boolean onSuggestionClick(int i) {

                //Add clicked text to search box
                try {
                    CursorAdapter ca = searchView.getSuggestionsAdapter();
                    Cursor c = ca.getCursor();
                    c.moveToPosition(i);

                    String clicked_word = c.getString(c.getColumnIndex("en_word"));
                    searchView.setQuery(clicked_word, false);

                    searchView.clearFocus();
                    searchView.setFocusable(false);
                    searchView.setQuery("", false);   //


                    Intent intent = new Intent(MainActivity.this, MeaningActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putString("en_word", clicked_word);
                    intent.putExtras(bundle);
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Exception main 140" + e);
                    Crashlytics.logException(e);
                }


                return true;
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                // This method will be called When the user hits enter or search icon
                try {
                    String text = searchView.getQuery().toString();

                    Pattern pattern = Pattern.compile("[A-Za-z \\-.]{1,25}");
                    Matcher matcher = pattern.matcher(s);

                    if (matcher.matches()) {
                        Cursor c = null;
                        try {

                            c = databaseHelper.getMeaning(text);

                        } catch (Exception e) {
                            e.printStackTrace();

                        }

                        //check if DB has the word entered by the user
                        if (c.getCount() == 0) {    //DB doesn't have the word entered by the user
                            showAlertDialog();

                        } else {
                            searchView.setQuery("", false);     //
                            searchView.clearFocus();
                            searchView.setFocusable(false);

                            Intent intent = new Intent(MainActivity.this, MeaningActivity.class);
                            Bundle bundle = new Bundle();
                            bundle.putString("en_word", text);
                            intent.putExtras(bundle);
                            startActivity(intent);

                        }

                    } else {
                        showAlertDialog();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Excepption main:206" + e);
                    Crashlytics.logException(e);
                }

                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {

                try {
                    searchView.setIconifiedByDefault(false);

                    Pattern pattern = Pattern.compile("[A-Za-z \\-.]{1,25}");
                    Matcher matcher = pattern.matcher(s);

                    if (matcher.matches()) {
                        Cursor cursor = databaseHelper.getSuggestions(s);
                        suggestionAdapter.changeCursor(cursor);  //to show suggestions whenever user enters new words

                    }

                } catch (Exception e) {
                    e.printStackTrace();
                     System.out.println("Exception search :" +e);
                     Crashlytics.logException(e);
                }


                return false;
            }
        });

        layoutManager = new LinearLayoutManager(MainActivity.this);
        recyclerHistory.setLayoutManager(layoutManager);

        fetchHistory();


    }

    private void fetchHistory() {
        try {
            historyArrayList = new ArrayList<>();
            historyAdapter = new RecyclerHistoryAdapter(historyArrayList, this);
            recyclerHistory.setAdapter(historyAdapter);

            History h;

            if (databaseOpened) {
                cursorHistory = databaseHelper.getHistory();

                if (cursorHistory.moveToFirst()) {

                    do {
                        String definition = cursorHistory.getString(cursorHistory.getColumnIndex("en_definition"));
                        definition = definition.substring(0, 1).toUpperCase() + definition.substring(1).toLowerCase();

                        h = new History(cursorHistory.getString(cursorHistory.getColumnIndex("word")), definition);
                        historyArrayList.add(h);

                    } while (cursorHistory.moveToNext());
                }

                historyAdapter.notifyDataSetChanged();

                if (historyAdapter.getItemCount() == 0) {

                    emptyHistory.setVisibility(View.VISIBLE);

                } else {

                    emptyHistory.setVisibility(View.GONE);


                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            Crashlytics.logException(e);
        }


    }


    //when user comes back from wordmeaning activity it should show the newly searched words in history list
    @Override
    protected void onResume() {
        super.onResume();
        try {
            fetchHistory();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected static void openDatabase() throws SQLiteException {

        try {
            databaseHelper.openDatabase();
            databaseOpened = true;

            Log.i("oooooo..", "" + databaseOpened);
        } catch (SQLiteException e) {
            e.printStackTrace();
            Crashlytics.getInstance().crash();


        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        switch (id) {

            case R.id.settings:
                Intent i = new Intent(this, SettingsActivity.class);
                startActivity(i);

                return true;


            case R.id.exit:
                System.exit(0);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @OnClick(R.id.search_view)
    public void onViewClicked() {

        searchView.setIconified(false);
       /* Intent i = new Intent(this,MeaningActivity.class);
        startActivity(i);*/
    }

    private void showAlertDialog() {

        try {
            searchView.setQuery("", false);

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.myDialogTheme);
            builder.setTitle("Word not found");
            builder.setMessage("Please search again");

            String positiveText = getString(android.R.string.ok);
            builder.setPositiveButton(positiveText, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });

            String negativeText = getString(android.R.string.cancel);
            builder.setNegativeButton(negativeText, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    searchView.clearFocus();

                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            Crashlytics.logException(e);
        }

    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {

            super.onBackPressed();
        }
        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(getApplicationContext(), "Press BACK again to exit", Toast.LENGTH_SHORT).show();


        Handler handler = new Handler();

        handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    doubleBackToExitPressedOnce = false;
                                }

                            }
                , 2000);


    }
}
