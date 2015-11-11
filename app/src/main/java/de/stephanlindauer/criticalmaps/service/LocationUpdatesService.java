package de.stephanlindauer.criticalmaps.service;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.osmdroid.util.GeoPoint;

import java.util.Date;

import javax.inject.Inject;

import de.stephanlindauer.criticalmaps.events.Events;
import de.stephanlindauer.criticalmaps.model.OwnLocationModel;
import de.stephanlindauer.criticalmaps.provider.EventBusProvider;
import de.stephanlindauer.criticalmaps.utils.DateUtils;
import de.stephanlindauer.criticalmaps.utils.LocationUtils;

import static de.stephanlindauer.criticalmaps.AppConstants.LOCATION_REFRESH_DISTANCE;
import static de.stephanlindauer.criticalmaps.AppConstants.LOCATION_REFRESH_TIME;
import static de.stephanlindauer.criticalmaps.AppConstants.MAX_LOCATION_AGE;

public class LocationUpdatesService {

    //dependencies
    private final OwnLocationModel ownLocationModel;
    private final EventBusProvider eventService;

    //misc
    private LocationManager locationManager;
    private SharedPreferences sharedPreferences;
    private boolean isRegisteredForLocationUpdates;
    private Location lastPublishedLocation;

    @Inject
    public LocationUpdatesService(OwnLocationModel ownLocationModel, EventBusProvider eventService) {
        this.ownLocationModel = ownLocationModel;
        this.eventService = eventService;
    }

    public void initializeAndStartListening(@NonNull Application application) {
        locationManager = (LocationManager) application.getSystemService(Context.LOCATION_SERVICE);
        sharedPreferences = application.getSharedPreferences("Main", Context.MODE_PRIVATE);
        registerLocationListeners();
    }

    private void registerLocationListeners() {
        requestLocationUpdatesIfPossible(LocationManager.GPS_PROVIDER);
        requestLocationUpdatesIfPossible(LocationManager.NETWORK_PROVIDER);

        isRegisteredForLocationUpdates = true;
    }

    private void requestLocationUpdatesIfPossible(String gpsProvider) {
        if (locationManager.isProviderEnabled(gpsProvider)) {
            locationManager.requestLocationUpdates(gpsProvider, LOCATION_REFRESH_TIME, LOCATION_REFRESH_DISTANCE, locationListener);
        }
    }

    public void handleShutdown() {
        if (!isRegisteredForLocationUpdates) {
            return;
        }

        locationManager.removeUpdates(locationListener);
        isRegisteredForLocationUpdates = false;
    }

    @Nullable
    public GeoPoint getLastKnownLocation() {
        if (sharedPreferences.contains("latitude") && sharedPreferences.contains("longitude") && sharedPreferences.contains("timestamp")) {
            Date timestampLastCoords = new Date(sharedPreferences.getLong("timestamp", 0));
            if (!DateUtils.isLongerAgoThen5Minutes(timestampLastCoords)) {
                return new GeoPoint(
                        Double.parseDouble(sharedPreferences.getString("latitude", "")),
                        Double.parseDouble(sharedPreferences.getString("longitude", "")));
            }
        } else {
            return LocationUtils.getBestLastKnownLocation(locationManager);
        }
        return null;
    }

    private void publishNewLocation(GeoPoint newLocation, float accuracy) {
        ownLocationModel.setLocation(newLocation, accuracy);
        eventService.post(Events.NEW_LOCATION_EVENT);
        sharedPreferences.edit()
                .putString("latitude", String.valueOf(newLocation.getLatitude()))
                .putString("longitude", String.valueOf(newLocation.getLongitude()))
                .putLong("timestamp", new Date().getTime())
                .apply();
    }

    private boolean shouldPublishNewLocation(Location location) {
        // Any location is better than no location
        if (lastPublishedLocation == null) {
            return true;
        }

        // Average speed of the CM is ~4 m/s so anything over 30 seconds old, may already
        // be well over 120m off. So a newer fix is assumed to be always better.
        long timeDelta = location.getTime() - lastPublishedLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > MAX_LOCATION_AGE;
        boolean isSignificantlyOlder = timeDelta < -MAX_LOCATION_AGE;


        if (isSignificantlyNewer) {
            return true;
        } else if (isSignificantlyOlder) {
            return false;
        }

        int accuracyDelta = (int) (location.getAccuracy() - lastPublishedLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 120;

        boolean isFromSameProvider = location.getProvider().equals(lastPublishedLocation.getProvider());
        boolean isNewer = timeDelta > 0;

        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }

        return false;
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            if (shouldPublishNewLocation(location)) {
                publishNewLocation(new GeoPoint(location.getLatitude(), location.getLongitude()),
                        location.getAccuracy());
                lastPublishedLocation = location;
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
        }

        @Override
        public void onProviderEnabled(String s) {
        }

        @Override
        public void onProviderDisabled(String s) {
        }
    };
}
