package com.example.backgroundodometer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

class TrackingBootReceiver :
    BroadcastReceiver() {

    companion object {

        private const val PREFS =
            "background_odometer"

        private const val PREF_AUTO_TRACKING =
            "auto_tracking_enabled"
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        if (
            intent.action !=
            Intent.ACTION_BOOT_COMPLETED &&
            intent.action !=
            Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action !=
            Intent.ACTION_MY_PACKAGE_REPLACED
        ) {

            return
        }

        val preferences =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val autoTracking =
            preferences.getBoolean(
                PREF_AUTO_TRACKING,
                false
            )

        if (!autoTracking) {
            return
        }

        /*
         * Foreground location requires foreground
         * location permission.
         */
        val fine =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                PackageManager.PERMISSION_GRANTED

        val coarse =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
                PackageManager.PERMISSION_GRANTED

        if (
            !fine &&
            !coarse
        ) {
            return
        }

        /*
         * For automatic background startup on
         * modern Android, background location access
         * is important.
         */
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            val background =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) ==
                    PackageManager.PERMISSION_GRANTED

            if (!background) {

                return
            }
        }

        val manager =
            context.getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        /*
         * Start the service even if GPS is currently
         * OFF.
         *
         * The service will remain in its waiting state
         * and detect GPS turning ON later.
         */
        try {

            ContextCompat.startForegroundService(
                context,
                Intent(
                    context,
                    LocationTrackingService::class.java
                )
            )

        } catch (_: Exception) {
        }
    }
    }
