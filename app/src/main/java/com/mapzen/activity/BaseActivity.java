package com.mapzen.activity;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CursorAdapter;
import android.widget.SearchView;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bugsense.trace.BugSenseHandler;
import com.mapzen.AutoCompleteCursor;
import com.mapzen.MapzenApplication;
import com.mapzen.PoiLayer;
import com.mapzen.R;
import com.mapzen.entity.Place;
import com.mapzen.fragment.MapFragment;
import com.mapzen.fragment.SearchResultsFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.oscim.android.MapActivity;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.layers.marker.ItemizedIconLayer;
import org.oscim.layers.marker.MarkerItem;
import org.oscim.map.Map;

import static android.provider.BaseColumns._ID;
import static com.mapzen.MapzenApplication.LOG_TAG;
import static com.mapzen.MapzenApplication.PELIAS_LAT;
import static com.mapzen.MapzenApplication.PELIAS_LON;
import static com.mapzen.MapzenApplication.PELIAS_TEXT;

public class BaseActivity extends MapActivity
        implements SearchView.OnQueryTextListener, MenuItem.OnActionExpandListener {
    private GeoNamesAdapter geoNamesAdapter;
    private RequestQueue queue;
    private MenuItem menuItem;
    private MapzenApplication app;
    private FragmentManager fragmentManager;
    private MapFragment mapFragment;
    private SearchResultsFragment searchResultsFragment;
    private String currentSearchTerm;

    private final String[] columns = {
        _ID, PELIAS_TEXT, PELIAS_LAT, PELIAS_LON
    };

    public Map getMap() {
        return mMap;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = MapzenApplication.getApp(this);
        queue = Volley.newRequestQueue(getApplicationContext());
        fragmentManager = getSupportFragmentManager();
        BugSenseHandler.initAndStartSession(BaseActivity.this, "881794a2");
        setContentView(R.layout.base);
    }

    public MapFragment getMapFragment() {
        if (mapFragment == null) {
            mapFragment = (MapFragment) fragmentManager.findFragmentById(R.id.map_fragment);
        }
        return mapFragment;
    }

    public SearchResultsFragment getSearchResultsFragment() {
        if (searchResultsFragment == null) {
            searchResultsFragment = (SearchResultsFragment) fragmentManager.findFragmentById(R.id.search_results_fragment);
        }
        return searchResultsFragment;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        menuItem = menu.findItem(R.id.search);
        menuItem.setOnActionExpandListener(this);
        final SearchView searchView = (SearchView) menuItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        setupAdapter(searchView);
        searchView.setOnQueryTextListener(this);
        return true;
    }

    private void setupAdapter(SearchView searchView) {
        if (geoNamesAdapter == null) {
            AutoCompleteCursor cursor = new AutoCompleteCursor(columns);
            geoNamesAdapter = new GeoNamesAdapter(getActionBar().getThemedContext(), cursor);
            geoNamesAdapter.setSearchView(searchView);
        }
        searchView.setSuggestionsAdapter(geoNamesAdapter);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        JsonObjectRequest jsonObjectRequest =
                Place.search(mMap, query, getSearchSuccessResponseListener(),
                        getSearchErrorResponseListener());
        queue.add(jsonObjectRequest);
        return true;
    }

    private Response.ErrorListener getSearchErrorResponseListener() {
        return new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
            }
        };
    }

    private Response.Listener<JSONObject> getSearchSuccessResponseListener() {
        return new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                Log.v(LOG_TAG, jsonObject.toString());
                JSONArray jsonArray = new JSONArray();
                try {
                    jsonArray = jsonObject.getJSONArray("features");
                } catch (JSONException e) {
                    Log.e(LOG_TAG, e.toString());
                }
                searchResultsFragment = getSearchResultsFragment();
                searchResultsFragment.clearAll();
                searchResultsFragment.setSearchResults(jsonArray);
                final SearchView searchView = (SearchView) menuItem.getActionView();
                assert searchView != null;
                searchView.clearFocus();
            }
        };
    }

    private void clearSearchText() {
        final SearchView searchView = (SearchView) menuItem.getActionView();
        assert searchView != null;
        searchView.setQuery("", false);
        searchView.clearFocus();
    }

    private Response.Listener<JSONObject> getAutocompleteSuccessResponseListener() {
        final AutoCompleteCursor cursor = new AutoCompleteCursor(columns);
        return new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                Log.v(LOG_TAG, jsonObject.toString());
                JSONArray jsonArray = new JSONArray();
                try {
                    jsonArray = jsonObject.getJSONArray("features");
                } catch (JSONException e) {
                    Log.e(LOG_TAG, e.toString());
                }
                for (int i = 0; i < jsonArray.length(); i++) {
                    try {
                        Place place = Place.fromJson(jsonArray.getJSONObject(i));
                        cursor.getRowBuilder().setId(i).setText(place.getDisplayName()).
                                setLat(place.getLat()).
                                setLon(place.getLon()).buildRow();
                    } catch (JSONException e) {
                        Log.e(LOG_TAG, e.toString());
                    }
                }
                geoNamesAdapter.swapCursor(cursor);
            }
        };
    }

    private Response.ErrorListener getAutocompleteErrorResponseListener() {
        return new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
            }
        };
    }

    public boolean onQueryTextChange(String newText) {
        if (currentSearchTerm != null || !newText.equals(currentSearchTerm)) {
            JsonObjectRequest jsonObjectRequest = Place.suggest(newText,
                    getAutocompleteSuccessResponseListener(), getAutocompleteErrorResponseListener());
            queue.add(jsonObjectRequest);
            currentSearchTerm = newText;
        }
        return true;
    }

    private class GeoNamesAdapter extends CursorAdapter {
        private SearchView searchView;

        public GeoNamesAdapter(Context context, Cursor c) {
            super(context, c, 0);
        }

        protected void setSearchView(SearchView view) {
            searchView = view;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);
            View v = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            assert v != null;
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clearSearchText();
                    MapPosition mapPosition = (MapPosition) view.getTag();
                    mMap.setMapPosition(mapPosition);
                }
            });
            parent.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    dismissKeyboard();
                    return false;
                }
            });
            return v;
        }

        private void dismissKeyboard() {
            InputMethodManager imm = (InputMethodManager)getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView tv = (TextView) view;
            final int textIndex = cursor.getColumnIndex(PELIAS_TEXT);
            double lat =
                    Double.parseDouble(cursor.getString(cursor.getColumnIndex(PELIAS_LAT)));
            double lon =
                    Double.parseDouble(cursor.getString(cursor.getColumnIndex(PELIAS_LON)));
            MapPosition position = new MapPosition(lat, lon, Math.pow(2, app.getStoredZoomLevel()));
            tv.setTag(position);
            tv.setText(cursor.getString(textIndex));
        }
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        if (searchResultsFragment != null) {
            searchResultsFragment.hideResultsWrapper();
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == MapzenApplication.PICK_PLACE_REQUEST) {
            Bundle bundle = data.getExtras();
            Place place = bundle.getParcelable("place");
            searchResultsFragment.hideResultsWrapper();
            clearSearchText();
            PoiLayer<MarkerItem> poiLayer = (PoiLayer<MarkerItem>) mapFragment.getPoiLayer();
            poiLayer.removeAllItems();
            poiLayer.addItem(place.getMarker());
            mapFragment.centerOn(place.getGeoPoint());
        }
    }


}
